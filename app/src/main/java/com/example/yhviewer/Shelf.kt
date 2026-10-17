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
 * v3 新增：permissions（每份文档自己的权限，见 DocPermissions）。
 * v4 新增：作者、封面检查标记、分组、内容分类及 shelf_groups 表。
 * 新增枚举值 CODE / TXT / EPUB 不需要迁移：type 按名字存成 TEXT。
 */
@Entity(
    tableName = "books",
    indices = [
        Index(value = ["sourceUri"], unique = true),
        Index(value = ["lastOpenedAt"]),
        Index(value = ["groupId"]),
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
    // v3：这份文档的权限（DocPermissions 位标记）。默认 1 = "标准"档，老文档升级后行为不变
    @ColumnInfo(defaultValue = "1") val permissions: Int = DocPermissions.STANDARD.toBits(),
    // v4：作者（EPUB 从 OPF 读；TXT 为 null，显示时从文件名解析）
    val author: String? = null,
    // v4：EPUB 是否已尝试提取封面。false 的书在启动时补做一次（升级前导入的书）
    @ColumnInfo(defaultValue = "0") val coverChecked: Boolean = false,
    // v4：所属分组；null = 未分组。删除分组时置回 null
    val groupId: Long? = null,
    // v4：ContentKind.id；null = 还没分类（升级前导入的），启动时补
    val kind: String? = null,
)

@Dao
interface BookDao {

    /** pattern 必须已经过 escapeLike 处理；空串 = 不过滤 */
    @Query(
        """
        SELECT * FROM books
        WHERE (:pattern = '' OR name LIKE '%' || :pattern || '%' ESCAPE '\'
               OR author LIKE '%' || :pattern || '%' ESCAPE '\')
          AND (:favoritesOnly = 0 OR favorite = 1)
        ORDER BY lastOpenedAt DESC
        """
    )
    fun observe(pattern: String, favoritesOnly: Boolean): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): Book?

    @Query("SELECT * FROM books WHERE sourceUri = :uri")
    suspend fun findBySource(uri: String): Book?

    /** 授权了"保留本地数据"的文档；启动时据此清理多余的 WebView Profile */
    @Query("SELECT id FROM books WHERE (permissions & 2) != 0")
    suspend fun idsWithPersistentStorage(): List<Long>

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

    @Query("UPDATE books SET permissions = :bits WHERE id = :id")
    suspend fun setPermissions(id: Long, bits: Int)

    @Query("UPDATE books SET localFile = :file, sizeBytes = :size, kind = :kind WHERE id = :id")
    suspend fun updateContent(id: Long, file: String, size: Long, kind: String): Int

    /** 升级前导入、还没提取过封面的 EPUB */
    @Query("SELECT * FROM books WHERE type = 'EPUB' AND coverChecked = 0")
    suspend fun epubsWithoutCoverCheck(): List<Book>

    @Query("UPDATE books SET author = :author, coverChecked = 1 WHERE id = :id")
    suspend fun setBookMeta(id: Long, author: String?)

    @Query("SELECT id FROM books")
    suspend fun allIds(): List<Long>

    // ---- v4：分组、批量操作、内容分类 ----

    @Query("UPDATE books SET groupId = :groupId WHERE id IN (:ids)")
    suspend fun moveToGroup(ids: List<Long>, groupId: Long?)

    @Query("UPDATE books SET groupId = NULL WHERE groupId = :groupId")
    suspend fun ungroup(groupId: Long)

    @Query("UPDATE books SET favorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<Long>, favorite: Boolean)

    @Query("SELECT * FROM books WHERE id IN (:ids)")
    suspend fun getAll(ids: List<Long>): List<Book>

    @Query("UPDATE books SET kind = :kind WHERE id = :id")
    suspend fun setKind(id: Long, kind: String)

    @Query("SELECT * FROM books WHERE kind IS NULL")
    suspend fun withoutKind(): List<Book>

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

/** v2 → v3：加权限列。DEFAULT 1 = 标准档，和升级前的行为完全一致 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN permissions INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v3 → v4：作者、封面标记、分组、内容分类。
 * 老数据：coverChecked = 0、kind = NULL → 启动时补封面和分类；groupId = NULL → 都在"未分组"。
 * 建表和建索引的 SQL 必须和 Room 导出的 schemas/…/4.json 完全一致，编译后对照一次。
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN author TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN coverChecked INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE books ADD COLUMN groupId INTEGER")
        db.execSQL("ALTER TABLE books ADD COLUMN kind TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_groupId` ON `books` (`groupId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `shelf_groups` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_shelf_groups_name` ON `shelf_groups` (`name`)")
    }
}

@Database(entities = [Book::class, ShelfGroup::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun groupDao(): GroupDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "shelf.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
