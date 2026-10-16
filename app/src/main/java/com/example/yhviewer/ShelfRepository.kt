package com.example.yhviewer

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
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
) {
    private val dao = db.bookDao()

    fun observe(query: String, favoritesOnly: Boolean): Flow<List<Book>> =
        dao.observe(escapeLike(query.trim()), favoritesOnly)

    suspend fun import(uri: Uri): Long = storageLock.withLock {
        val source = DocumentLoader.probe(context, uri)
        if (source.type == DocType.EPUB) importEpub(uri, source) else importText(uri, source)
    }

    private suspend fun importText(uri: Uri, source: DocumentLoader.Source): Long {
        val max = if (source.type == DocType.TXT) DocumentLoader.MAX_TXT_BYTES else DocumentLoader.MAX_TEXT_BYTES
        // 编码识别只在导入时做一次，副本统一存成 UTF-8，之后打开不用再猜
        val bytes = DocumentLoader.readText(context, uri, max).toByteArray(Charsets.UTF_8)
        val fileName = UUID.randomUUID().toString()
        val target = File(dir, fileName)
        try {
            withContext(Dispatchers.IO) { writeAtomically(target, bytes) }
            return commit(uri.toString(), source.name, source.type, fileName, bytes.size.toLong())
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
            return commit(uri.toString(), book.title ?: source.name, DocType.EPUB, fileName, size)
        } catch (e: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { staging.deleteRecursively() }
            discardUnreferenced(fileName)
            throw e
        } finally {
            zip.delete()
        }
    }

    /** 新副本已经写好，这里只改数据库；成功后删除旧副本 */
    private suspend fun commit(key: String, name: String, type: DocType, fileName: String, size: Long): Long {
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

    suspend fun setPermissions(id: Long, permissions: DocPermissions) = dao.setPermissions(id, permissions.toBits())

    suspend fun idsWithPersistentStorage(): Set<Long> = dao.idsWithPersistentStorage().toSet()

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    /** 先删记录再删文件：最坏情况留下孤儿文件（启动时会被 cleanOrphans 清掉），而不是一条打不开的记录 */
    suspend fun delete(id: Long) = storageLock.withLock {
        val book = dao.get(id) ?: return@withLock
        dao.delete(id)
        withContext(Dispatchers.IO) { File(dir, book.localFile).deleteRecursively() }
    }

    /**
     * 清理没有记录指向的副本和中断导入留下的 .tmp。
     * 只在 App 启动、还没有导入任务时调用，避免误删正在写入的文件。返回删除的数量。
     */
    suspend fun cleanOrphans(): Int = storageLock.withLock {
        val referenced = dao.allLocalFiles().toSet()
        withContext(Dispatchers.IO) {
            dir.listFiles().orEmpty()
                .filter { it.name !in referenced }
                .count { it.deleteRecursively() }
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
    }
}

/**
 * 转义 LIKE 通配符：用户搜 "50%" 时应该找"50%"，而不是"50 开头的任意内容"。
 * 反斜杠必须最先处理，否则会把后面加上的转义符再转义一次。
 */
internal fun escapeLike(s: String): String =
    s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
