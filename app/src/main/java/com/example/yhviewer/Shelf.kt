package com.example.yhviewer

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 书架里的一条记录。文件内容不进数据库，存成 filesDir/shelf/<localFile> 的副本；
 * 数据库只存元数据（和后端"大字段放对象存储，表里只放 key"一个思路）。
 */
@Entity(
    tableName = "books",
    indices = [
        Index(value = ["sourceUri"], unique = true), // 同一个来源只保留一条
        Index(value = ["lastOpenedAt"]),             // 书架按最近打开排序
    ],
)
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUri: String,
    val name: String,
    val type: DocType,        // Room 内置枚举支持，按名字存成 TEXT
    val localFile: String,
    val sizeBytes: Long,
    val addedAt: Long,
    val lastOpenedAt: Long,
    val favorite: Boolean = false,
)

@Dao
interface BookDao {

    /** pattern 必须已经过 escapeLike 处理；空串 = 不过滤 */
    @Query(
        """
        SELECT * FROM books
        WHERE (:pattern = '' OR name LIKE '%' || :pattern || '%' ESCAPE '\')
          AND (:favoritesOnly = 0 OR favorite = 1)
        ORDER BY lastOpenedAt DESC
        """
    )
    fun observe(pattern: String, favoritesOnly: Boolean): Flow<List<Book>> // 返回 Flow：表一变，列表自动刷新

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): Book?

    @Query("SELECT * FROM books WHERE sourceUri = :uri")
    suspend fun findBySource(uri: String): Book?

    @Insert
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Query("UPDATE books SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE books SET lastOpenedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [Book::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // 双重检查锁单例：整个 App 只开一个数据库连接池
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "shelf.db"
                ).build().also { instance = it }
            }
    }
}
