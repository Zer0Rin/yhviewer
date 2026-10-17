package com.example.yhviewer

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
    ) : OpenedDoc {
        override val chapterCount get() = chapters.size
    }
}

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

    fun observe(query: String, favoritesOnly: Boolean): Flow<List<Book>> =
        dao.observe(escapeLike(query.trim()), favoritesOnly)

    suspend fun import(uri: Uri): Long = storageLock.withLock {
        val source = DocumentLoader.probe(context, uri)
        if (source.type == DocType.EPUB) importEpub(uri, source) else importText(uri, source)
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
            withContext(NonCancellable + Dispatchers.IO) { File(dir, old).deleteRecursively() }
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
                DocType.EPUB -> OpenedDoc.Epub(book.id, book.name, locator, local, EpubParser.parse(local).chapters)
                DocType.TXT -> OpenedDoc.Txt(book.id, book.name, locator, TxtChapters.split(local.readText(Charsets.UTF_8)))
                else -> OpenedDoc.Single(
                    book.id, book.name, locator, LoadedDoc(book.name, book.type, local.readText(Charsets.UTF_8)), DocPermissions.fromBits(book.permissions)
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
        val fileName = UUID.randomUUID().toString()
        try {
            withContext(Dispatchers.IO) { writeAtomically(File(dir, fileName), bytes) }
            db.withTransaction {
                if (dao.updateContent(id, fileName, bytes.size.toLong(), ContentClassifier.classify(book.name, book.type, bytes.size.toLong(), text).id) != 1) {
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
        dao.delete(id)
        withContext(NonCancellable + Dispatchers.IO) {
            File(dir, book.localFile).deleteRecursively()
            covers.delete(id)
        }
    }

    /**
     * 清理没有记录指向的副本和中断导入留下的 .tmp。
     * 只在 App 启动、还没有导入任务时调用，避免误删正在写入的文件。返回删除的数量。
     */
    suspend fun cleanOrphans(): Int = storageLock.withLock {
        val referenced = dao.allLocalFiles().toSet()
        val ids = dao.allIds().toSet()
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
