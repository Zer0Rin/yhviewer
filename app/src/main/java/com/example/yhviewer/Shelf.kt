package com.example.yhviewer

import android.content.Context
import androidx.room.ColumnInfo
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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 书架记录。文件内容不进数据库：文本类存成 filesDir/shelf/<localFile>（统一 UTF-8），
 * EPUB 解压成 filesDir/shelf/<localFile>/ 目录。
 *
 * v2 新增：locator（阅读位置，见 Locator）、progress（全书进度 0..1，书架显示用）。
 * 新增枚举值 CODE / TXT / EPUB 不需要迁移：type 按名字存成 TEXT。
 */
@Entity(
    tableName = "books",
    indices = [
        Index(value = ["sourceUri"], unique = true),
        Index(value = ["lastOpenedAt"]),
    ],
)
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUri: String,
    val name: String,
    val type: DocType,
    val localFile: String,
    val sizeBytes: Long,
    val addedAt: Long,
    val lastOpenedAt: Long,
    val favorite: Boolean = false,
    val locator: String? = null,
    // defaultValue 必须和迁移里的 DEFAULT 0 一致，否则 Room 校验表结构时会报"迁移不正确"
    @ColumnInfo(defaultValue = "0") val progress: Float = 0f,
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
    fun observe(pattern: String, favoritesOnly: Boolean): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): Book?

    @Query("SELECT * FROM books WHERE sourceUri = :uri")
    suspend fun findBySource(uri: String): Book?

    @Query("SELECT localFile FROM books")
    suspend fun allLocalFiles(): List<String>

    @Insert
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Query("UPDATE books SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE books SET lastOpenedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long)

    @Query("UPDATE books SET locator = :locator, progress = :progress WHERE id = :id")
    suspend fun updatePosition(id: Long, locator: String, progress: Float)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)
}

/** v1 → v2：加两列。老数据保留，locator 为空（从头读），progress 为 0 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN locator TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN progress REAL NOT NULL DEFAULT 0")
    }
}

@Database(entities = [Book::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "shelf.db")
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
