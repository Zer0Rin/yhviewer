package com.example.yhviewer

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorHandleIntegrationTest : PermissionWebViewFixture() {
    private val handle = WebViewHandle()
    private fun editor(text: String, missing: Boolean = false) {
        val template = Templates.get(compose.activity, EditorRenderer.TEMPLATE).let {
            if (missing) it.replace("cm6.bundle.js", "missing-editor.js") else it
        }
        val html = EditorRenderer.render(text,"javascript",false,template,ReaderTheme.PAPER)
        compose.runOnUiThread { compose.activity.setContent { key(text,missing) {
            HtmlView(PageSource.Html(html),Modifier.fillMaxSize(),handle=handle)
        } } }
        compose.waitUntil(15_000) { var ready=false;compose.runOnUiThread { ready=handle.webView?.progress==100 };ready }
    }
    @Test fun realEditorRoundTripSnapshotBaselineAndDisposal() {
        val original="中文 😀 </script>\nconsole.log('x')\n"
        editor(original)
        compose.waitUntil(10_000) { js("!!window.editorApi")=="true" }
        assertEquals(original,runBlocking { handle.editorText() })
        assertFalse(runBlocking { handle.editorDirty() })
        js("window.editorApi.insert('A',0)")
        val saved=runBlocking { handle.editorText() }!!
        js("window.editorApi.insert('B',0)")
        compose.runOnUiThread { handle.markSaved(saved) }
        assertTrue(runBlocking { handle.editorDirty() })
        val latest=runBlocking { handle.editorText() }!!
        compose.runOnUiThread { handle.markSaved(latest) }
        assertFalse(runBlocking { handle.editorDirty() })
        assertEquals("undefined",js("typeof Android").trim('"'))
        compose.runOnUiThread { compose.activity.setContent { Text("Editor disposed") } }
        compose.waitUntil(5_000) { handle.webView==null }
        assertNull(runBlocking { handle.editorText() })
    }
    @Test fun missingBundleReturnsNullRatherThanEmptyContent() {
        editor("must never overwrite this",missing=true)
        assertEquals("null",js("window.editorApi"))
        assertTrue(js("document.body.textContent").contains("编辑器组件未加载"))
        assertNull(runBlocking { handle.editorText() })
    }
}
