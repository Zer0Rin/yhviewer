package com.example.yhviewer

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/*
 * 用户自建分组（文件夹式）：一份文档最多属于一个分组（books.groupId），不在任何分组时为 null（"未分组"）。
 *
 * 刻意不用外键：删除分组时在同一个事务里先把成员的 groupId 清空，再删分组（见 ShelfRepository.deleteGroup）。
 * 这样迁移只需要 ALTER TABLE ADD COLUMN，不用重建 books 表，也不依赖 PRAGMA foreign_keys。
 */

@Entity(
    tableName = "shelf_groups",
    // 数据库层保证完全相同的名字不重复；忽略大小写的判断在 GroupNames.validate 里
    indices = [Index(value = ["name"], unique = true)],
)
data class ShelfGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Dao
interface GroupDao {
    /** 按创建顺序：新分组排在最后，标签栏位置稳定 */
    @Query("SELECT * FROM shelf_groups ORDER BY createdAt, id")
    fun observe(): Flow<List<ShelfGroup>>

    @Query("SELECT * FROM shelf_groups")
    suspend fun all(): List<ShelfGroup>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: ShelfGroup): Long

    @Query("UPDATE shelf_groups SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM shelf_groups WHERE id = :id")
    suspend fun delete(id: Long)
}

/** 分组名校验（纯函数，JVM 可测）。返回 null = 通过，否则是给用户看的错误信息 */
object GroupNames {
    const val MAX_LENGTH = 20

    /** 和固定标签页重名会让人分不清 */
    val RESERVED = setOf("全部", "收藏", "未分组")

    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun validate(raw: String, existing: Collection<ShelfGroup>, renamingId: Long? = null): String? {
        val name = normalize(raw)
        return when {
            name.isEmpty() -> "分组名不能为空"
            name.length > MAX_LENGTH -> "分组名最多 $MAX_LENGTH 个字"
            name in RESERVED -> "「$name」是固定标签，换一个名字"
            existing.any { it.id != renamingId && it.name.equals(name, ignoreCase = true) } -> "已经有叫「$name」的分组"
            else -> null
        }
    }
}
