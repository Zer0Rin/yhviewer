package com.example.yhviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandardNetworkPermissionsIntegrationTest : PermissionWebViewFixture() {
    @Test fun framesAreExplicitlyOutsideParentConnectPolicy() {
        assertTrue(BuildConfig.NETWORK_CAPABLE)
        load(DocPermissions.STANDARD.copy(frames=true))
        if (!AppCapabilities.persistentStorageSupported) {
            // Old engines cannot isolate third-party iframe origins across documents.
            assertFalse(AppCapabilities.effective(DocPermissions.STANDARD.copy(frames=true)).frames)
            js("window.frameBlocked=false;addEventListener('securitypolicyviolation',e=>{if(e.effectiveDirective==='frame-src')window.frameBlocked=true});var f=document.createElement('iframe');f.src='https://fixture.example/child';document.body.appendChild(f)")
            compose.waitUntil(10_000) { js("window.frameBlocked") == "true" }
            return
        }
        fakeNetwork()
        js("window.childResult='waiting';addEventListener('message',e=>window.childResult=e.data);var f=document.createElement('iframe');f.src='https://fixture.example/child';document.body.appendChild(f)")
        compose.waitUntil(15_000) { js("window.childResult") == "\"child-network-ok\"" }
        js("window.parentBlocked=false;addEventListener('securitypolicyviolation',e=>{if(e.effectiveDirective==='connect-src')window.parentBlocked=true});fetch('https://fixture.example/data').catch(()=>{})")
        compose.waitUntil(10_000) { js("window.parentBlocked") == "true" }
    }
    @Test fun onlyExplicitNetworkGrantAllowsParentFetch() {
        assertTrue(BuildConfig.NETWORK_CAPABLE)
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
