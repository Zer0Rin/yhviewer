package com.example.yhviewer

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import android.webkit.WebView
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Manual screenshot fixture. Copy into androidTest temporarily, then remove after capture. */
class ShowcaseCaptureTest : PermissionWebViewFixture() {
    private val owned = mutableListOf<Uri>()
    private val vm get() = ViewModelProvider(compose.activity)[ShelfViewModel::class.java]
    private fun open(name: String, content: String) {
        compose.runOnUiThread { vm.backToShelf(); vm.selectTheme(ReaderTheme.TERMINAL) }
        val r = compose.activity.contentResolver
        val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/YHViewerShowcase-${System.nanoTime()}")
        })!!
        owned += uri
        r.openOutputStream(uri)!!.use { it.write(content.toByteArray()) }
        compose.runOnUiThread { vm.importAndOpen(uri) }
        compose.waitUntil(20_000) { var ready=false; compose.runOnUiThread { ready=vm.reader is ReaderState.Ready }; ready }
    }
    private fun capture(name: String, targetView: WebView? = null) {
        compose.waitForIdle()
        val drawn=CountDownLatch(1)
        compose.runOnUiThread {
            val web=targetView ?: find(compose.activity.window.decorView)!!
            assertTrue(web.isAttachedToWindow)
            web.postVisualStateCallback(1, object: WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    web.invalidate()
                    web.postOnAnimation { web.postOnAnimation { drawn.countDown() } }
                }
            })
        }
        assertTrue("Visible WebView frame was not committed", drawn.await(15,TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(image)
        val target=File(compose.activity.getExternalFilesDir("showcase"),name)
        target.outputStream().use { assertTrue(image!!.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) }
        image!!.recycle()
    }
    @After fun cleanup() {
        compose.runOnUiThread { vm.backToShelf() }
        val app=compose.activity.applicationContext
        runBlocking { val db=AppDatabase.get(app); val repo=ShelfRepository(app,db)
            owned.forEach { uri -> db.bookDao().findBySource(uri.toString())?.let { repo.delete(it.id) }; app.contentResolver.delete(uri,null,null) }
        }
    }
    @Test fun captureSyntheticEditorRunnerAndPermissions() {
        open("reading-notes.js", """
            // 阅读笔记 · 离线编辑
            const notes = [
              { title: "安全沙箱", done: true },
              { title: "代码编辑", done: true },
              { title: "Python 运行", done: true },
            ];

            function summarize(items) {
              return items
                .filter(item => item.done)
                .map(item => item.title);
            }

            console.log("本周完成：");
            console.log(summarize(notes));
        """.trimIndent())
        compose.onNodeWithText("编辑").performClick()
        compose.waitUntil(15_000) { js("!!window.editorApi")=="true" }
        capture("editor.png")
        open("offline-demo.py", """
            import math, json
            print("Python · 离线运行")
            print("标准库，无需联网")
            print("平方根:", math.isqrt(2025))
            print(json.dumps({"status": "ready", "items": 3}))
            print("你好，世界！ 🌏")
        """.trimIndent())
        compose.onNodeWithText("▶ 运行").performClick()
        var runView: WebView?=null
        onView(isAssignableFrom(WebView::class.java)).inRoot(isDialog()).check { v,_ -> runView=v as WebView }
        fun done(): Boolean { val latch=CountDownLatch(1); var value="false"
            compose.runOnUiThread { runView!!.evaluateJavascript("window.__runResult && window.__runResult.state==='done'") { value=it;latch.countDown() } }
            assertTrue(latch.await(5,TimeUnit.SECONDS));return value=="true"
        }
        compose.waitUntil(70_000) { done() }
        capture("run.png",runView)
        compose.onNodeWithText("关闭").performClick()
        open("interactive-report.html", "<h1>Interactive report</h1><button onclick=\"this.textContent='Ready'\">Check</button>")
        compose.onNodeWithText("权限").performClick()
        compose.onNodeWithText("运行脚本").assertIsDisplayed()
        capture("permissions-standard.png")
    }
}
