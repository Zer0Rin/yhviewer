package com.example.yhviewer

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real SAF/native-WebView acceptance. Optional external30 pack stays outside the public repository. */
@androidx.test.filters.SdkSuppress(minSdkVersion = 29)
@RunWith(AndroidJUnit4::class)
class V10ExternalPackAcceptanceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val db get() = AppDatabase.get(context)
    private val repo get() = ShelfRepository(context, db)
    private val authority = "com.example.yhviewer.v10.fixtures"
    private val base get() = Uri.parse("content://$authority")
    private fun tree(root: String = "generic") = DocumentsContract.buildTreeDocumentUri(authority, "$root:")
    private fun uri(path: String, root: String = "generic") = DocumentsContract.buildDocumentUriUsingTree(tree(root), "$root:$path")
    private fun owned(): List<Book> = runBlocking { repo.observe("", false).first().filter { it.sourceUri.startsWith(base.toString()) } }
    @Before fun setUp() {
        owned().forEach { runBlocking { repo.delete(it.id) } }
        // Bootstrap the test-APK provider as the shell document picker would, then drop identity.
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.MANAGE_DOCUMENTS)
        try { assertTrue(context.contentResolver.call(base, "fixtureReset", null, null)!!.getBoolean("ready")) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }
    @After fun tearDown() { owned().forEach { runBlocking { repo.delete(it.id) } } }
    private fun launch(intent: Intent = Intent(context, MainActivity::class.java)) = ActivityScenario.launch<MainActivity>(intent)
    private fun model(s: ActivityScenario<MainActivity>, action: (ShelfViewModel) -> Unit) {
        s.onActivity { action(ViewModelProvider(it)[ShelfViewModel::class.java]) }; compose.waitForIdle()
    }
    private fun open(s: ActivityScenario<MainActivity>, id: Long) {
        model(s) { it.openBook(id) }
        compose.waitUntil(30_000) {
            var ready = false
            s.onActivity { ready = (ViewModelProvider(it)[ShelfViewModel::class.java].reader as? ReaderState.Ready)?.doc?.bookId == id }
            ready
        }
        waitWeb(s)
    }
    private fun web(s: ActivityScenario<MainActivity>): WebView? {
        var result: WebView? = null
        s.onActivity { a ->
            fun visit(v: View) {
                if (v is WebView && v.visibility == View.VISIBLE) result = v
                if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))
            }
            visit(a.window.decorView)
        }
        return result
    }
    private fun waitWeb(s: ActivityScenario<MainActivity>) {
        compose.waitUntil(30_000) {
            var ready = false
            s.onActivity { web(s)?.let { ready = it.progress == 100 && it.contentHeight > 0 && it.url != null } }
            ready
        }
    }
    private fun eval(s: ActivityScenario<MainActivity>, script: String): String? {
        val done = CountDownLatch(1); var result: String? = null
        s.onActivity { web(s)!!.evaluateJavascript(script) { result = it; done.countDown() } }
        assertTrue("JavaScript evaluation timed out", done.await(5, TimeUnit.SECONDS))
        return result
    }
    private fun fire(s: ActivityScenario<MainActivity>, script: String) { s.onActivity { web(s)!!.evaluateJavascript(script, null) } }
    private fun waitText(text: String) = compose.waitUntil(30_000) { compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("v7Screenshots") != "true") return
        // Native dialog/window entry animations are outside Compose's idling resource.
        android.os.SystemClock.sleep(400)
        compose.waitForIdle()
        exportV7Screenshot(name)
    }
    private fun folder(root: String = "generic") = runBlocking { repo.importFolder(tree(root)) }
    private fun book(name: String) = owned().single { it.name == name }
    private fun nativeFind(s: ActivityScenario<MainActivity>, query: String): Int {
        val done = CountDownLatch(1); var found = 0
        s.onActivity { web(s)!!.apply {
            setFindListener { _, count, complete -> if (complete) { found = count; done.countDown() } }
            findAllAsync(query)
        } }
        assertTrue("Native find timed out for $query", done.await(10, TimeUnit.SECONDS))
        return found
    }

    @Test fun folderCopiesResourcesNavigatesEditsAndReports() {
        val report = folder()
        assertEquals(report.toString(), 6, report.imported); assertEquals(2, report.resources)
        assertTrue(report.failed.toString(), report.failed.isEmpty()); assertEquals(1, report.skipped.size)
        val page = book("page.html")
        launch().use { s ->
            open(s, page.id)
            compose.waitUntil(20_000) { eval(s, "getComputedStyle(document.body).backgroundColor") == "\"rgb(230, 248, 230)\"" }
            assertEquals("true", eval(s, "document.images[0].complete && document.images[0].naturalWidth > 0"))
            assertEquals("1", eval(s, "document.querySelectorAll('link[rel=stylesheet]').length"))
            fire(s, "document.querySelector('a').click()")
            compose.waitUntil(15_000) {
                var ready = false
                s.onActivity { web(s)?.let { ready = it.url?.endsWith("#second") == true && it.progress == 100 } }
                ready
            }
            waitWeb(s)
            assertEquals("true", eval(s, "document.body.innerText.includes('SECOND-END')"))
            val old = runBlocking { repo.open(page.id) as OpenedDoc.Single }
            runBlocking { repo.updateContent(page.id, old.doc.text.replace("Fixture page", "Edited fixture")) }
            open(s, page.id)
            compose.waitUntil(15_000) { eval(s, "getComputedStyle(document.body).backgroundColor") == "\"rgb(230, 248, 230)\"" }
            assertEquals("true", eval(s, "document.body.innerText.includes('Edited fixture')"))
            model(s) { it.backToShelf(); it.importFolder(tree()) }
            waitText("导入完成")
            capture("import-report.png")
        }
    }

    @Test fun singleFileMissingResourcesNativeFindAndDialogBlocking() {
        val id = runBlocking { repo.import(uri("page.html")) }
        launch().use { s ->
            open(s, id)
            waitText("单个文件导入")
            compose.onNodeWithText("搜索", useUnmergedTree = true).performClick()
            compose.onNodeWithContentDescription("查找内容").performTextInput("needle")
            waitText("1/2")
            compose.onNodeWithText("↓").performClick(); waitText("2/2")
            model(s) { it.jumpTo(0, .3f) }; waitWeb(s)
            waitText("1/2")
            compose.onNodeWithText("↓").performClick(); waitText("2/2")
            compose.onAllNodesWithText("关闭", useUnmergedTree = true).onFirst().performClick()
            // Reopening the same query must restore highlights after clearFind.
            compose.onNodeWithText("搜索", useUnmergedTree = true).performClick(); waitText("1/2")
            compose.onAllNodesWithText("关闭", useUnmergedTree = true).onFirst().performClick()
            repeat(3) { i ->
                fire(s, "alert('fixture alert')")
                waitText("fixture alert"); waitText("page.html")
                if (i == 2) compose.onNode(isToggleable()).performClick()
                compose.onNodeWithText("确定").performClick()
            }
            fire(s, "alert('blocked alert');window.afterBlockedAlert=true")
            compose.waitUntil(10_000) { eval(s, "window.afterBlockedAlert===true") == "true" }
            compose.onNodeWithText("blocked alert").assertDoesNotExist()
        }
    }

    @Test fun multipleAndShareImportKeepShelfSingleOpensReader() {
        val uris = listOf(uri("notes.md"), uri("sample.log"), uri("preview.svg"))
        launch().use { s ->
            compose.onNodeWithText("＋ 打开").performClick()
            compose.onNodeWithText("选择文件").assertIsDisplayed()
            compose.onNodeWithText("导入文件夹").assertIsDisplayed()
            androidx.test.espresso.Espresso.pressBack()
            model(s) { it.importMany(uris) }
            compose.waitUntil(20_000) { owned().size == 3 }
            var shelf = false; s.onActivity { shelf = ViewModelProvider(it)[ShelfViewModel::class.java].screen == Screen.Shelf }
            assertTrue(shelf)
            model(s) { it.dismissImportReport(); it.importMany(listOf(uri("page.html"))) }
            compose.waitUntil(20_000) {
                var reader = false; s.onActivity { reader = ViewModelProvider(it)[ShelfViewModel::class.java].screen == Screen.Reader }; reader
            }
        }
        owned().forEach { runBlocking { repo.delete(it.id) } }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND_MULTIPLE; type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(intent).use { s ->
            compose.waitUntil(20_000) { owned().size == 3 }
            var count = 0; s.onActivity { count = ViewModelProvider(it)[ShelfViewModel::class.java].importReport?.imported ?: 0 }
            compose.waitUntil(10_000) { s.onActivity { count = ViewModelProvider(it)[ShelfViewModel::class.java].importReport?.imported ?: 0 }; count == 3 }
            assertEquals(3, count)
        }
    }

    @Test fun bookmarksSearchNthHitFootnotesAndSvgSource() {
        folder()
        val novel = book("novel.txt")
        launch().use { s ->
            open(s, book("notes.md").id)
            compose.waitUntil(20_000) { eval(s, "!!document.querySelector('sup.footnote-ref') && !!document.querySelector('section.footnotes')") == "true" }
            open(s, book("preview.svg").id)
            assertEquals("true", eval(s, "document.images.length===1 && document.images[0].complete && document.images[0].naturalWidth>0"))
            compose.onNodeWithContentDescription("更多").performClick()
            compose.onNodeWithText("查看源码").performClick()
            compose.waitUntil(15_000) { eval(s, "document.body.innerText.includes('<svg')") == "true" }
            open(s, novel.id)
            var hits = emptyList<SearchHit>()
            val texts = runBlocking { repo.chapterTexts(repo.open(novel.id)) }
            hits = BookSearch.search(texts, "needle")
            val chapter = hits.maxOf { it.chapter }
            val hit = hits.filter { it.chapter == chapter }[2]
            model(s) { it.jumpTo(chapter, .95f) }; waitWeb(s)
            model(s) { it.openHit(hit, "needle", 2) }
            waitText("3/${hits.count { it.chapter == chapter }}")
            compose.onNodeWithText("全书").performClick()
            waitText("「needle」共")
            capture("book-search.png")
            androidx.test.espresso.Espresso.pressBack()
            model(s) { it.jumpTo(chapter, .45f) }
            var addResult: Result<Boolean>? = null
            model(s) { it.addBookmark("终点") { result -> addResult = result } }
            compose.waitUntil(15_000) { addResult != null }
            assertEquals(true, addResult!!.getOrThrow())
            compose.waitUntil(15_000) { runBlocking { repo.observeBookmarks(novel.id).first().size } == 1 }
            val bookmark = runBlocking { repo.observeBookmarks(novel.id).first().single() }
            addResult = null
            model(s) { it.addBookmark("终点") { result -> addResult = result } }
            compose.waitUntil(15_000) { addResult != null }
            assertEquals(false, addResult!!.getOrThrow())
            model(s) { it.backToShelf() }
            open(s, novel.id)
            model(s) { it.goToChapter(0) }; waitWeb(s)
            compose.onNodeWithText("目录").performClick()
            waitText("书签 · 1")
            compose.onNodeWithText("书签 · 1").performClick()
            waitText(bookmark.label)
            compose.onNodeWithText(bookmark.label).performClick()
            waitWeb(s)
            var position = 0f; s.onActivity { position = ViewModelProvider(it)[ShelfViewModel::class.java].initialRatio() }
            assertEquals(bookmark.ratio, position, .05f)
        }
        runBlocking { repo.delete(novel.id) }
        assertTrue(runBlocking { repo.observeBookmarks(novel.id).first().isEmpty() })
        assertEquals(ContentKind.TEXT.id, book("sample.log").kind)
    }

    @Test fun reimportRetainsReferencedOldBundleAndMetadata() {
        folder()
        val page = book("page.html"); val other = book("other.html")
        val old = runBlocking { repo.open(other.id) as OpenedDoc.Single }.bundle!!
        runBlocking { repo.setFavorite(listOf(page.id), true); repo.savePosition(page.id, Locator(0, .5f), 1) }
        context.contentResolver.call(base, "removeGeneric", "other.html", null)
        val report = folder(); assertTrue(report.failed.toString(), report.failed.isEmpty())
        assertTrue(old.rootDir.resolve(old.relPath).isFile)
        assertTrue(old.rootDir.resolve("page.html").isFile)
        val updated = runBlocking { db.bookDao().get(page.id)!! }
        assertTrue(updated.favorite); assertEquals(.5f, updated.progress, .001f)
        runBlocking { repo.delete(other.id) }
        assertFalse(old.rootDir.exists())
    }

    @Test fun externalThirtyFixturesImportAndFindEveryTailMarker() {
        assumeTrue("Opt-in real fixture pack: see docs/testing/v10-acceptance.md", InstrumentationRegistry.getArguments().getString("v10ExternalPack") == "true")
        assertTrue(context.contentResolver.call(base, "externalReady", null, null)!!.getBoolean("ready"))
        val report = folder("external")
        assertEquals(report.toString(), 34, report.imported); assertEquals(2, report.resources)
        assertTrue(report.toString(), report.failed.isEmpty()); assertTrue(report.toString(), report.skipped.isEmpty())
        val samples = owned().filter { it.sourceUri.contains(Regex("0[1-6]_")) }
        assertEquals(30, samples.size)
        launch().use { s ->
            for (book in samples.sortedBy { it.sourceUri }) {
                val doc = runBlocking { repo.open(book.id) }
                val texts = runBlocking { repo.chapterTexts(doc) }
                val marker = Regex("[A-Z][0-9]{2}-END").findAll(texts.joinToString("\n")).lastOrNull()?.value ?: "lastBook".takeIf { book.name.endsWith(".json") }
                assertNotNull("Missing fixture marker: ${book.name}", marker)
                val hit = BookSearch.search(texts, marker!!).last()
                open(s, book.id)
                if (doc is OpenedDoc.Txt || doc is OpenedDoc.Epub) {
                    model(s) { it.goToChapter(hit.chapter) }; waitWeb(s)
                } else if ((doc as? OpenedDoc.Single)?.doc?.isSvg == true) {
                    compose.onNodeWithContentDescription("更多").performClick(); compose.onNodeWithText("查看源码").performClick(); waitWeb(s)
                    compose.waitUntil(15_000) { eval(s, "document.readyState==='complete' && document.body.innerText.includes('<svg')") == "true" }
                }
                compose.waitUntil(25_000) { nativeFind(s, marker) > 0 }
                if (book.name.startsWith("02_") && book.type == DocType.HTML) {
                    assertEquals("true", eval(s, "document.images.length>0 && [...document.images].every(i=>i.complete&&i.naturalWidth>0)"))
                    assertEquals("\"rgb(230, 246, 239)\"", eval(s, "getComputedStyle(document.querySelector('.external-css-marker')).backgroundColor"))
                }
                if (doc is OpenedDoc.Epub) assertEquals(book.sourceUri.contains("04_"), doc.vertical)
                if (doc is OpenedDoc.Epub && book.sourceUri.contains("04_")) s.onActivity {
                    assertFalse("Vertical EPUB must use vertical scrolling after normalization", web(s)!!.canScrollHorizontally(1) || web(s)!!.canScrollHorizontally(-1))
                }
                if (doc is OpenedDoc.Epub && book.sourceUri.contains("02_")) assertTrue(repo.covers.exists(book.id))
                android.util.Log.i("V10Pack", "PASS ${book.name}: nativeFind($marker), chapter ${hit.chapter}")
                model(s) { it.backToShelf() }
            }
        }
    }
}
