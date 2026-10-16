package com.example.yhviewer

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Standard-only tests execute bundled CPython/WASM, never a runtime stub. */
@RunWith(AndroidJUnit4::class)
class PythonRuntimeIntegrationTest : PermissionWebViewFixture() {
    private var generation=0
    private fun run(code:String,timeout:Int=5_000):JSONObject {
        assertTrue(AppCapabilities.pythonAvailable)
        val html=CodeRunner.render(code,Templates.get(compose.activity,CodeRunner.TEMPLATE),ReaderTheme.PAPER,timeout,CodeRunner.Lang.PYTHON)
        val id=++generation
        var previous: android.webkit.WebView? = null
        compose.runOnUiThread { previous=find(compose.activity.window.decorView) }
        compose.runOnUiThread { compose.activity.setContent { key(id) {
            HtmlView(PageSource.Html(html,CodeRunner.cspFor(CodeRunner.Lang.PYTHON)),Modifier.fillMaxSize())
        } } }
        compose.waitUntil(15_000) {
            var ready=false
            compose.runOnUiThread { val view=find(compose.activity.window.decorView);ready=view!=null && view!==previous && view.progress==100 }
            ready
        }
        compose.waitUntil(70_000) { js("window.__runResult && window.__runResult.state !== 'running'")=="true" }
        return JSONObject(js("window.__runResult"))
    }
    private fun output()=JSONTokener(js("document.getElementById('out').textContent")).nextValue() as String
    @Test fun realStdlibUnicodeAndAwaitExecuteFromBundledRuntime() {
        val result=run("import sys,json,re,math,datetime,collections,asyncio\nprint(sys.version)\nprint('你好 😀', math.isqrt(81), json.loads('{\"a\":2}')[\"a\"])\nawait asyncio.sleep(0.05)\nprint('await-native-ok')")
        assertEquals(output(),"done",result.getString("state"))
        assertTrue(output(),output().contains("3.12.1"))
        assertTrue(output(),output().contains("你好 😀 9 2"))
        assertTrue(output(),output().contains("await-native-ok"))
    }
    @Test fun tracebackShowsUserLineAndInputExplainsEof() {
        assertEquals("error",run("# one\n# two\nraise ValueError('native-boom')").getString("state"))
        assertTrue(output(),output().contains("第 3 行"))
        assertFalse(output(),output().contains("_pyodide"))
        assertFalse(output(),output().contains("site-packages"))
        assertEquals("error",run("input()").getString("state"))
        assertTrue(output(),output().contains("EOFError"))
        assertTrue(output(),output().contains("没有键盘输入"))
    }
    @Test fun infinitePythonTimesOutAfterLoadingWithoutBlockingNativeUi() {
        val r=run("print('before-native-loop', end='', flush=True)\nwhile True: pass",500)
        assertEquals(output(),"timeout",r.getString("state"))
        assertTrue(output(),output().contains("before-native-loop"))
        assertEquals("2",js("1+1"))
    }
    @Test fun realPythonInteropCannotFetchExternalHostsOrNativeFiles() {
        val r=run("""
            import js
            for url in ['https://example.com/native-python', 'file:///data/local/tmp/v6-secret', 'content://settings/system']:
                try:
                    await js.fetch(url)
                    print('ALLOWED')
                except Exception:
                    print('blocked', url.split(':')[0])
        """.trimIndent())
        assertEquals(output(),"done",r.getString("state"))
        assertFalse(output(),output().contains("ALLOWED"))
        for (s in listOf("blocked https","blocked file","blocked content")) assertTrue(output(),output().contains(s))
    }
    @Test fun pythonOutputAndForgedReadyMessagesCannotBypassBudgets() {
        val r=run("for i in range(5000): print(i)")
        assertEquals(output(),"limit",r.getString("state"))
        assertTrue(r.getInt("lines")<=1000)
        val spoof=run("import js\nwhile True: js.postMessage(js.JSON.parse('{\"t\":\"ready\"}'))",500)
        assertTrue(output(),spoof.getString("state") in listOf("timeout","limit"))
        assertTrue(spoof.getInt("messages")<=4000)
    }
    @Test fun partialUnicodeOutputSurvivesCompletionAndError() {
        assertEquals("done",run("print('部分 中文 😀', end='')").getString("state"))
        assertTrue(output(),output().contains("部分 中文 😀"))
        assertEquals("error",run("print('错误前 中文 😀', end='')\nraise ValueError('partial-boom')").getString("state"))
        assertTrue(output(),output().contains("错误前 中文 😀"))
        assertTrue(output(),output().contains("partial-boom"))
    }
    @Test fun outputWithoutNewlinesStillHasBoundedBuffers() {
        val r=run("print('x' * 300000, end='')")
        assertEquals(output().take(200),"limit",r.getString("state"))
        assertTrue(r.getInt("chars")<=200000)
        assertTrue(r.getInt("lines")<=1000)
    }

}
