package com.example.yhviewer

import android.content.Context
import android.net.Uri
import android.system.Os
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EditorRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var root: File
    private lateinit var shelf: File
    private lateinit var repo: ShelfRepository
    @Volatile private var onContentUpdate: (() -> Unit)? = null

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCallback({ sql, _ ->
                if (sql.startsWith("UPDATE books SET localFile")) onContentUpdate?.invoke()
            }, Executor { it.run() })
            .build()
        root = File(context.cacheDir, "editor-test-${UUID.randomUUID()}").apply { mkdirs() }
        shelf = File(root, "shelf")
        repo = newRepository()
    }

    @After fun tearDown() {
        onContentUpdate = null
        db.close()
        root.deleteRecursively()
    }

    private fun newRepository() = ShelfRepository(context, db, dir = shelf, clock = { 1000L })
    private fun source(name: String, text: String): File = File(root, name).apply { writeText(text) }
    private suspend fun imported(name: String = "document.md"): Long = repo.import(Uri.fromFile(source(name, "original")))
    private fun copies() = shelf.listFiles().orEmpty().map { it.name }.toSet()
    private suspend fun rejects(block: suspend () -> Unit) {
        try { block(); fail("Unsafe editor save must fail") } catch (_: IOException) { }
    }

    @Test fun unicodeSaveReplacesOnlyShelfContentAndPreservesMetadata() = runBlocking<Unit> {
        val original = source("unicode.html", "original")
        val id = repo.import(Uri.fromFile(original))
        val dao = db.bookDao()
        val before = dao.get(id)!!.copy(
            favorite = true, locator = Locator(0, .61f).encode(), progress = .61f,
            permissions = DocPermissions.STANDARD.toBits() or 2,
        )
        dao.update(before)
        val text = "<h1>繁體中文 😀</h1>\n𠮷 café\t\r\n"

        repo.updateContent(id, text)

        val after = dao.get(id)!!
        assertNotEquals(before.localFile, after.localFile)
        assertEquals(before.copy(localFile = after.localFile, sizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong()), after)
        assertEquals("original", original.readText())
        assertFalse(File(shelf, before.localFile).exists())
        assertEquals(text.toByteArray(Charsets.UTF_8).toList(), File(shelf, after.localFile).readBytes().toList())
        assertEquals(setOf(after.localFile), copies())
        assertEquals(text, (newRepository().open(id) as OpenedDoc.Single).doc.text)
    }

    @Test fun allSingleDocumentTypesCanBeSaved() = runBlocking<Unit> {
        for (name in listOf("a.html", "b.md", "c.kt", "d.unknown")) {
            val id = imported(name)
            repo.updateContent(id, "edited $name")
            assertEquals("edited $name", (repo.open(id) as OpenedDoc.Single).doc.text)
        }
    }

    @Test fun missingRowDoesNotCreateAFile() = runBlocking<Unit> {
        rejects { repo.updateContent(Long.MAX_VALUE, "new") }
        assertTrue(copies().isEmpty())
    }

    @Test fun txtAndEpubRowsAreNotEditable() = runBlocking<Unit> {
        val txt = imported("novel.txt")
        val epubFile = File(shelf, "epub-directory").apply { mkdirs() }
        val epub = db.bookDao().insert(db.bookDao().get(txt)!!.copy(
            id = 0, sourceUri = "synthetic:epub", type = DocType.EPUB, localFile = epubFile.name,
        ))
        val before = copies()
        for (id in listOf(txt, epub)) {
            val row = db.bookDao().get(id)
            rejects { repo.updateContent(id, "new") }
            assertEquals(row, db.bookDao().get(id))
        }
        assertEquals(before, copies())
    }

    @Test fun oversizedEditorTextKeepsExistingCopy() = runBlocking<Unit> {
        val id = imported()
        val row = db.bookDao().get(id)!!
        rejects { repo.updateContent(id, "x".repeat(1_000_001)) }
        assertEquals(row, db.bookDao().get(id))
        assertEquals(setOf(row.localFile), copies())
        assertEquals("original", File(shelf, row.localFile).readText())
    }

    @Test fun missingAndUnreadableCopiesAreRejected() = runBlocking<Unit> {
        val missingId = imported("missing.md")
        val missing = db.bookDao().get(missingId)!!
        File(shelf, missing.localFile).delete()
        rejects { repo.updateContent(missingId, "new") }
        assertEquals(missing, db.bookDao().get(missingId))
        val unreadableId = imported("unreadable.md")
        val unreadable = db.bookDao().get(unreadableId)!!
        val copy = File(shelf, unreadable.localFile)
        Os.chmod(copy.path, 0)
        try {
            assertFalse("Test requires unreadable file", copy.canRead())
            rejects { repo.updateContent(unreadableId, "new") }
            assertEquals(unreadable, db.bookDao().get(unreadableId))
            assertEquals(setOf(unreadable.localFile), copies())
        } finally { Os.chmod(copy.path, 384) }
    }

    @Test fun unwritableShelfLeavesDatabaseAndOldCopyIntact() = runBlocking<Unit> {
        val id = imported()
        val row = db.bookDao().get(id)!!
        Os.chmod(shelf.path, 320) // r-x: readable old file, no staging-file creation
        try {
            assertFalse("Test requires unwritable directory", shelf.canWrite())
            rejects { repo.updateContent(id, "new") }
            assertEquals(row, db.bookDao().get(id))
            assertEquals(setOf(row.localFile), copies())
            assertEquals("original", File(shelf, row.localFile).readText())
        } finally { Os.chmod(shelf.path, 448) }
    }

    @Test fun cancellationDuringDatabaseUpdateNeverRemovesAReferencedCopy() = runBlocking<Unit> {
        val id = imported()
        lateinit var task: Deferred<Unit>
        onContentUpdate = { task.cancel() }
        task = async(start = CoroutineStart.LAZY) { repo.updateContent(id, "new") }
        task.start()
        task.join()
        assertTrue(task.isCancelled)
        onContentUpdate = null
        val row = db.bookDao().get(id)!!
        assertTrue(File(shelf, row.localFile).isFile)
        repo.cleanOrphans()
        assertEquals(setOf(row.localFile), copies())
    }

    @Test fun cleanupImportAndDeleteWaitForSaveAcrossRepositoryInstances() = runBlocking<Unit> {
        val id = imported()
        val other = newRepository()
        val unrelated = source("other.md", "other")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        onContentUpdate = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "Save transaction was not released" }
        }
        val save = async(Dispatchers.IO) { repo.updateContent(id, "saved") }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val cleanup = async(Dispatchers.IO) { other.cleanOrphans() }
        val import = async(Dispatchers.IO) { other.import(Uri.fromFile(unrelated)) }
        val delete = async(Dispatchers.IO) { other.delete(id) }
        try {
            delay(100)
            assertFalse(cleanup.isCompleted)
            assertFalse(import.isCompleted)
            assertFalse(delete.isCompleted)
        } finally { release.countDown() }
        save.await(); cleanup.await(); delete.await()
        val otherId = import.await()
        val row = db.bookDao().get(otherId)!!
        assertEquals(setOf(row.localFile), copies())
        assertEquals("other", (other.open(otherId) as OpenedDoc.Single).doc.text)
    }
}
