package com.example.yhviewer

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 用导出的 schemas/1.json 建一个"老版本"数据库，写入数据，再跑迁移到 v2，
 * 由 Room 校验迁移后的表结构和 Entity 完全一致（第三个参数 validateDroppedTables = true）。
 * 需要 androidTestImplementation("androidx.room:room-testing:<room版本>")。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2KeepsDataAndAddsDefaults() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO books (sourceUri, name, type, localFile, sizeBytes, addedAt, lastOpenedAt, favorite) " +
                    "VALUES ('content://a', 'a.md', 'MARKDOWN', 'f1', 10, 1, 2, 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        db.query("SELECT name, favorite, locator, progress FROM books").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("a.md", c.getString(0))
            assertEquals(1, c.getInt(1))       // 老数据（收藏）保留
            assertTrue(c.isNull(2))            // 没有阅读位置 → 从头读
            assertEquals(0f, c.getFloat(3), 0f)
        }
    }
}
