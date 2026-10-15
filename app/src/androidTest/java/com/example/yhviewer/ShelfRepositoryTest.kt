package com.example.yhviewer

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ShelfRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: ShelfRepository
    private lateinit var shelfDir: File
    private lateinit var srcDir: File
    private var now = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val root = File(context.cacheDir, "shelf-test-${UUID.randomUUID()}")
        shelfDir = File(root, "shelf")
        srcDir = File(root, "src").apply { mkdirs() }
        repo = ShelfRepository(context, db, dir = shelfDir, clock = { now })
    }

    @After
    fun tearDown() {
        db.close()
        shelfDir.parentFile?.deleteRecursively()
    }

    /** 用 file:// Uri 模拟外部文件；ContentResolver 也支持读 file 协议 */
    private fun source(name: String, text: String): Pair<File, Uri> {
        val f = File(srcDir, name).apply { writeText(text) }
        return f to Uri.fromFile(f)
    }

    @Test
    fun importThenOpenRoundTrip() = runBlocking<Unit> {
        val (_, uri) = source("a.md", "# hi")
        val doc = repo.open(repo.import(uri))
        assertEquals("a.md", doc.name)
        assertEquals(DocType.MARKDOWN, doc.type)
        assertEquals("# hi", doc.text)
    }

    @Test
    fun reimportSameUriUpdatesInsteadOfDuplicating() = runBlocking<Unit> {
        val (file, uri) = source("b.html", "v1")
        val id1 = repo.import(uri)
        file.writeText("v2")
        now = 2_000L
        val id2 = repo.import(uri)

        assertEquals(id1, id2)
        assertEquals(1, repo.observe("", false).first().size)
        assertEquals("v2", repo.open(id1).text)
    }

    @Test
    fun copySurvivesSourceDeletion() = runBlocking<Unit> {
        val (file, uri) = source("c.html", "keep me")
        val id = repo.import(uri)
        file.delete()
        assertEquals("keep me", repo.open(id).text)
    }

    @Test
    fun openUpdatesLastOpened() = runBlocking<Unit> {
        val (_, uri) = source("d.md", "x")
        val id = repo.import(uri)
        now = 5_000L
        repo.open(id)
        assertEquals(5_000L, db.bookDao().get(id)!!.lastOpenedAt)
    }

    @Test
    fun deleteRemovesRowAndLocalCopy() = runBlocking<Unit> {
        val (_, uri) = source("e.html", "x")
        val id = repo.import(uri)
        val copy = File(shelfDir, db.bookDao().get(id)!!.localFile)
        assertTrue(copy.exists())

        repo.delete(id)

        assertNull(db.bookDao().get(id))
        assertFalse(copy.exists())
    }

    @Test
    fun noTempFilesLeftBehind() = runBlocking<Unit> {
        val (_, uri) = source("f.md", "x")
        repo.import(uri)
        assertTrue(shelfDir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test(expected = IOException::class)
    fun missingLocalCopyGivesClearError() = runBlocking<Unit> {
        val (_, uri) = source("g.md", "x")
        val id = repo.import(uri)
        File(shelfDir, db.bookDao().get(id)!!.localFile).delete()
        repo.open(id)
    }
}
