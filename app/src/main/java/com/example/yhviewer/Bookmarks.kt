package com.example.yhviewer

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/*
 * v10：手动书签。自动保存的"阅读位置"只有一个，书签可以有很多个。
 * 位置用和 Locator 一样的"章节 + 章内比例"，单页文档章节恒为 0。
 * 不用外键：删除文档时在 ShelfRepository.delete 里一起删（和分组的做法一致，迁移只需建表）。
 */

@Entity(tableName = "bookmarks", indices = [Index(value = ["bookId"])])
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapter: Int,
    val ratio: Float,
    /** 列表里显示的文字：章节名（书）或文档名，后面跟百分比 */
    val label: String,
    val createdAt: Long,
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY chapter, ratio")
    fun observe(bookId: Long): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY chapter, ratio")
    suspend fun forBook(bookId: Long): List<Bookmark>

    @Insert
    suspend fun insert(b: Bookmark): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)

    @Query("SELECT bookId FROM bookmarks")
    suspend fun allBookIds(): List<Long>

    @Query("DELETE FROM bookmarks WHERE bookId NOT IN (SELECT id FROM books)")
    suspend fun deleteOrphans(): Int
}

/** v4 → v5：书签表。建表 / 建索引的 SQL 必须和导出的 schemas/…/5.json 一致，编译后对照 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `chapter` INTEGER NOT NULL, " +
                "`ratio` REAL NOT NULL, `label` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId` ON `bookmarks` (`bookId`)")
    }
}

/** 书签标签（纯函数）：同一位置附近（同章、比例差 < 1%）已有书签时不重复添加 */
object BookmarkRules {
    fun label(chapterTitle: String?, docName: String, ratio: Float): String {
        val pct = (ratio.coerceIn(0f, 1f) * 100).toInt()
        val base = chapterTitle?.takeIf { it.isNotBlank() } ?: docName
        return "${base.take(40)} · $pct%"
    }

    fun isDuplicate(existing: List<Bookmark>, chapter: Int, ratio: Float): Boolean =
        existing.any { it.chapter == chapter && kotlin.math.abs(it.ratio - ratio) < 0.01f }
}

/** Shared by both bookmark entry points; feedback follows the committed transaction result. */
object BookmarkFeedback {
    fun message(result: Result<Boolean>, added: String = "已添加书签"): String = result.fold(
        onSuccess = { if (it) added else "这里已有书签" },
        onFailure = { "添加书签失败：${it.message ?: "请重试"}" },
    )
}
