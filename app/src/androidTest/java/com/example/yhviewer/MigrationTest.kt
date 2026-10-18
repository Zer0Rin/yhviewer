package com.example.yhviewer

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val insertV1 =
        "INSERT INTO books (sourceUri, name, type, localFile, sizeBytes, addedAt, lastOpenedAt, favorite) " +
            "VALUES ('content://a', 'a.html', 'HTML', 'f1', 10, 1, 2, 1)"

    @Test
    fun migrate1To2KeepsDataAndAddsDefaults() {
        helper.createDatabase(dbName, 1).apply { execSQL(insertV1); close() }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        db.query("SELECT name, favorite, locator, progress FROM books").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("a.html", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertTrue(c.isNull(2))
            assertEquals(0f, c.getFloat(3), 0f)
        }
    }

    @Test
    fun migrate2To3DefaultsToStandardPermissions() {
        helper.createDatabase(dbName, 2).apply {
            execSQL(insertV1)
            execSQL("UPDATE books SET locator = '0:0.5000', progress = 0.5")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

        db.query("SELECT permissions, locator, progress FROM books").use { c ->
            assertTrue(c.moveToFirst())
            // 老文档升级后是"标准"档：和升级前的行为完全一致
            assertEquals(DocPermissions.STANDARD.toBits(), c.getInt(0))
            assertEquals("0:0.5000", c.getString(1))   // v2 的数据保留
            assertEquals(0.5f, c.getFloat(2), 0f)
        }
    }

    @Test
    fun migrateAllTheWayFrom1() {
        helper.createDatabase(dbName, 1).apply { execSQL(insertV1); close() }
        helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).close()
    }
    @Test
    fun migrate3To4PreservesMetadataAndAddsUnclassifiedUngroupedDefaults() {
        helper.createDatabase(dbName, 3).apply {
            execSQL(insertV1)
            execSQL("UPDATE books SET locator = '2:0.5000', progress = 0.5, permissions = 3")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)
        db.query("SELECT name, favorite, locator, progress, permissions, author, coverChecked, groupId, kind FROM books").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("a.html", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertEquals("2:0.5000", c.getString(2))
            assertEquals(0.5f, c.getFloat(3), 0f)
            assertEquals(3, c.getInt(4))
            assertTrue(c.isNull(5))
            assertEquals(0, c.getInt(6))
            assertTrue(c.isNull(7))
            assertTrue(c.isNull(8))
        }
        db.execSQL("INSERT INTO shelf_groups(name, createdAt) VALUES ('工作', 1)")
        db.query("SELECT name FROM shelf_groups").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("工作", c.getString(0))
        }
        db.close()
    }

    @Test
    fun migrate4To5AddsBookmarksAndKeepsBooks() {
        helper.createDatabase(dbName, 4).apply {
            execSQL(insertV1)
            execSQL("UPDATE books SET locator = '1:0.2500', groupId = NULL, kind = 'novel'")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5)
        db.query("SELECT locator, kind FROM books").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("1:0.2500", c.getString(0))
            assertEquals("novel", c.getString(1))
        }
        db.execSQL("INSERT INTO bookmarks(bookId, chapter, ratio, label, createdAt) VALUES (1, 1, 0.25, '第二章 · 25%', 1)")
        db.query("SELECT label FROM bookmarks WHERE bookId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("第二章 · 25%", c.getString(0))
        }
        db.close()
    }
}
