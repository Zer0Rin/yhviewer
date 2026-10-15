package com.example.yhviewer

import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentPermissionsIntegrationTest : PermissionWebViewFixture() {
    @Test fun persistentDocumentsAreIsolatedAndRevocationCannotResurrectLiveProfile() {
        val profilesSupported = DocProfiles.isSupported()
        assertEquals(profilesSupported, AppCapabilities.effective(DocPermissions.STANDARD.copy(persistentStorage=true)).persistentStorage)
        val p=DocPermissions.STANDARD.copy(persistentStorage=true)
        val a=9_900_001L; val b=9_900_002L
        try {
            load(p,a); assertEquals("\"A\"",js("localStorage.setItem('secret','A');localStorage.getItem('secret')"))
            load(p,b); assertEquals("null",js("localStorage.getItem('secret')"))
            load(p,a); assertEquals(if (profilesSupported) "\"A\"" else "null",js("localStorage.getItem('secret')"))
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
}
