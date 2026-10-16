package com.example.yhviewer

import android.content.ContentValues
import android.net.Uri
import android.os.Process
import android.provider.MediaStore
import android.webkit.WebResourceRequest
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RendererRecoveryIntegrationTest : PermissionWebViewFixture() {
    @Test fun rendererCrashShowsErrorAndNewOpenUsesFreshWebView() {
        val context = compose.activity.applicationContext
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "v5-renderer-${System.nanoTime()}.html")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/html")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/YHViewerV5Tests")
        })!!
        context.contentResolver.openOutputStream(uri)!!.use { it.write("<h1>Fresh recovery fixture</h1>".toByteArray()) }
        val vm = ViewModelProvider(compose.activity)[ShelfViewModel::class.java]
        var id: Long? = null
        try {
            compose.runOnUiThread { vm.importAndOpen(uri) }
            compose.waitUntil(20_000) { var ready=false;compose.runOnUiThread { ready=vm.reader is ReaderState.Ready && find(compose.activity.window.decorView)?.progress==100 };ready }
            compose.runOnUiThread { id=(vm.reader as ReaderState.Ready).doc.bookId }
            val crashedSession = vm.documentSession
            val pid = Process.myPid()
            compose.runOnUiThread { find(compose.activity.window.decorView)!!.loadUrl("chrome://crash") }
            compose.waitUntil(20_000) { var error=false;compose.runOnUiThread { error=vm.reader is ReaderState.Error };error }
            assertEquals(pid, Process.myPid())
            compose.onNode(hasText("渲染进程已终止", substring=true)).assertIsDisplayed()
            compose.runOnUiThread { assertNull(find(compose.activity.window.decorView));vm.backToShelf();vm.openBook(id!!) }
            compose.waitUntil(20_000) { var ready=false;compose.runOnUiThread { ready=vm.reader is ReaderState.Ready && find(compose.activity.window.decorView)?.progress==100 };ready }
            compose.runOnUiThread { vm.onRendererGone(crashedSession,0);assertTrue(vm.reader is ReaderState.Ready) }
        } finally {
            compose.runOnUiThread { vm.backToShelf() }
            id?.let { runBlocking { ShelfRepository(context,AppDatabase.get(context)).delete(it) } }
            context.contentResolver.delete(uri,null,null)
        }
    }

    @Test fun epubAlwaysUsesEphemeralStrictPolicyAndDecodesUtf16() {
        val context=compose.activity
        val root=File(context.filesDir,"v5-epub-${System.nanoTime()}").apply { mkdirs() }
        val id=9_980_001L
        val text="""<?xml version="1.0" encoding="UTF-16"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>测试</title></head><body><h1>繁體中文章节</h1></body></html>"""
        File(root,"one.xhtml").writeBytes(text.toByteArray(Charsets.UTF_16))
        try {
            compose.runOnUiThread { context.setContent { HtmlView(PageSource.BookFile(root,"one.xhtml"),Modifier.fillMaxSize(),permissions=DocPermissions.TRUSTED,storageProfileId=id) } }
            compose.waitUntil(20_000) { var ready=false;compose.runOnUiThread { ready=find(context.window.decorView)?.progress==100 };ready }
            compose.runOnUiThread {
                val w=find(context.window.decorView)!!
                assertFalse(w.settings.javaScriptEnabled)
                assertFalse(w.settings.domStorageEnabled)
                assertFalse("EPUB must not register persistent data even if a caller supplies grants",context.getSharedPreferences("document_profiles",0).contains(id.toString()))
                val req=object:WebResourceRequest {
                    override fun getUrl()=Uri.parse(w.url)
                    override fun isForMainFrame()=true
                    override fun isRedirect()=false
                    override fun hasGesture()=false
                    override fun getMethod()="GET"
                    override fun getRequestHeaders()=emptyMap<String,String>()
                }
                val response=w.webViewClient.shouldInterceptRequest(w,req)!!
                assertTrue(response.responseHeaders["Content-Security-Policy"]!!.contains("script-src 'none'"))
                assertTrue(response.data.use { it.readBytes() }.toString(Charsets.UTF_8).contains("繁體中文章节"))
                assertEquals("utf-8",response.encoding)
            }
        } finally {
            compose.runOnUiThread { context.setContent { androidx.compose.material3.Text("Fixture closed") };DocProfiles.revoke(context,id) }
            root.deleteRecursively()
        }
    }

    @Test fun profileMutationsRejectBackgroundThreadsBeforeChangingStorage() {
        assertNotEquals(android.os.Looper.getMainLooper(),android.os.Looper.myLooper())
        val context=compose.activity
        for (operation in listOf<()->Unit>(
            { DocProfiles.revoke(context,9_980_002L) },
            { DocProfiles.release(context,DocProfiles.Session(null,"unused.${Sandbox.HOST}",true)) },
            { DocProfiles.cleanup(context,emptySet()) },
        )) {
            try { operation();fail("Profile mutation must reject a background thread") }
            catch (expected:IllegalStateException) { assertTrue(expected.message.orEmpty().contains("main",ignoreCase=true)) }
        }
    }
}
