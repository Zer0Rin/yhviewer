package com.example.yhviewer

import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ReaderIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun document(name: String, mime: String, bytes: ByteArray): Uri {
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/YHViewerTests")
            })!!
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        return uri
    }

    private fun viewIntent(uri: Uri, mime: String) = Intent(Intent.ACTION_VIEW)
        .setClass(context, MainActivity::class.java)
        .setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun js(scenario: ActivityScenario<MainActivity>, expression: String): String {
        val latch = CountDownLatch(1)
        var result = "null"
        scenario.onActivity { activity ->
            val web = findWebView(activity.window.decorView)
            if (web == null) latch.countDown()
            else web.evaluateJavascript(expression) { value -> result = value; latch.countDown() }
        }
        assertTrue("JavaScript callback timed out", latch.await(5, TimeUnit.SECONDS))
        return result
    }

    private fun awaitTitle(name: String) {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun viewMarkdownAndShareReuseActivityWithoutExecutingRawHtml() {
        val html = document("hello.html", "text/html", context.assets.open("hello.html").use { it.readBytes() })
        val markdown = document("sample.md", "text/markdown", instrumentation.context.assets.open("sample.md").use { it.readBytes() })
        try {
            ActivityScenario.launch<MainActivity>(viewIntent(html, "text/html")).use { scenario ->
                awaitTitle("hello.html")
                compose.waitUntil(20_000) { js(scenario, "document.getElementById('js')?.textContent") == "\"JS 正常\"" }
                assertEquals("\"3\"", js(scenario, "document.getElementById('btn').click();document.getElementById('btn').click();document.getElementById('btn').click();document.getElementById('count').textContent"))
                var original: MainActivity? = null
                scenario.onActivity { original = it; it.startActivity(viewIntent(markdown, "text/plain")) }
                awaitTitle("sample.md")
                scenario.onActivity { assertSame("singleTop must reuse Activity", original, it) }
                compose.waitUntil(20_000) { js(scenario, "!!document.querySelector('#content h1')") == "true" }
                assertEquals("0", js(scenario, "document.querySelectorAll('#content script').length"))
                assertEquals("true", js(scenario, "document.querySelector('#content').textContent.includes(\"<script>alert('\") && document.querySelector('#content strong').textContent==='Markdown' && document.querySelector('#content h1').textContent==='第 2 步测试文档'"))
                // Rendered Markdown must contain no script elements from the source document.
                scenario.onActivity { findWebView(it.window.decorView)!!.webChromeClient = WebChromeClient() }
                scenario.onActivity { activity ->
                    activity.startActivity(Intent(Intent.ACTION_SEND).setClass(context, MainActivity::class.java)
                        .setType("text/html").putExtra(Intent.EXTRA_STREAM, html)
                        .apply { clipData = ClipData.newRawUri("hello.html", html) }
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
                awaitTitle("hello.html")
                scenario.onActivity { assertSame(original, it) }
                compose.waitUntil(20_000) { js(scenario, "document.getElementById('js')?.textContent") == "\"JS 正常\"" }
                assertEquals("\"0\"", js(scenario, "document.getElementById('count').textContent"))
            }
        } finally {
            context.contentResolver.delete(html, null, null)
            context.contentResolver.delete(markdown, null, null)
        }
    }

    @Test fun backNavigatesWebHistoryBeforeLeavingActivity() {
        val uri = document("history.html", "text/html", "<h1 id='section'>History</h1><a href='#section' style='position:fixed;inset:0'>Navigate</a>".toByteArray())
        try {
            ActivityScenario.launch<MainActivity>(viewIntent(uri, "text/html")).use { scenario ->
                awaitTitle("history.html")
                compose.waitUntil(20_000) { js(scenario, "!!document.querySelector('h1')") == "true" }
                androidx.test.espresso.Espresso.onView(
                    androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(WebView::class.java)
                ).perform(androidx.test.espresso.action.ViewActions.click())
                compose.waitUntil(20_000) {
                    var back = false
                    scenario.onActivity { back = findWebView(it.window.decorView)!!.canGoBack() }
                    back
                }
                compose.waitForIdle()
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitUntil(20_000) { js(scenario, "location.hash") == "\"\"" }
                scenario.onActivity { assertFalse(it.isFinishing) }
            }
        } finally { context.contentResolver.delete(uri, null, null) }
    }

    @Test fun loaderRejectsFilesOverTwentyMiB() {
        val uri = document("oversize.md", "text/markdown", ByteArray(20 * 1024 * 1024 + 1) { 65 })
        try {
            assertEquals(DocType.MARKDOWN, runBlocking { DocumentLoader.probe(context, uri).type })
            val failure = runCatching { runBlocking { DocumentLoader.load(context, uri) } }.exceptionOrNull()
            assertEquals("文件超过 20MB", failure?.message)
        } finally { context.contentResolver.delete(uri, null, null) }
    }
}
