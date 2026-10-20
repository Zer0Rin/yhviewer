package com.example.yhviewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class V2ReaderIntegrationTest {
    private lateinit var legacyChapterMode: LegacyChapterMode
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val dao get() = AppDatabase.get(context).bookDao()
    private val created = mutableListOf<Uri>()
    private val prefs get() = context.getSharedPreferences("reader_prefs", 0)
    private var oldReading = ReadingPrefs()
    private var oldTheme: String? = null
    private var oldWrap: Boolean? = null

    @Before fun prepare() {
        legacyChapterMode = LegacyChapterMode(context)
        oldReading = ReadingPrefsStore(context).load()
        ReadingPrefsStore(context).save(ReadingPrefs(continuous = false))
        oldTheme = prefs.getString("md_theme", null)
        oldWrap = if (prefs.contains("code_wrap")) prefs.getBoolean("code_wrap", false) else null
        assertTrue(prefs.edit().putString("md_theme", "paper").putBoolean("code_wrap", false).commit())
    }
    @After fun clean() {
        ReadingPrefsStore(context).save(oldReading)
        legacyChapterMode.restore()
        runBlocking {
            val repo = ShelfRepository(context, AppDatabase.get(context))
            created.forEach { uri ->
                dao.findBySource(uri.toString())?.let { repo.delete(it.id) }
                context.contentResolver.delete(uri, null, null)
            }
        }
        val edit = prefs.edit()
        oldTheme?.let { edit.putString("md_theme", it) } ?: edit.remove("md_theme")
        oldWrap?.let { edit.putBoolean("code_wrap", it) } ?: edit.remove("code_wrap")
        assertTrue(edit.commit())
    }
    private fun document(name: String, bytes: ByteArray, mime: String = "application/octet-stream"): Uri {
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/YHViewerV2Tests")
        })!!
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        created += uri
        val probed = runBlocking { DocumentLoader.probe(context, uri) }
        android.util.Log.i("V2Fixture", "$name -> ${probed.name} (${probed.type})")
        assertEquals("Fixture filename changed by MediaStore", name, probed.name)
        return uri
    }
    private fun document(name: String, text: String, mime: String = "application/octet-stream") = document(name, text.toByteArray(), mime)
    private fun intent(uri: Uri) = Intent(Intent.ACTION_VIEW).setClass(context, MainActivity::class.java)
        .setDataAndType(uri, context.contentResolver.getType(uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    private fun launch(uri: Uri) = ActivityScenario.launch<MainActivity>(intent(uri))
    private fun web(v: View): WebView? {
        if (v is WebView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) web(v.getChildAt(i))?.let { return it }
        return null
    }
    private fun js(s: ActivityScenario<MainActivity>, expression: String): String {
        val done = CountDownLatch(1); var out = "null"
        s.onActivity { a -> web(a.window.decorView)?.evaluateJavascript(expression) { out = it; done.countDown() } ?: done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS)); return out
    }
    private fun waitJs(s: ActivityScenario<MainActivity>, expression: String) = compose.waitUntil(30_000) { js(s, expression) == "true" }
    private fun waitText(text: String) = compose.waitUntil(25_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun select(label: String) {
        compose.onNodeWithText("Aa").performClick()
        // Both book and document settings now use the Aa bottom sheet. The theme chip row
        // can scroll horizontally on narrow screens, so bring the selected option into view.
        val settingsSheet = compose.onAllNodesWithText("主题").fetchSemanticsNodes().isNotEmpty()
        if (settingsSheet && label == "自动换行") {
            // It moved from a menu item to the CODE sheet's Switch.
            compose.onNode(isToggleable()).performClick()
        } else {
            val option = compose.onNode(hasText(label, substring = true))
            if (settingsSheet) option.performScrollTo().assertIsDisplayed().performClick()
            else option.performClick()
        }
        if (settingsSheet) androidx.test.espresso.Espresso.pressBack()
    }
    private fun nativeRatio(s: ActivityScenario<MainActivity>): Float {
        var result = -1f
        s.onActivity { a -> result = web(a.window.decorView)?.scrollRatio() ?: -1f }
        return result
    }
    private fun waitRatio(s: ActivityScenario<MainActivity>, target: Float) = compose.waitUntil(25_000) { abs(nativeRatio(s) - target) < .025f }
    private fun scroll(s: ActivityScenario<MainActivity>, ratio: Float) {
        // DOM readiness can precede WebView's native contentHeight update.
        compose.waitUntil(20_000) {
            var ready = false
            s.onActivity { a -> web(a.window.decorView)?.let { w ->
                @Suppress("DEPRECATION") val range = w.contentHeight * w.scale - w.height
                ready = w.progress == 100 && w.height > 0 && range > 0
            } }
            ready
        }
        // Scroll after initialization has acknowledged the position, before backgrounding.
        compose.waitUntil(10_000) {
            var reported = false
            s.onActivity { a -> web(a.window.decorView)!!.let { w ->
                @Suppress("DEPRECATION") val range = (w.contentHeight * w.scale).roundToInt() - w.height
                w.scrollTo(0, (range * ratio).roundToInt())
                reported = abs(ViewModelProvider(a)[ShelfViewModel::class.java].initialRatio() - ratio) < .025f
            } }
            reported
        }
    }
    private fun longText(label: String) = (1..100).joinToString("\n") { "$label 第 $it 段。阅读位置应在换主题、旋转和重开后保留。" }

    @Test fun codeLanguagesJsonWrapAndClipboardExcludeGutter() {
        val source = "fun main() {\n    /* multiline\n       comment */\n\n    val value = \"" + "x".repeat(400) + "\"\n    println(value)\n}"
        val kotlin = document("v2-demo.kt", source)
        launch(kotlin).use { s ->
            waitText("v2-demo.kt")
            waitJs(s, "document.querySelectorAll('.l').length===7 && document.getElementById('meta').textContent.includes('Kotlin')")
            assertEquals("true", js(s, "!!document.querySelector('.hljs-keyword') && !!document.querySelector('.hljs-comment') && [...document.querySelectorAll('.n')].every(n=>n.textContent==='')"))
            // DOM construction can precede the viewport/layout update on a cold WebView.
            waitJs(s, "document.getElementById('code').scrollWidth>document.getElementById('code').clientWidth")
            js(s, "window.__gutterLeft=document.querySelector('.n').getBoundingClientRect().left;document.getElementById('code').scrollLeft=120")
            waitJs(s, "document.getElementById('code').scrollLeft>=100 && Math.abs(document.querySelector('.n').getBoundingClientRect().left-window.__gutterLeft)<1")
            js(s, "document.getElementById('code').scrollLeft=0")
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            s.onActivity { a -> web(a.window.decorView)!!.requestFocus(); clipboard.setPrimaryClip(ClipData.newPlainText("test", "sentinel")) }
            js(s, "var r=document.createRange();r.selectNodeContents(document.getElementById('code'));var selection=getSelection();selection.removeAllRanges();selection.addRange(r)")
            instrumentation.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, 0, KeyEvent.META_CTRL_ON))
            instrumentation.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_C, 0, KeyEvent.META_CTRL_ON))
            var copied: String? = null
            compose.waitUntil(10_000) {
                copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.takeUnless { it == "sentinel" }
                copied != null
            }
            assertEquals(source.trim(), copied!!.trim())
            s.onActivity { clipboard.setPrimaryClip(ClipData.newPlainText("test", "sentinel")) }
            js(s, "var cells=document.querySelectorAll('.t');var partial=document.createRange();partial.setStart(cells[2],0);partial.setEndAfter(cells[4].querySelector('.hljs-keyword'));getSelection().removeAllRanges();getSelection().addRange(partial)")
            instrumentation.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, 0, KeyEvent.META_CTRL_ON))
            instrumentation.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_C, 0, KeyEvent.META_CTRL_ON))
            var partialCopy: String? = null
            compose.waitUntil(10_000) {
                partialCopy = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.takeUnless { it == "sentinel" }
                partialCopy != null
            }
            assertEquals("       comment */\n\n    val", partialCopy)
            js(s, "getSelection().removeAllRanges();window.__sameCode=true")
            select("自动换行")
            waitJs(s, "document.getElementById('code').classList.contains('wrap') && document.getElementById('code').scrollWidth<=innerWidth && window.__sameCode===true")
            for ((id, label) in listOf("paper" to "纸张（浅）", "ink" to "墨色（深）", "terminal" to "终端", "auto" to "跟随系统")) {
                select(label)
                waitJs(s, "document.documentElement.dataset.theme==='$id' && window.__sameCode===true")
            }
            for ((name, text, language) in listOf(Triple("v2-demo.py", "def add(a, b):\n    return a + b", "Python"), Triple("v2-demo.yml", "name: reader\nactive: true", "YAML"))) {
                s.onActivity { it.startActivity(intent(document(name, text))) }
                waitText(name); waitJs(s, "document.getElementById('meta')?.textContent.includes('$language') && !!document.querySelector('.hljs-attr,.hljs-keyword')")
            }
            s.onActivity { it.startActivity(intent(document("v2-small.json", "{\"name\":\"reader\",\"enabled\":true,\"items\":[1,2,3]}", "application/json"))) }
            waitText("v2-small.json")
            waitJs(s, "document.getElementById('meta')?.textContent.includes('已格式化单行 JSON') && document.querySelectorAll('.l').length>2")
        }
        launch(kotlin).use { s ->
            waitText("v2-demo.kt")
            waitJs(s, "document.getElementById('code')?.classList.contains('wrap')")
        }
    }

    @Test fun largePlaintextLogRendersInChunksWithoutHighlighting() {
        val text = "2026-10-15 INFO request completed successfully; content is local only\n".repeat(32_000)
        assertTrue(text.toByteArray().size > 2_000_000)
        launch(document("v2-large.log", text)).use { s ->
            waitText("v2-large.log")
            waitJs(s, "document.getElementById('meta')?.textContent.includes('文件较大，已关闭高亮')")
            assertEquals("true", js(s, "document.querySelectorAll('.chunk').length>1 && document.querySelectorAll('.l').length===32000 && !document.querySelector('.hljs-keyword')"))
            assertEquals("Skipped chunks must not create a giant blank horizontal range", "true",
                js(s, "document.getElementById('code').scrollWidth<1000"))
            compose.onNodeWithText("Aa").performClick()
            compose.onNode(hasText("终端", substring = true)).assertIsDisplayed()
        }
    }

    @Test fun gbkTxtTocAndRelativePositionSurviveThemeRotationAndReopen() {
        val headings = listOf("第一章 开始", "第二章 继续", "第三章 抵达")
        val novel = headings.joinToString("\n") + "\n\n" + headings.mapIndexed { i, h -> "$h\n${longText("章节${i + 1}")}" }.joinToString("\n")
        val uri = document("v2-gbk-novel.txt", novel.toByteArray(Charset.forName("GBK")))
        launch(uri).use { s ->
            waitText(headings[0]); waitJs(s, "document.getElementById('content')?.textContent.includes('章节1')")
            val row = runBlocking { dao.findBySource(uri.toString())!! }
            assertEquals(novel, File(context.filesDir, "shelf/${row.localFile}").readText())
            compose.onNodeWithText("目录").performClick(); waitText("目录 · 3 章")
            compose.onNodeWithText(headings[1]).performClick()
            // The modal drawer remains composed offscreen. Wait for its closing animation
            // and target chapter DOM before using the reader's identically labeled controls.
            compose.waitForIdle()
            waitJs(s, "document.getElementById('content')?.textContent.includes('章节2')")
            compose.onNode(hasText("下一章 ›") and hasAnyAncestor(hasTestTag("BookReaderBackground"))).performClick(); waitText(headings[2])
            compose.onNode(hasText("‹ 上一章") and hasAnyAncestor(hasTestTag("BookReaderBackground"))).performClick(); waitText(headings[1])
            waitJs(s, "document.getElementById('content')?.textContent.includes('章节2')")
            // Let initial layout restoration finish before simulating reading.
            compose.waitUntil(10_000) { nativeRatio(s) >= 0f }
            scroll(s, .45f)
            compose.waitUntil(10_000) { runBlocking { Locator.decode(dao.get(row.id)!!.locator).let { it.chapter==1 && abs(it.ratio-.45f)<.025f } } }
            select("终端"); waitJs(s, "document.documentElement.dataset.theme==='terminal'"); waitRatio(s, .45f)
            s.recreate(); waitText(headings[1]); waitRatio(s, .45f)
            compose.onNodeWithText("‹ 书架").performClick(); waitText("书架")
            val shelfTitle = BookTitles.display(row).title
            waitText(shelfTitle)
            compose.onNodeWithText(shelfTitle).performClick(); waitText(headings[1]); waitRatio(s, .45f)
        }
        launch(uri).use { s -> waitText(headings[1]); waitRatio(s, .45f) }
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> entries.forEach { (name, data) -> z.putNextEntry(ZipEntry(name));z.write(data);z.closeEntry() } }
    }.toByteArray()
    private fun epub(): ByteArray {
        val image = ByteArrayOutputStream().also { out -> Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 160, 120));compress(Bitmap.CompressFormat.PNG, 100, out);recycle() } }.toByteArray()
        val chapter = """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter</title><style>body {color:black;background:white} p {font-size:12px}</style></head><body><h1>Safe chapter</h1><img src="image.png"/><script>document.body.textContent='SCRIPT RAN'</script><p>${longText("EPUB")}</p><h2 id="target">Target</h2>LINK</body></html>"""
        return zip(mapOf(
            "mimetype" to "application/epub+zip".toByteArray(),
            "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="book.opf"/></rootfiles></container>""".toByteArray(),
            "book.opf" to """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>V2 EPUB Demo</dc:title></metadata><manifest><item id="nav" href="nav.xhtml" properties="nav" media-type="application/xhtml+xml"/><item id="one" href="one.xhtml" media-type="application/xhtml+xml"/><item id="two" href="two.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="one"/><itemref idref="two"/></spine></package>""".toByteArray(),
            "nav.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><a href="one.xhtml">第一节 出发</a><a href="two.xhtml">第二节 抵达</a></nav></body></html>""".toByteArray(),
            "one.xhtml" to chapter.replace("LINK", """<a href="two.xhtml#target" style="position:fixed;inset:30%;z-index:999">Next chapter</a>""").toByteArray(),
            "two.xhtml" to chapter.replace("LINK", "").toByteArray(),
            "image.png" to image,
        ))
    }
    private fun response(s: ActivityScenario<MainActivity>, path: String): Pair<Map<String,String>, ByteArray> {
        var result: Pair<Map<String,String>, ByteArray>? = null
        s.onActivity { a -> val w=web(a.window.decorView)!!
            val req=object:WebResourceRequest {
                override fun getUrl()=Uri.parse(Sandbox.bookUrl(path).replace(Sandbox.HOST, Uri.parse(w.url).host!!))
                override fun isForMainFrame()=true
                override fun isRedirect()=false
                override fun hasGesture()=false
                override fun getMethod()="GET"
                override fun getRequestHeaders()=emptyMap<String,String>()
            }
            val res=w.webViewClient.shouldInterceptRequest(w,req)!!
            result=res.responseHeaders to (res.data?.use { it.readBytes() } ?: byteArrayOf())
        }
        return result!!
    }

    @Test fun epubChaptersDisableScriptsServeImagesAndHandleInternalLinks() {
        val uri=document("v2-book.epub",epub(),"application/epub+zip")
        launch(uri).use { s ->
            waitText("第一节 出发")
            compose.waitUntil(20_000) { var ready=false;s.onActivity { a->ready=web(a.window.decorView)?.let{it.progress==100 && it.contentHeight>1000}==true };ready }
            s.onActivity { a -> assertFalse(web(a.window.decorView)!!.settings.javaScriptEnabled) }
            val chapter=response(s,"one.xhtml")
            assertTrue(chapter.first["Content-Security-Policy"]!!.contains("script-src 'none'"))
            assertTrue(chapter.second.toString(Charsets.UTF_8).contains(EpubInjector.LINKS))
            val image=response(s,"image.png").second
            assertTrue(image.size>20 && image[0]==0x89.toByte())
            // Pixels prove the image reached the rendered chapter; executing its script would
            // replace the body (and remove this image). No JS is enabled to inspect the document.
            compose.waitUntil(20_000) {
                val shot = instrumentation.uiAutomation.takeScreenshot()
                if (shot == null) false else try {
                    var green = 0
                    for (y in 0 until shot.height step 8) for (x in 0 until shot.width step 8) {
                        if (shot.getPixel(x, y) == Color.rgb(30, 160, 120)) green++
                    }
                    green > 20
                } finally { shot.recycle() }
            }
            onView(isAssignableFrom(WebView::class.java)).perform(click())
            waitText("第二节 抵达")
            compose.waitUntil(20_000) { var url="";s.onActivity { a->url=web(a.window.decorView)?.url.orEmpty() };url.endsWith("two.xhtml#target") }
            scroll(s,.4f)
            for ((id, label) in listOf("paper" to "纸张（浅）", "ink" to "墨色（深）", "terminal" to "终端", "auto" to "跟随系统")) {
                select(label); waitRatio(s,.4f)
                assertTrue(response(s,"two.xhtml").second.toString(Charsets.UTF_8).contains("data-theme=\"$id\""))
                s.onActivity { a -> assertFalse(web(a.window.decorView)!!.settings.javaScriptEnabled) }
            }
            var before: WebView? = null
            var previousHeight = 0
            s.onActivity { a -> before=web(a.window.decorView);previousHeight=before!!.contentHeight
                ViewModelProvider(a)[ShelfViewModel::class.java].updateReading(ReadingPrefs(fontScale=150, continuous=false)) }
            compose.waitUntil(20_000) {
                var reflowed=false
                s.onActivity { a -> val w=web(a.window.decorView)
                    reflowed=w != null && w !== before && w.progress==100 && w.contentHeight>previousHeight*1.2f
                }
                reflowed
            }
            waitRatio(s,.4f)
            assertTrue(response(s,"two.xhtml").second.toString(Charsets.UTF_8).contains("--read-scale:1.50"))
            s.onActivity { a -> assertFalse(web(a.window.decorView)!!.settings.javaScriptEnabled) }
            compose.onNodeWithText("‹ 书架").performClick();waitText("书架")
            waitText("V2 EPUB Demo")
            compose.onNodeWithText("V2 EPUB Demo").assertIsDisplayed()
        }
    }

    @Test fun startupCleanupAndBackgroundSaveKeepBooksIndependent() {
        val orphan=File(context.filesDir,"shelf/v2-abandoned.tmp").apply { parentFile!!.mkdirs();writeText("orphan") }
        val first=document("v2-first.md","# First\n\n"+longText("First"),"text/markdown")
        val second=document("v2-second.md","# Second\n\n"+longText("Second"),"text/markdown")
        launch(first).use { s ->
            waitText("v2-first.md");waitJs(s,"!!document.querySelector('#content h1')")
            assertFalse(orphan.exists())
            val firstId=runBlocking { dao.findBySource(first.toString())!!.id }
            var staleScroll: ((Float) -> Unit)? = null
            s.onActivity { a -> val vm = ViewModelProvider(a)[ShelfViewModel::class.java]; val session = vm.documentSession; staleScroll = { vm.onScroll(session, 0, it) } }
            s.onActivity { it.startActivity(intent(second)) }
            waitText("v2-second.md");waitJs(s,"document.querySelector('#content h1')?.textContent==='Second'")
            val secondId=runBlocking { dao.findBySource(second.toString())!!.id }
            s.onActivity { staleScroll!!(.9f); ViewModelProvider(it)[ShelfViewModel::class.java].flushPosition() }
            compose.waitUntil(10_000) { runBlocking { dao.get(secondId)!!.locator!=null } }
            assertEquals("A stale first-book callback must not move the second book", 0f,
                runBlocking { Locator.decode(dao.get(secondId)!!.locator).ratio }, .01f)
            scroll(s,.65f)
            s.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            compose.waitUntil(10_000) { runBlocking { abs(Locator.decode(dao.get(secondId)!!.locator).ratio-.65f)<.025f } }
            assertNotEquals(firstId,secondId)
        }
        launch(second).use { s -> waitText("v2-second.md");waitRatio(s,.65f) }
    }
}
