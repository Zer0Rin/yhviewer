package com.example.yhviewer

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Platform capability gate; actual app flow behavior has separate acceptance coverage. */
@RunWith(AndroidJUnit4::class)
class V11FindCompatibilityTest {
    @Test fun nativeFindTraversesScriptDisabledSameOriginIframe() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val page = CountDownLatch(1)
        lateinit var view: WebView
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                view = WebView(activity)
                activity.setContentView(view)
                view.settings.javaScriptEnabled = true
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(v: WebView, url: String?) { page.countDown() }
                }
                view.loadDataWithBaseURL("https://iframe-probe.invalid/flow/index.html", """
                    <!doctype html><html><body><h1>Trusted outer shell</h1>
                    <iframe sandbox="allow-same-origin" style="height:600px;width:100%" srcdoc="&lt;h1&gt;Chapter&lt;/h1&gt;&lt;p&gt;needle&lt;/p&gt;&lt;p&gt;needle&lt;/p&gt;&lt;p&gt;needle&lt;/p&gt;&lt;script&gt;parent.document.title='escaped'&lt;/script&gt;"></iframe>
                    </body></html>
                """.trimIndent(), "text/html", "UTF-8", null)
            }
            assertTrue("Outer page ready", page.await(15, TimeUnit.SECONDS))
            instrument.waitForIdleSync()
            val matches = CountDownLatch(1)
            var count = -1
            scenario.onActivity {
                view.setFindListener { _, total, complete -> if (complete) { count = total; matches.countDown() } }
                view.findAllAsync("needle")
            }
            assertTrue("Native find completed", matches.await(15, TimeUnit.SECONDS))
            assertEquals("Native WebView find must traverse iframe book content", 3, count)
            val evaluated = CountDownLatch(1)
            var title = ""
            scenario.onActivity { view.evaluateJavascript("document.title") { title = it; evaluated.countDown() } }
            assertTrue(evaluated.await(10, TimeUnit.SECONDS))
            assertNotEquals("Book script must not change outer title", "\"escaped\"", title)
            scenario.onActivity { view.destroy() }
        }
    }
}
