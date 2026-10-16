package com.example.yhviewer

import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Native fixture for disposable emulator tests; waits for app cleanup before creating test Profiles. */
abstract class PermissionWebViewFixture {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var generation = 0
    protected fun find(v: View): WebView? {
        if (v is WebView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
        return null
    }
    protected fun js(expression: String): String {
        val latch = CountDownLatch(1)
        var result = "null"
        compose.runOnUiThread { find(compose.activity.window.decorView)?.evaluateJavascript(expression) { result=it; latch.countDown() } ?: latch.countDown() }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        return result
    }
    protected fun load(p: DocPermissions, id: Long? = null) {
        val g=++generation
        var previous: WebView? = null
        compose.runOnUiThread { previous=find(compose.activity.window.decorView) }
        compose.runOnUiThread { compose.activity.setContent { key(g) { HtmlView(PageSource.Html("<h1>Permission fixture</h1>"), Modifier.fillMaxSize(), permissions=p, storageProfileId=id) } } }
        compose.waitUntil(20_000) {
            var ready=false
            compose.runOnUiThread { val w=find(compose.activity.window.decorView); ready=w != null && w !== previous && w.progress==100 && w.url?.startsWith("https://")==true }
            ready
        }
    }
    /** Deterministic fake HTTPS responses are supplied only after the production interceptor allows
     * the request. The real Chromium CSP engine still decides whether requests reach this hook. */
    protected fun fakeNetwork() {
        compose.runOnUiThread {
            val w=find(compose.activity.window.decorView)!!
            val delegate=w.webViewClient
            w.webViewClient=object: WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                    delegate.shouldInterceptRequest(view,req)?.let { return it }
                    val body=if(req.url.path=="/child") "<script>fetch('https://fixture.example/data').then(()=>parent.postMessage('child-network-ok','*'))</script>" else "ok"
                    return WebResourceResponse(if(req.url.path=="/child") "text/html" else "text/plain","utf-8",200,"OK",mapOf("Access-Control-Allow-Origin" to "*"),ByteArrayInputStream(body.toByteArray()))
                }
                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest)=delegate.shouldOverrideUrlLoading(view,req)
                override fun onPageFinished(view: WebView,url: String?)=delegate.onPageFinished(view,url)
            }
        }
    }
    @org.junit.Before fun awaitStartup() {
        compose.runOnUiThread { androidx.lifecycle.ViewModelProvider(compose.activity)[ShelfViewModel::class.java].openBook(Long.MAX_VALUE) }
        compose.waitUntil(20_000) {
            var done=false
            compose.runOnUiThread { done=androidx.lifecycle.ViewModelProvider(compose.activity)[ShelfViewModel::class.java].reader is ReaderState.Error }
            done
        }
        compose.runOnUiThread { androidx.lifecycle.ViewModelProvider(compose.activity)[ShelfViewModel::class.java].backToShelf() }
    }
}
