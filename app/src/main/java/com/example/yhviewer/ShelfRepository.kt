package com.example.yhviewer

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** 打开后交给阅读页的内容：单页文档，或分章节的 TXT / EPUB */
sealed interface OpenedDoc {
    val bookId: Long
    val name: String
    val locator: Locator
    val chapterCount: Int

    data class Single(
        override val bookId: Long,
        override val name: String,
        override val locator: Locator,
        val doc: LoadedDoc,
        val permissions: DocPermissions = DocPermissions.STANDARD,
        /** v10：文件夹导入的 HTML / Markdown，和同文件夹资源放在一起；单文件导入时为 null */
        val bundle: BundleRef? = null,
    ) : OpenedDoc {
        override val chapterCount get() = 1
    }

    data class Txt(
        override val bookId: Long,
        override val name: String,
        override val locator: Locator,
        val chapters: List<TxtChapter>,
    ) : OpenedDoc {
        override val chapterCount get() = chapters.size
    }

    data class Epub(
        override val bookId: Long,
        override val name: String,
        override val locator: Locator,
        val rootDir: File,
        val chapters: List<EpubChapter>,
        /** v10：原书竖排，阅读时已转为横排 */
        val vertical: Boolean = false,
    ) : OpenedDoc {
        override val chapterCount get() = chapters.size
    }
}

/** 文件夹副本里的一份文档：rootDir 是副本根目录，relPath 是文档在其中的相对路径 */
data class BundleRef(val rootDir: File, val relPath: String)

/**
 * 书架的唯一入口：所有文档先"导入"成本地副本，再从副本打开。
 *
 * 导入流程（所有类型一致）：
 *   1. 先把新内容写到一个全新的文件名下（失败就删掉，数据库不受影响）
 *   2. 在事务里插入或更新记录，指向新文件
 *   3. 事务成功后再删掉旧副本
 * 这样重复导入同一个文件是"原子替换"：任何一步崩溃，都不会出现记录指向半截文件的情况。
 */
class ShelfRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val dir: File = File(context.filesDir, "shelf"),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val tmpDir: File = context.cacheDir,
    val covers: CoverStore = CoverStore(File(context.filesDir, "covers")),
) {
    private val dao = db.bookDao()
    private val groupDao = db.groupDao()
    private val bookmarkDao = db.bookmarkDao()

    fun observe(query: String, favoritesOnly: Boolean): Flow<List<Book>> =
        dao.observe(escapeLike(query.trim()), favoritesOnly)

    suspend fun import(uri: Uri): Long = storageLock.withLock {
        val source = DocumentLoader.probe(context, uri)
        if (source.type == DocType.EPUB) importEpub(uri, source) else importText(uri, source)
    }

    // ---------------- v10：多选导入、文件夹导入 ----------------

    /** 逐个导入，单个失败不影响其他文件；返回报告。importedIds 按成功顺序 */
    suspend fun importMany(uris: List<Uri>, onProgress: (ImportProgress) -> Unit = {}): Pair<ImportReport, List<Long>> {
        val ids = ArrayList<Long>()
        val failed = ArrayList<Pair<String, String>>()
        uris.forEachIndexed { i, uri ->
            val name = runCatching { DocumentLoader.probe(context, uri).name }.getOrDefault(uri.lastPathSegment ?: "文件")
            onProgress(ImportProgress(i, uris.size, name))
            try {
                ids += import(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += name to (e.message ?: e.javaClass.simpleName)
            }
        }
        onProgress(ImportProgress(uris.size, uris.size, ""))
        return ImportReport(imported = ids.size, failed = failed, skipped = emptyList()) to ids
    }

    /**
     * 导入整个文件夹（见 FolderImport.kt 的分类规则）：
     *  1. HTML / Markdown 和资源复制进一个新的文件夹副本 shelf/<uuid>/，保持相对路径
     *  2. 每份 HTML / Markdown 提交成一条记录，localFile = "<uuid>/<相对路径>"，来源键 = 文件夹 Uri#相对路径
     *     （再次导入同一个文件夹时更新原记录，阅读位置、分组、收藏都保留）
     *  3. 其他文档（TXT、EPUB、代码…）逐个按单文件导入
     *  4. 旧的文件夹副本不再被任何记录引用时删除
     */
    suspend fun importFolder(treeUri: Uri, onProgress: (ImportProgress) -> Unit = {}): ImportReport {
        val found = FolderWalker.walk(context, treeUri)
        val plan = FolderImportPlanner.plan(found.map { it.entry })
        val uriOf = found.filter { it.entry.skipReason == null }.associate { it.entry.relPath to it.uri }
        val failed = ArrayList<Pair<String, String>>()
        val skipped = plan.skipped.map { it.entry.relPath to (it.reason ?: "跳过") }
        val total = plan.bundleDocs.size + plan.standalone.size
        var done = 0
        var imported = 0
        var copiedResources = 0

        if (plan.bundleDocs.isNotEmpty()) {
            storageLock.withLock {
                val bundle = UUID.randomUUID().toString()
                val staging = File(dir, "$bundle.tmp")
                val root = File(dir, bundle)
                val budget = BundleCopyBudget(FolderImportPlanner.Limits().maxBundleBytes)
                val docs = ArrayList<StagedBundleDocument>()
                try {
                    withContext(Dispatchers.IO) {
                        for (p in plan.bundleFiles) {
                            currentCoroutineContext().ensureActive()
                            val rel = p.entry.relPath
                            val target = File(staging, rel)
                            if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                                throw IOException("非法路径：$rel")
                            }
                            val uri = uriOf.getValue(rel)
                            try {
                                if (p.role == EntryRole.BUNDLE_DOC) {
                                    val name = rel.substringAfterLast('/')
                                    // 文档统一转成 UTF-8 存储，提供给 WebView 时声明 utf-8（HTTP 头优先于页面里的 meta charset）
                                    val text = DocumentLoader.readText(context, uri, DocumentLoader.MAX_TEXT_BYTES)
                                    val bytes = text.toByteArray(Charsets.UTF_8)
                                    target.parentFile?.mkdirs()
                                    budget.add(bytes.size.toLong())
                                    target.writeBytes(bytes)
                                    docs += StagedBundleDocument.from(rel, bytes.size.toLong(), text)
                                } else {
                                    val size = DocumentLoader.copyTo(context, uri, target, minOf(FolderImportPlanner.Limits().maxFileBytes, budget.remaining))
                                    budget.add(size)
                                    copiedResources++
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                target.delete()
                                failed += rel to (e.message ?: e.javaClass.simpleName)
                            }
                        }
                        if (docs.isNotEmpty() && !staging.renameTo(root)) throw IOException("保存文件夹副本失败")
                    }
                } catch (e: Exception) {
                    withContext(NonCancellable + Dispatchers.IO) { staging.deleteRecursively() }
                    throw e
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) { staging.deleteRecursively() }
                }

                val previousTops = HashSet<String>()
                for (doc in docs) {
                    val rel = doc.relPath
                    val size = doc.size
                    onProgress(ImportProgress(done, total, rel))
                    val key = BundlePaths.sourceKey(treeUri.toString(), rel)
                    dao.findBySource(key)?.let { previousTops += BundlePaths.topLevel(it.localFile) }
                    try {
                        commit(key, doc.name, doc.type, BundlePaths.localFile(bundle, rel), size, doc.kind, null)
                        imported++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed += rel to (e.message ?: e.javaClass.simpleName)
                    }
                    done++
                }
                if (imported == 0) copiedResources = 0
                // 新副本一个记录都没提交成功、或旧副本已经没有记录引用：删掉
                withContext(NonCancellable + Dispatchers.IO) {
                    val referenced = dao.allLocalFiles().mapTo(HashSet()) { BundlePaths.topLevel(it) }
                    (previousTops + bundle).filter { it !in referenced }.forEach { File(dir, it).deleteRecursively() }
                }
            }
        }

        for (p in plan.standalone) {
            onProgress(ImportProgress(done, total, p.entry.relPath))
            try {
                import(uriOf.getValue(p.entry.relPath))
                imported++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += p.entry.relPath to (e.message ?: e.javaClass.simpleName)
            }
            done++
        }
        onProgress(ImportProgress(total, total, ""))
        return ImportReport(imported, failed, skipped, resources = copiedResources.takeIf { imported > 0 } ?: 0)
    }

    private suspend fun importText(uri: Uri, source: DocumentLoader.Source): Long {
        val max = if (source.type == DocType.TXT) DocumentLoader.MAX_TXT_BYTES else DocumentLoader.MAX_TEXT_BYTES
        // 编码识别只在导入时做一次，副本统一存成 UTF-8，之后打开不用再猜
        val text = DocumentLoader.readText(context, uri, max)
        val bytes = text.toByteArray(Charsets.UTF_8)
        val kind = ContentClassifier.classify(source.name, source.type, bytes.size.toLong(), text)
        val fileName = UUID.randomUUID().toString()
        val target = File(dir, fileName)
        try {
            withContext(Dispatchers.IO) { writeAtomically(target, bytes) }
            val id = commit(uri.toString(), source.name, source.type, fileName, bytes.size.toLong(), kind, BookTitles.parse(source.name).author)
            withContext(NonCancellable + Dispatchers.IO) { runCatching { covers.delete(id) } }
            return id
        } catch (e: Exception) {
            discardUnreferenced(fileName)
            throw e
        }
    }

    private suspend fun importEpub(uri: Uri, source: DocumentLoader.Source): Long {
        val zip = File(tmpDir, "import-${UUID.randomUUID()}.epub")
        val fileName = UUID.randomUUID().toString()
        val staging = File(dir, "$fileName.tmp")
        try {
            val size = DocumentLoader.copyTo(context, uri, zip, DocumentLoader.MAX_EPUB_BYTES)
            val book = withContext(Dispatchers.IO) {
                zip.inputStream().use { SafeUnzip.extract(it, staging) }
                EpubParser.parse(staging) // 导入时就完整校验一遍，坏书不进书架
            }
            val target = File(dir, fileName)
            if (!staging.renameTo(target)) throw IOException("保存 EPUB 失败")
            val id = commit(uri.toString(), book.title ?: source.name, DocType.EPUB, fileName, size, ContentKind.EBOOK, book.author)
            saveBookMeta(id, target, book)
            return id
        } catch (e: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { staging.deleteRecursively() }
            discardUnreferenced(fileName)
            throw e
        } finally {
            zip.delete()
        }
    }

    /** 新副本已经写好，这里只改数据库；成功后删除旧副本 */
    private suspend fun commit(key: String, name: String, type: DocType, fileName: String, size: Long, kind: ContentKind, author: String?): Long {
        var oldFile: String? = null
        val id = try {
            db.withTransaction {
                val now = clock()
                val existing = dao.findBySource(key)
                if (existing != null) {
                    oldFile = existing.localFile
                    dao.update(
                        existing.copy(
                            name = name, type = type, localFile = fileName, sizeBytes = size, lastOpenedAt = now,
                            kind = kind.id, author = author, coverChecked = type != DocType.EPUB,
                            // 类型变了（极少见）就从头读；否则保留阅读位置
                            locator = if (existing.type == type) existing.locator else null,
                            progress = if (existing.type == type) existing.progress else 0f,
                        )
                    )
                    existing.id
                } else {
                    dao.insert(
                        Book(
                            sourceUri = key, name = name, type = type, localFile = fileName,
                            sizeBytes = size, addedAt = now, lastOpenedAt = now,
                            kind = kind.id, author = author, coverChecked = type != DocType.EPUB,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            discardUnreferenced(fileName)
            throw e
        }
        oldFile?.takeIf { it != fileName }?.let { old ->
            withContext(NonCancellable + Dispatchers.IO) { BundleStorage.removeUnreferenced(dir, old, dao.allLocalFiles()) }
        }
        return id
    }

    /** 生成封面（没有就删掉旧的）并记下作者；重新导入同一本书时也走这里，旧封面会被替换 */
    private suspend fun saveBookMeta(id: Long, root: File, book: EpubBook) {
        try {
            withContext(Dispatchers.IO) {
                val ok = book.coverPath?.let { covers.generate(id, File(root, it)) } ?: false
                if (!ok) covers.delete(id)
            }
            dao.setBookMeta(id, book.author)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The row and original content are already committed. Retry metadata at startup.
        }
    }

    /**
     * 给升级前导入的 EPUB 补封面和作者。每本只做一次（coverChecked），
     * 解析失败的书也标记掉，不会每次启动都重试。启动清理之后在后台调用。
     */
    suspend fun backfillCovers(): Int = storageLock.withLock {
        var done = 0
        for (b in dao.epubsWithoutCoverCheck()) {
            val root = File(dir, b.localFile)
            val parsed = withContext(Dispatchers.IO) { runCatching { EpubParser.parse(root) }.getOrNull() }
            if (parsed != null) {
                saveBookMeta(b.id, root, parsed)
                if (covers.exists(b.id)) done++
            } else {
                withContext(Dispatchers.IO) { covers.delete(b.id) }
                dao.setBookMeta(b.id, null)
            }
        }
        done
    }

    /** 给升级前导入的文档补内容分类；读不到文件的标成纯文本，不会每次启动都重试 */
    suspend fun backfillKinds(): Int = storageLock.withLock {
        val todo = dao.withoutKind()
        for (b in todo) {
            val kind = withContext(Dispatchers.IO) {
                if (b.type == DocType.EPUB) return@withContext ContentKind.EBOOK
                val sample = runCatching {
                    File(dir, b.localFile).bufferedReader(Charsets.UTF_8).use { r ->
                        val buf = CharArray(ContentClassifier.SAMPLE_CHARS)
                        val n = r.read(buf)
                        if (n <= 0) "" else String(buf, 0, n)
                    }
                }.getOrDefault("")
                ContentClassifier.classify(b.name, b.type, b.sizeBytes, sample)
            }
            dao.setKind(b.id, kind.id)
        }
        todo.size
    }

    // ---------------- 分组（v7） ----------------

    fun observeGroups(): Flow<List<ShelfGroup>> = groupDao.observe()

    /** 校验失败抛 IllegalArgumentException，message 直接给用户看 */
    suspend fun createGroup(rawName: String): Long = db.withTransaction {
        createGroupInTransaction(rawName)
    }

    private suspend fun createGroupInTransaction(rawName: String): Long {
        GroupNames.validate(rawName, groupDao.all())?.let { throw IllegalArgumentException(it) }
        return groupDao.insert(ShelfGroup(name = GroupNames.normalize(rawName), createdAt = clock()))
    }

    suspend fun createGroupAndMove(rawName: String, ids: Collection<Long>): Long = db.withTransaction {
        val id = createGroupInTransaction(rawName)
        ids.distinct().chunked(SQL_BATCH_SIZE).forEach { dao.moveToGroup(it, id) }
        id
    }

    suspend fun renameGroup(id: Long, rawName: String) = db.withTransaction {
        val groups = groupDao.all()
        require(groups.any { it.id == id }) { "分组已不存在" }
        GroupNames.validate(rawName, groups, renamingId = id)?.let { throw IllegalArgumentException(it) }
        groupDao.rename(id, GroupNames.normalize(rawName))
    }

    /** 删除分组：成员回到"未分组"，文件和阅读记录都不动。同一个事务，不会出现指向已删分组的记录 */
    suspend fun deleteGroup(id: Long) {
        db.withTransaction {
            dao.ungroup(id)
            groupDao.delete(id)
        }
    }

    /** groupId = null：移出分组 */
    suspend fun moveToGroup(ids: Collection<Long>, groupId: Long?) = db.withTransaction {
        require(groupId == null || groupDao.all().any { it.id == groupId }) { "分组已不存在" }
        ids.distinct().chunked(SQL_BATCH_SIZE).forEach { dao.moveToGroup(it, groupId) }
    }

    suspend fun setFavorite(ids: Collection<Long>, favorite: Boolean) = db.withTransaction {
        ids.distinct().chunked(SQL_BATCH_SIZE).forEach { dao.setFavorite(it, favorite) }
    }

    /** 批量删除：逐个走 delete()，保证每本书的副本和封面都被清理 */
    suspend fun deleteMany(ids: Collection<Long>) {
        ids.forEach { delete(it) }
    }

    suspend fun open(id: Long): OpenedDoc {
        val book = dao.get(id) ?: throw IOException("书架里没有这个文档")
        val locator = Locator.decode(book.locator)
        val opened = withContext(Dispatchers.IO) {
            val local = File(dir, book.localFile)
            if (!local.exists()) throw IOException("本地副本丢失，请从原文件重新打开")
            when (book.type) {
                DocType.EPUB -> EpubParser.parse(local).let { parsed ->
                    OpenedDoc.Epub(book.id, book.name, locator, local, parsed.chapters, parsed.vertical)
                }
                DocType.TXT -> OpenedDoc.Txt(book.id, book.name, locator, TxtChapters.split(local.readText(Charsets.UTF_8)))
                else -> OpenedDoc.Single(
                    book.id, book.name, locator, LoadedDoc(book.name, book.type, local.readText(Charsets.UTF_8)), DocPermissions.fromBits(book.permissions),
                    bundle = book.localFile.takeIf(BundlePaths::isBundle)?.let {
                        BundleRef(File(dir, BundlePaths.topLevel(it)), BundlePaths.relPath(it))
                    },
                )
            }
        }
        dao.touch(id, clock())
        return opened
    }

    suspend fun savePosition(id: Long, locator: Locator, chapterCount: Int) {
        dao.updatePosition(id, locator.encode(), locator.overallProgress(chapterCount))
    }

    /** 编辑只替换书架副本，原文件和所有阅读元数据保持原样。 */
    suspend fun updateContent(id: Long, text: String): Unit = storageLock.withLock {
        val book = dao.get(id) ?: throw IOException("书架里没有这个文档")
        when (book.type) {
            DocType.HTML, DocType.MARKDOWN, DocType.CODE, DocType.UNKNOWN -> Unit
            DocType.TXT, DocType.EPUB -> throw IOException("分章节文档不支持编辑")
        }
        val bytes = editorTextBytes(text)
        withContext(Dispatchers.IO) {
            val existing = File(dir, book.localFile)
            if (!existing.isFile || !existing.canRead()) {
                throw IOException("本地副本丢失或无法读取，请从原文件重新打开")
            }
            // Verify that opening succeeds too; a provider/filesystem can deny access after canRead.
            existing.inputStream().use { it.read() }
        }
        val kind = ContentClassifier.classify(book.name, book.type, bytes.size.toLong(), text).id
        if (BundlePaths.isBundle(book.localFile)) {
            // 文件夹副本里的文档原地替换：换成新文件名会离开副本目录，相对路径的资源就找不到了
            withContext(Dispatchers.IO) {
                val target = File(dir, book.localFile)
                BundleStorage.replace(target, bytes)
            }
            if (dao.updateContent(id, book.localFile, bytes.size.toLong(), kind) != 1) throw IOException("书架里没有这个文档")
            return@withLock
        }
        val fileName = UUID.randomUUID().toString()
        try {
            withContext(Dispatchers.IO) { writeAtomically(File(dir, fileName), bytes) }
            db.withTransaction {
                if (dao.updateContent(id, fileName, bytes.size.toLong(), kind) != 1) {
                    throw IOException("书架里没有这个文档")
                }
            }
        } catch (e: Exception) {
            // Room may have committed just before cancellation: never delete a referenced copy.
            discardUnreferenced(fileName)
            throw e
        }
        withContext(NonCancellable + Dispatchers.IO) {
            File(dir, book.localFile).deleteRecursively()
        }
    }

    suspend fun setPermissions(id: Long, permissions: DocPermissions) = dao.setPermissions(id, permissions.toBits())

    suspend fun idsWithPersistentStorage(): Set<Long> = dao.idsWithPersistentStorage().toSet()

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    /** 先删记录再删文件：最坏情况留下孤儿文件（启动时会被 cleanOrphans 清掉），而不是一条打不开的记录 */
    suspend fun delete(id: Long) = storageLock.withLock {
        val book = dao.get(id) ?: return@withLock
        db.withTransaction {
            dao.delete(id)
            bookmarkDao.deleteForBook(id)
        }
        withContext(NonCancellable + Dispatchers.IO) {
            BundleStorage.removeUnreferenced(dir, book.localFile, dao.allLocalFiles())
            covers.delete(id)
        }
    }

    // ---------------- v10：书签 ----------------

    fun observeBookmarks(bookId: Long): Flow<List<Bookmark>> = bookmarkDao.observe(bookId)

    /** Serialize against current persisted rows, including rapid adds before Flow delivery. */
    suspend fun addBookmark(b: Bookmark): Boolean = storageLock.withLock {
        db.withTransaction {
            if (dao.get(b.bookId) == null) throw IOException("书架里没有这个文档")
            val current = bookmarkDao.forBook(b.bookId)
            if (BookmarkRules.isDuplicate(current, b.chapter, b.ratio)) false
            else {
                bookmarkDao.insert(b.copy(createdAt = clock()))
                true
            }
        }
    }

    suspend fun deleteBookmark(id: Long) = bookmarkDao.delete(id)

    // ---------------- v10：全书搜索用的纯文本 ----------------

    /** 每章纯文本：TXT 直接取章节文本；EPUB 读章节 XHTML 去标签。单章读失败当作空章 */
    suspend fun chapterTexts(doc: OpenedDoc): List<String> = when (doc) {
        is OpenedDoc.Txt -> doc.chapters.map { TxtSearchText.rendered(it.title, it.text) }
        is OpenedDoc.Epub -> withContext(Dispatchers.IO) {
            doc.chapters.map { ch ->
                currentCoroutineContext().ensureActive()
                runCatching {
                    val f = File(doc.rootDir, ch.path)
                    if (f.length() > 10L * 1024 * 1024) "" else XhtmlText.extract(CharsetDetector.decode(f.readBytes()).text)
                }.getOrDefault("")
            }
        }
        is OpenedDoc.Single -> listOf(doc.doc.text)
    }

    /**
     * 清理没有记录指向的副本和中断导入留下的 .tmp。
     * 只在 App 启动、还没有导入任务时调用，避免误删正在写入的文件。返回删除的数量。
     */
    suspend fun cleanOrphans(): Int = storageLock.withLock {
        // 文件夹副本按顶层目录名比较："<uuid>/a/b.html" 引用的是目录 <uuid>
        val referenced = dao.allLocalFiles().mapTo(HashSet()) { BundlePaths.topLevel(it) }
        val ids = dao.allIds().toSet()
        runCatching { bookmarkDao.deleteOrphans() }
        withContext(Dispatchers.IO) {
            dir.listFiles().orEmpty()
                .filter { it.name !in referenced }
                .count { it.deleteRecursively() } + covers.cleanOrphans(ids)
        }
    }

    /** 先写临时文件再 rename：写到一半崩溃也不会留下半截副本 */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        dir.mkdirs()
        val tmp = File(dir, target.name + ".tmp")
        try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) throw IOException("保存副本失败")
        } finally { tmp.delete() }
    }

    // A cancellation can arrive after Room commits. Check references before removing anything:
    // an orphan is recoverable at startup, a committed row pointing to a missing file is not.
    private suspend fun discardUnreferenced(fileName: String) = withContext(NonCancellable + Dispatchers.IO) {
        // 文件夹副本里的文件由 importFolder 统一清理，这里只处理普通副本
        if (BundlePaths.isBundle(fileName)) return@withContext
        if (fileName !in dao.allLocalFiles()) File(dir, fileName).deleteRecursively()
    }

    private companion object {
        // Shared across repository instances so recreation cannot clean another import's staging files.
        val storageLock = Mutex()
        const val SQL_BATCH_SIZE = 900
    }
}

/**
 * 转义 LIKE 通配符：用户搜 "50%" 时应该找"50%"，而不是"50 开头的任意内容"。
 * 反斜杠必须最先处理，否则会把后面加上的转义符再转义一次。
 */
internal fun escapeLike(s: String): String =
    s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/** The editor limit also applies to Save As, before touching the destination. */
internal fun editorTextBytes(text: String): ByteArray {
    if (text.length > 1_000_000) throw IOException("文档超过编辑器的 100 万字符限制")
    return text.toByteArray(Charsets.UTF_8).also {
        if (it.size.toLong() > DocumentLoader.MAX_TEXT_BYTES) throw IOException("文档超过文本大小限制")
    }
}
