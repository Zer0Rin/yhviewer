package com.example.yhviewer

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * 书架的唯一入口：所有文档都先"导入"成本地副本，再从副本打开。
 *
 * 为什么要复制一份？从文件管理器"打开方式"拿到的 Uri 权限是临时的，
 * App 重启后就读不了了（大多数情况下也拿不到持久权限）。复制后：
 * 书架永远能打开、离线可用、原文件删了也还在。代价是不跟随原文件更新——
 * 重新从原处打开一次即可刷新副本。
 */
class ShelfRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val dir: File = File(context.filesDir, "shelf"), // 可注入：测试用临时目录，不污染真实书架
    private val clock: () -> Long = { System.currentTimeMillis() }, // 可注入：测试里控制时间
) {
    private val dao = db.bookDao()

    fun observe(query: String, favoritesOnly: Boolean): Flow<List<Book>> =
        dao.observe(escapeLike(query.trim()), favoritesOnly)

    /** 导入（或刷新）外部文档，返回书 id。同一 Uri 再次导入 = 更新，不会重复 */
    suspend fun import(uri: Uri): Long {
        val doc = DocumentLoader.load(context, uri)
        val bytes = doc.text.toByteArray(Charsets.UTF_8)
        // 事务：防止同一个 Uri 并发导入两次时撞上 unique 约束
        return db.withTransaction {
            val now = clock()
            val key = uri.toString()
            val existing = dao.findBySource(key)
            val fileName = existing?.localFile ?: UUID.randomUUID().toString()
            writeAtomically(File(dir, fileName), bytes)
            if (existing != null) {
                dao.update(
                    existing.copy(
                        name = doc.name, type = doc.type,
                        sizeBytes = bytes.size.toLong(), lastOpenedAt = now,
                    )
                )
                existing.id
            } else {
                dao.insert(
                    Book(
                        sourceUri = key, name = doc.name, type = doc.type,
                        localFile = fileName, sizeBytes = bytes.size.toLong(),
                        addedAt = now, lastOpenedAt = now,
                    )
                )
            }
        }
    }

    suspend fun open(id: Long): LoadedDoc {
        val book = dao.get(id) ?: throw IOException("书架里没有这个文档")
        val text = withContext(Dispatchers.IO) {
            val file = File(dir, book.localFile)
            if (!file.exists()) throw IOException("本地副本丢失，请从原文件重新打开")
            file.readText(Charsets.UTF_8)
        }
        dao.touch(id, clock())
        return LoadedDoc(book.name, book.type, text)
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    /** 先删记录再删文件：最坏情况留下一个孤儿文件，而不是一条打不开的记录 */
    suspend fun delete(id: Long) {
        val book = dao.get(id) ?: return
        dao.delete(id)
        withContext(Dispatchers.IO) { File(dir, book.localFile).delete() }
    }

    /** 先写临时文件再 rename：写到一半崩溃也不会留下半截副本 */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        dir.mkdirs()
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("保存副本失败")
        }
    }
}

/**
 * 转义 LIKE 通配符：用户搜 "50%" 时应该找"50%"，而不是"50 开头的任意内容"。
 * 反斜杠必须最先处理，否则会把后面加上的转义符再转义一次。
 */
internal fun escapeLike(s: String): String =
    s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
