package com.example.yhviewer

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ShelfRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: ShelfRepository
    private lateinit var root: File
    private lateinit var shelfDir: File
    private lateinit var srcDir: File
    private var now = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        root = File(context.cacheDir, "shelf-test-${UUID.randomUUID()}")
        shelfDir = File(root, "shelf")
        srcDir = File(root, "src").apply { mkdirs() }
        repo = ShelfRepository(context, db, dir = shelfDir, clock = { now }, tmpDir = File(root, "tmp").apply { mkdirs() }, covers = CoverStore(File(root, "covers")))
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private fun source(name: String, bytes: ByteArray): Pair<File, Uri> {
        val f = File(srcDir, name).apply { writeBytes(bytes) }
        return f to Uri.fromFile(f)
    }

    private fun source(name: String, text: String) = source(name, text.toByteArray(Charsets.UTF_8))

    private suspend fun openSingle(id: Long) = repo.open(id) as OpenedDoc.Single

    @Test fun concurrentBookmarkAddsUsePersistedStateBeforeObserverDelivery() = runBlocking<Unit> {
        val (_, uri) = source("bookmark.md", "# book")
        val id = repo.import(uri)
        val first = Bookmark(bookId = id, chapter = 0, ratio = 0.25f, label = "first", createdAt = 0)
        // Start both writes without collecting the Flow: UI observer delivery cannot deduplicate these.
        val a = async(start = CoroutineStart.UNDISPATCHED) { repo.addBookmark(first) }
        val b = async(start = CoroutineStart.UNDISPATCHED) { repo.addBookmark(first.copy(ratio = 0.255f)) }
        assertEquals(listOf(false, true), listOf(a.await(), b.await()).sorted())
        assertEquals(1, repo.observeBookmarks(id).first().size)
        repo.addBookmark(first.copy(ratio = 0.5f))
        repo.addBookmark(first.copy(chapter = 1))
        assertEquals(3, repo.observeBookmarks(id).first().size)
    }

    // ---------- 单页文档（沿用第 5 步的行为） ----------

    @Test fun importThenOpenRoundTrip() = runBlocking<Unit> {
        val (_, uri) = source("a.md", "# hi")
        val doc = openSingle(repo.import(uri)).doc
        assertEquals("a.md", doc.name)
        assertEquals(DocType.MARKDOWN, doc.type)
        assertEquals("# hi", doc.text)
    }

    @Test fun codeFileIsDetected() = runBlocking<Unit> {
        val (_, uri) = source("Main.kt", "fun main() {}")
        assertEquals(DocType.CODE, openSingle(repo.import(uri)).doc.type)
    }

    @Test fun reimportSameUriReplacesCopyAndKeepsPosition() = runBlocking<Unit> {
        val (file, uri) = source("b.html", "v1")
        val id1 = repo.import(uri)
        val oldCopy = db.bookDao().get(id1)!!.localFile
        repo.savePosition(id1, Locator(0, 0.5f), 1)

        file.writeText("v2")
        now = 2_000L
        val id2 = repo.import(uri)

        assertEquals(id1, id2)
        assertEquals(1, repo.observe("", false).first().size)
        assertEquals("v2", openSingle(id1).doc.text)
        assertFalse("旧副本应已删除", File(shelfDir, oldCopy).exists())
        assertEquals(0.5f, db.bookDao().get(id1)!!.progress, 1e-4f)
    }

    @Test fun copySurvivesSourceDeletion() = runBlocking<Unit> {
        val (file, uri) = source("c.html", "keep me")
        val id = repo.import(uri)
        file.delete()
        assertEquals("keep me", openSingle(id).doc.text)
    }

    @Test fun deleteRemovesRowAndLocalCopy() = runBlocking<Unit> {
        val (_, uri) = source("e.html", "x")
        val id = repo.import(uri)
        val copy = File(shelfDir, db.bookDao().get(id)!!.localFile)
        assertTrue(copy.exists())
        repo.delete(id)
        assertNull(db.bookDao().get(id))
        assertFalse(copy.exists())
    }

    @Test fun noTempFilesLeftBehind() = runBlocking<Unit> {
        repo.import(source("f.md", "x").second)
        assertTrue(shelfDir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test(expected = IOException::class)
    fun missingLocalCopyGivesClearError() = runBlocking<Unit> {
        val id = repo.import(source("g.md", "x").second)
        File(shelfDir, db.bookDao().get(id)!!.localFile).delete()
        repo.open(id)
    }

    // ---------- TXT ----------

    @Test fun gbkTxtIsNormalizedToUtf8AndSplit() = runBlocking<Unit> {
        val text = "第一章 夜车\n　　夜色渐深。\n第二章 抵达\n　　天亮了。"
        val (_, uri) = source("novel.txt", text.toByteArray(Charset.forName("GBK")))
        val id = repo.import(uri)

        val copy = File(shelfDir, db.bookDao().get(id)!!.localFile)
        assertEquals("副本应已转成 UTF-8", text, copy.readText(Charsets.UTF_8))

        val opened = repo.open(id) as OpenedDoc.Txt
        assertEquals(listOf("第一章 夜车", "第二章 抵达"), opened.chapters.map { it.title })
    }

    @Test fun positionIsSavedAndRestored() = runBlocking<Unit> {
        val (_, uri) = source("n.txt", "第一章 甲\n内容\n第二章 乙\n内容\n第三章 丙\n内容\n第四章 丁\n内容")
        val id = repo.import(uri)
        repo.savePosition(id, Locator(2, 0.5f), 4)

        val opened = repo.open(id) as OpenedDoc.Txt
        assertEquals(Locator(2, 0.5f), opened.locator)
        assertEquals(0.625f, db.bookDao().get(id)!!.progress, 1e-4f) // (2 + 0.5) / 4
    }

    // ---------- EPUB ----------

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name)); zip.write(data.toByteArray()); zip.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun minimalEpub(title: String = "夜车") = zipOf(
        "mimetype" to "application/epub+zip",
        "META-INF/container.xml" to """<?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="content.opf"/></rootfiles></container>""",
        "content.opf" to """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>$title</dc:title></metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""",
        "c1.xhtml" to """<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>c</title></head><body><p>hi</p></body></html>""",
    )

    @Test fun epubIsExtractedAndUsesBookTitle() = runBlocking<Unit> {
        val id = repo.import(source("book.epub", minimalEpub()).second)
        val row = db.bookDao().get(id)!!
        assertEquals("夜车", row.name)
        assertEquals(DocType.EPUB, row.type)
        assertTrue(File(shelfDir, row.localFile).isDirectory)

        val opened = repo.open(id) as OpenedDoc.Epub
        assertEquals("c1.xhtml", opened.chapters.single().path)
    }

    @Test fun maliciousEpubIsRejectedAndLeavesNothing() = runBlocking<Unit> {
        val evil = zipOf("../../evil.txt" to "x")
        try {
            repo.import(source("evil.epub", evil).second)
            fail("应当拒绝")
        } catch (e: IOException) {
            // EpubFormatException 或系统 ZipException，都是 IOException
        }
        assertEquals(0, repo.observe("", false).first().size)
        assertTrue("书架目录里不应残留任何文件", shelfDir.listFiles().orEmpty().isEmpty())
        assertTrue(File(root, "tmp").listFiles().orEmpty().isEmpty())
    }

    @Test fun reimportEpubSwapsDirectory() = runBlocking<Unit> {
        val (file, uri) = source("book.epub", minimalEpub("v1"))
        val id = repo.import(uri)
        val oldDir = db.bookDao().get(id)!!.localFile
        file.writeBytes(minimalEpub("v2"))
        repo.import(uri)
        val row = db.bookDao().get(id)!!
        assertEquals("v2", row.name)
        assertFalse(File(shelfDir, oldDir).exists())
    }

    // ---------- 孤儿清理 ----------

    @Test fun cleanOrphansRemovesUnreferencedFilesOnly() = runBlocking<Unit> {
        val id = repo.import(source("keep.md", "x").second)
        File(shelfDir, "orphan").writeText("x")
        File(shelfDir, "half.tmp").mkdirs()

        assertEquals(2, repo.cleanOrphans())
        assertTrue(File(shelfDir, db.bookDao().get(id)!!.localFile).exists())
    }
    @Test fun cancelledReplacementDoesNotLeakOrDeleteTheOldCopy() = runBlocking<Unit> {
        for ((name, bytes) in listOf("cancel.md" to "old".toByteArray(), "cancel.epub" to minimalEpub())) {
            val (_, uri) = source(name, bytes)
            val id = repo.import(uri)
            val old = db.bookDao().get(id)!!
            val before = shelfDir.listFiles()!!.map { it.name }.toSet()
            lateinit var task: Deferred<Long>
            val cancelling = ShelfRepository(context, db, dir = shelfDir, clock = { task.cancel(); now }, tmpDir = File(root, "tmp"))
            task = async(start = CoroutineStart.LAZY) { cancelling.import(uri) }
            task.start(); task.join()
            assertTrue(task.isCancelled)
            assertEquals(old, db.bookDao().get(id))
            assertEquals("Cancelled imports must leave no new file", before, shelfDir.listFiles()!!.map { it.name }.toSet())
            assertTrue(File(shelfDir, old.localFile).exists())
        }
    }

    @Test fun classificationReimportAndEditorSaveFollowContentWithoutChangingReader() = runBlocking<Unit> {
        val (file, uri) = source("response.unknown", "plain text")
        val id = repo.import(uri)
        assertEquals(ContentKind.TEXT.id, db.bookDao().get(id)!!.kind)
        val group = repo.createGroup("工作")
        repo.moveToGroup(listOf(id), group)
        repo.setFavorite(listOf(id), true)
        repo.savePosition(id, Locator(0, .5f), 1)
        file.writeText("{\"answer\":42}")
        repo.import(uri)
        val data = db.bookDao().get(id)!!
        assertEquals(ContentKind.DATA.id, data.kind)
        assertEquals(DocType.UNKNOWN, data.type)
        assertEquals(group, data.groupId)
        assertTrue(data.favorite)
        assertEquals(.5f, data.progress, 0f)
        repo.updateContent(id, "<!doctype html><html><body>hello</body></html>")
        assertEquals(ContentKind.WEB.id, db.bookDao().get(id)!!.kind)
        assertEquals(DocType.UNKNOWN, db.bookDao().get(id)!!.type)
    }

    @Test fun deletingGroupPreservesFilesAndReadingMetadata() = runBlocking<Unit> {
        val id = repo.import(source("group.md", "# hi").second)
        val group = repo.createGroup("待读")
        repo.moveToGroup(listOf(id), group)
        repo.savePosition(id, Locator(0, .75f), 1)
        val before = db.bookDao().get(id)!!
        repo.deleteGroup(group)
        assertEquals(before.copy(groupId = null), db.bookDao().get(id))
        assertTrue(File(shelfDir, before.localFile).exists())
        assertTrue(db.groupDao().all().isEmpty())
    }

    @Test fun concurrentCaseInsensitiveGroupCreationAllowsOnlyOneName() = runBlocking<Unit> {
        val results = listOf("Work", " work ").map { name ->
            async { runCatching { repo.createGroup(name) } }
        }.map { it.await() }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, db.groupDao().all().size)
    }

    @Test fun movingToDeletedGroupIsRejected() = runBlocking<Unit> {
        val id = repo.import(source("group.md", "x").second)
        val group = repo.createGroup("待读")
        repo.deleteGroup(group)
        try {
            repo.moveToGroup(listOf(id), group)
            fail("Deleted group must not be referenced")
        } catch (_: IllegalArgumentException) { }
        assertNull(db.bookDao().get(id)!!.groupId)
    }

    @Test fun startupBackfillAndCoverCleanupRetainOnlyLiveData() = runBlocking<Unit> {
        val id = repo.import(source("data.txt", "{\"x\":1}").second)
        db.bookDao().update(db.bookDao().get(id)!!.copy(kind = null))
        assertEquals(1, repo.backfillKinds())
        assertEquals(ContentKind.DATA.id, db.bookDao().get(id)!!.kind)
        assertEquals(0, repo.backfillKinds())
        val coversDir = repo.covers.file(id).parentFile!!.apply { mkdirs() }
        repo.covers.file(id).writeText("live")
        File(coversDir, "99999.jpg").writeText("orphan")
        File(coversDir, "$id.jpg.tmp").writeText("incomplete")
        assertEquals(2, repo.cleanOrphans())
        assertTrue(repo.covers.exists(id))
        repo.deleteMany(listOf(id))
        assertFalse(repo.covers.exists(id))
        assertNull(db.bookDao().get(id))
    }

    @Test fun authorSearchAndReimportReplaceMetadataEvenWhenOptionalCoverUpdateFails() = runBlocking<Unit> {
        fun book(author: String) = zipOf(
            "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="content.opf"/></rootfiles></container>""",
            "content.opf" to """<package><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Test book</dc:title><dc:creator>$author</dc:creator></metadata><manifest><item id="c" href="c.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c"/></spine></package>""",
            "c.xhtml" to "<html><body>chapter</body></html>",
        )
        val (file, uri) = source("author.epub", book("First Author"))
        val id = repo.import(uri)
        assertEquals("First Author", db.bookDao().get(id)!!.author)
        assertEquals(listOf(id), repo.observe("First Author", false).first().map { it.id })
        repo.covers.file(id).parentFile!!.mkdirs()
        repo.covers.file(id).writeText("stale cover")
        // Optional metadata update fails after the content transaction commits.
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_cover_metadata BEFORE UPDATE OF author ON books
            WHEN NEW.coverChecked = 1 BEGIN SELECT RAISE(ABORT, 'metadata unavailable'); END
        """.trimIndent())
        file.writeBytes(book("Second Author"))
        assertEquals(id, repo.import(uri))
        val row = db.bookDao().get(id)!!
        assertEquals("Second Author", row.author)
        assertFalse(row.coverChecked)
        assertTrue(File(shelfDir, row.localFile).isDirectory)
        assertFalse(repo.covers.exists(id))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_cover_metadata")
        assertEquals(0, repo.backfillCovers())
        assertTrue(db.bookDao().get(id)!!.coverChecked)
        assertTrue(repo.observe("First Author", false).first().isEmpty())
        assertEquals(listOf(id), repo.observe("Second Author", false).first().map { it.id })
    }

    @Test fun createGroupAndMoveIsAtomicAndSupportsLargeSelections() = runBlocking<Unit> {
        val template = Book(sourceUri = "", name = "item.txt", type = DocType.TXT,
            localFile = "unused", sizeBytes = 1, addedAt = 0, lastOpenedAt = 0)
        val ids = (1..1001).map { db.bookDao().insert(template.copy(sourceUri = "synthetic:$it")) }
        val group = repo.createGroupAndMove("批量", ids)
        assertTrue(db.bookDao().observe("", false).first().all { it.groupId == group })
        repo.setFavorite(ids, true)
        assertEquals(ids.size, repo.observe("", true).first().size)
        repo.moveToGroup(ids, null)
        assertTrue(db.bookDao().observe("", false).first().all { it.groupId == null })
    }

}
