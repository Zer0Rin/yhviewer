package com.example.yhviewer

import android.net.Uri
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DocumentPermissionsIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var generation = 0
    private fun find(v: View): WebView? {
        if (v is WebView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
        return null
    }
    private fun js(expression: String): String {
        val latch = CountDownLatch(1)
        var result = "null"
        compose.runOnUiThread { find(compose.activity.window.decorView)?.evaluateJavascript(expression) { result=it; latch.countDown() } ?: latch.countDown() }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        return result
    }
    private fun load(p: DocPermissions, id: Long? = null) {
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
    @Test fun persistentDocumentsAreIsolatedAndRevocationCannotResurrectLiveProfile() {
        assumeTrue(DocProfiles.isSupported())
        val p=DocPermissions.STANDARD.copy(persistentStorage=true)
        val a=9_900_001L; val b=9_900_002L
        try {
            load(p,a); assertEquals("\"A\"",js("localStorage.setItem('secret','A');localStorage.getItem('secret')"))
            load(p,b); assertEquals("null",js("localStorage.getItem('secret')"))
            load(p,a); assertEquals("\"A\"",js("localStorage.getItem('secret')"))
            // Revoke while the old WebView still exists, then immediately regrant.
            compose.runOnUiThread { DocProfiles.revoke(compose.activity,a) }
            load(p,a); assertEquals("null",js("localStorage.getItem('secret')"))
            compose.runOnUiThread { assertFalse(find(compose.activity.window.decorView)!!.settings.allowFileAccess); assertFalse(find(compose.activity.window.decorView)!!.settings.allowContentAccess) }
        } finally { compose.runOnUiThread { DocProfiles.revoke(compose.activity,a);DocProfiles.revoke(compose.activity,b) } }
    }
    @Test fun ephemeralStorageIsNewOnEveryOpen() {
        load(DocPermissions.STANDARD)
        assertEquals("\"ephemeral\"", js("localStorage.setItem('value','ephemeral');localStorage.getItem('value')"))
        load(DocPermissions.STANDARD)
        assertEquals("null", js("localStorage.getItem('value')"))
    }
    @Test fun scriptsAndDeviceBuildCapabilitiesAreActuallyApplied() {
        load(DocPermissions.STRICT)
        compose.runOnUiThread {
            val w=find(compose.activity.window.decorView)!!
            assertFalse(w.settings.javaScriptEnabled)
            assertFalse(w.settings.allowFileAccess)
            assertFalse(w.settings.allowContentAccess)
        }
        assertEquals(BuildConfig.NETWORK_CAPABLE, compose.activity.checkSelfPermission(android.Manifest.permission.INTERNET)==android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    private fun request(url: String) = object : WebResourceRequest {
        override fun getUrl()=Uri.parse(url)
        override fun isForMainFrame()=false
        override fun isRedirect()=false
        override fun hasGesture()=false
        override fun getMethod()="GET"
        override fun getRequestHeaders()=emptyMap<String,String>()
    }
    @Test fun interceptorHonorsCdnHostAndAlwaysBlocksPrivateAndFileSchemes() {
        load(DocPermissions.TRUSTED)
        compose.runOnUiThread {
            val w=find(compose.activity.window.decorView)!!
            val allowed=w.webViewClient.shouldInterceptRequest(w,request("https://cdn.jsdelivr.net/npm/library.js"))
            if (BuildConfig.NETWORK_CAPABLE) assertNull(allowed) else assertEquals(403,allowed!!.statusCode)
            for (url in listOf("https://${Sandbox.HOST}:8443/doc/index.html", "https://other.${Sandbox.HOST}/doc/index.html", "file:///sdcard/private.txt", "content://private/secret")) {
                assertEquals(url,403,w.webViewClient.shouldInterceptRequest(w,request(url))!!.statusCode)
            }
        }
    }
    /** Deterministic fake HTTPS responses are supplied only after the production interceptor allows
     * the request. The real Chromium CSP engine still decides whether requests reach this hook. */
    private fun fakeNetwork() {
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
    @Test fun framesAreExplicitlyOutsideParentConnectPolicy() {
        assumeTrue(BuildConfig.NETWORK_CAPABLE)
        load(DocPermissions.STANDARD.copy(frames=true))
        fakeNetwork()
        js("window.childResult='waiting';addEventListener('message',e=>window.childResult=e.data);var f=document.createElement('iframe');f.src='https://fixture.example/child';document.body.appendChild(f)")
        compose.waitUntil(15_000) { js("window.childResult") == "\"child-network-ok\"" }
        js("window.parentBlocked=false;addEventListener('securitypolicyviolation',e=>{if(e.effectiveDirective==='connect-src')window.parentBlocked=true});fetch('https://fixture.example/data').catch(()=>{})")
        compose.waitUntil(10_000) { js("window.parentBlocked") == "true" }
    }
    @Test fun onlyExplicitNetworkGrantAllowsParentFetch() {
        assumeTrue(BuildConfig.NETWORK_CAPABLE)
        load(DocPermissions.TRUSTED)
        fakeNetwork()
        js("window.blocked=false;addEventListener('securitypolicyviolation',e=>{if(e.effectiveDirective==='connect-src')window.blocked=true});fetch('https://fixture.example/data').catch(()=>{})")
        compose.waitUntil(10_000) { js("window.blocked") == "true" }
        load(DocPermissions.TRUSTED.copy(network=true))
        fakeNetwork()
        js("window.result='waiting';fetch('https://fixture.example/data').then(r=>r.text()).then(t=>window.result=t)")
        compose.waitUntil(10_000) { js("window.result") == "\"ok\"" }
    }
}
