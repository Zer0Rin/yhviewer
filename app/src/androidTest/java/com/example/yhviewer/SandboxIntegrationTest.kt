package com.example.yhviewer

import android.Manifest
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.yhviewer.ui.theme.YHViewerTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

@RunWith(AndroidJUnit4::class)
class SandboxIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var generation = 0

    private fun web(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) web(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun load(html: String) {
        val id = ++generation
        compose.runOnUiThread { compose.activity.setContent { key(id) { YHViewerTheme { HtmlView(PageSource.Html(html), Modifier.fillMaxSize()) } } } }
        compose.waitUntil(20_000) { js("document.readyState") == "\"complete\"" && js("location.href") == "\"${Sandbox.DOC_URL}\"" }
    }

    private fun js(expression: String): String {
        val done = CountDownLatch(1)
        var result = "null"
        compose.runOnUiThread {
            val view = web(compose.activity.window.decorView)
            if (view == null) done.countDown()
            else view.evaluateJavascript(expression) { result = it; done.countDown() }
        }
        assertTrue("JS callback timeout", done.await(5, TimeUnit.SECONDS))
        return result
    }

    @Test fun nineChecksReopenStorageAndAutomaticRedirect() {
        val expectInternet = InstrumentationRegistry.getArguments().getString("expectInternet") == "true"
        assertEquals("The tested APK must match the permission round", expectInternet,
            compose.activity.checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED)
        if (expectInternet) {
            val connection = URL("https://example.com/").openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                assertTrue("Positive control must have real internet access", connection.responseCode in 200..399)
                Log.i("SandboxTest", "INTERNET positive control: HTTPS reachable (${connection.responseCode})")
            } finally { connection.disconnect() }
        }
        val html = instrumentation.context.assets.open("sandbox-test.html").bufferedReader().use { it.readText() }
        repeat(2) { round ->
            load(html)
            compose.waitUntil(25_000) { js("!!window.__sandboxResult") == "true" }
            val result = JSONObject(js("window.__sandboxResult"))
            Log.i("SandboxTest", "internet=$expectInternet reopen=$round result=$result")
            assertEquals(result.toString(), 9, result.getJSONArray("results").length())
            assertEquals(result.toString(), 0, result.getInt("failed"))
            compose.waitUntil(15_000) { js("document.getElementById('redirect').className") == "\"PASS\"" }
            assertEquals("\"${Sandbox.DOC_URL}\"", js("location.href"))
            assertEquals("true", js("localStorage.getItem('sandbox-test-mark') !== null"))
            compose.runOnUiThread {
                val settings = web(compose.activity.window.decorView)!!.settings
                assertFalse(settings.allowFileAccess)
                assertFalse(settings.allowContentAccess)
                assertFalse(settings.javaScriptCanOpenWindowsAutomatically)
            }
            compose.runOnUiThread { compose.activity.setContent { } }
            compose.waitForIdle()
        }
    }

    @Test fun requestInterceptorDeniesExternalAndUnknownResourcesWithoutRelyingOnCsp() {
        load("<h1>Interceptor test</h1>")
        js("window.frameBlocked=false;document.addEventListener('securitypolicyviolation',e=>{if(e.effectiveDirective==='frame-src' && e.blockedURI.startsWith('https://example.com'))window.frameBlocked=true});var f=document.createElement('iframe');f.src='https://example.com/';document.body.appendChild(f)")
        compose.waitUntil(10_000) { js("window.frameBlocked") == "true" }

        for (url in listOf("https://example.com/probe", "file:///sdcard/Download/hello.html",
            "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fhello.html",
            "http://${Sandbox.HOST}/doc/index.html", "https://${Sandbox.HOST}:8443/doc/index.html",
            "https://${Sandbox.HOST}/doc/missing.png", "https://${Sandbox.HOST}.example.com/probe")) {
            var code = 0
            instrumentation.runOnMainSync {
                val view = web(compose.activity.window.decorView)!!
                val request = object : WebResourceRequest {
                    override fun getUrl() = android.net.Uri.parse(url)
                    override fun isForMainFrame() = false
                    override fun isRedirect() = false
                    override fun hasGesture() = false
                    override fun getMethod() = "GET"
                    override fun getRequestHeaders() = emptyMap<String, String>()
                }
                // Exercise the real client directly: CSP can block before WebView calls this hook.
                code = view.webViewClient.shouldInterceptRequest(view, request)!!.statusCode
            }
            assertEquals(url, 403, code)
        }
    }

    @Test fun onlyUserClickOpensExternalBrowser() {
        val monitor = Instrumentation.ActivityMonitor(IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addDataScheme("https")
        }, Instrumentation.ActivityResult(0, null), true)
        instrumentation.addMonitor(monitor)
        try {
            load("""<a href="https://example.com/" style="position:fixed;inset:0">Open browser</a>
                <script>setTimeout(()=>{location.href='https://example.com/?automatic';window.attempted=true},100)</script>""")
            compose.waitUntil(10_000) { js("window.attempted === true") == "true" }
            compose.waitForIdle()
            assertEquals("Automatic navigation must not launch another app", 0, monitor.hits)
            assertEquals("\"${Sandbox.DOC_URL}\"", js("location.href"))
            onView(isAssignableFrom(WebView::class.java)).perform(click())
            compose.waitUntil(10_000) { monitor.hits == 1 }
            assertEquals("\"${Sandbox.DOC_URL}\"", js("location.href"))
        } finally { instrumentation.removeMonitor(monitor) }
    }
}
