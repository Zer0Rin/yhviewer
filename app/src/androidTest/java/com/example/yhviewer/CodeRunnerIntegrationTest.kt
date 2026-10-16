package com.example.yhviewer

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CodeRunnerIntegrationTest : PermissionWebViewFixture() {
    private var runId=0
    private val theme=mutableStateOf(ReaderTheme.PAPER)
    private fun run(code:String, timeout:Int=5_000) {
        val html=CodeRunner.render(code,Templates.get(compose.activity,CodeRunner.TEMPLATE),theme.value,timeout)
        val id=++runId
        var previous:android.webkit.WebView?=null
        compose.runOnUiThread { previous=find(compose.activity.window.decorView) }
        compose.runOnUiThread { compose.activity.setContent { key(id) { HtmlView(PageSource.Html(html,csp=CodeRunner.CSP),Modifier.fillMaxSize(),theme=theme.value,permissions=DocPermissions.STANDARD) } } }
        compose.waitUntil(15_000) {
            var ready=false
            compose.runOnUiThread { val view=find(compose.activity.window.decorView);ready=view!=null && view!==previous && view.progress==100 }
            ready
        }
        compose.waitUntil(15_000) { js("!!window.__runResult")=="true" }
    }
    private fun result(expected:String):JSONObject {
        compose.waitUntil(15_000) { js("window.__runResult && window.__runResult.state !== 'running'")=="true" }
        val r=JSONObject(js("window.__runResult"));assertEquals(output(),expected,r.getString("state"));return r
    }
    private fun output()=JSONTokener(js("document.getElementById('out').textContent")).nextValue() as String

    @Test fun consoleAwaitDelayedOutputAndEscapingUsePlainText() {
        run("""
            const circle={};circle.self=circle;
            console.log('hello', {a:1}, [1,2], new Map([['k',2]]), new Set([3]), circle);
            console.info('info');console.warn('warn');console.error('error');console.table({x:1});
            console.time('clock');await new Promise(r=>setTimeout(r,50));console.timeEnd('clock');
            console.assert(false,'assertion');setTimeout(()=>console.log('delayed'),100);
            console.log('</script><script>globalThis.escaped=true</script>');
            console.log('globals',typeof document,typeof window,typeof require);
        """.trimIndent())
        result("done")
        for (text in listOf("hello","Map(1)","Set(1)","Circular","delayed","clock:","assertion","</script>","globals undefined undefined undefined")) assertTrue(text,output().contains(text))
        assertEquals("0",js("document.querySelectorAll('#out script').length"))
        assertEquals("undefined",js("typeof window.escaped").trim('"'))
    }

    @Test fun errorsMapToSourceLinesAndRejectionsFail() {
        run("\n\nlet x = ;");result("error");assertTrue(output(),output().contains("第 3 行"))
        run("console.log('before')\nnull.foo");result("error");assertTrue(output(),output().contains("第 2 行"))
        run("// first\r\n// second\rnull.foo");result("error");assertTrue(output(),output().contains("第 3 行"))
        run("Promise.reject(new Error('unhandled-v5'))");result("error");assertTrue(output(),output().contains("unhandled-v5"))
        run("console.log('a\u2028b'.length,'x\u2029y'.length)");result("done");assertTrue(output(),output().contains("3 3"))
    }

    @Test fun infiniteWorkerTimesOutWithoutBlockingPageAndLateTimersAreCutOff() {
        run("while(true){}",500)
        assertEquals("2",js("1+1"))
        result("timeout")
        run("setTimeout(()=>console.log('too-late-v5'),800)")
        result("done");assertFalse(output().contains("too-late-v5"))
        run("await new Promise(r=>setTimeout(r,800));console.log('awaited-v5')")
        result("done");assertTrue(output().contains("awaited-v5"))
    }

    @Test fun allWorkerMessagePathsAreBoundedAndCannotEraseTerminalDiagnostics() {
        run("for(let i=0;i<5000;i++)console.log('row',i)")
        val logs=result("limit");assertTrue(logs.getInt("lines")<=1000);assertTrue(logs.getInt("chars")<=200000)
        assertTrue(js("document.querySelectorAll('#out .line:not(.sys)').length").toInt()<=1000)
        run("for(let i=0;i<10000;i++)postMessage({t:'log',level:'sys',text:'forged-v5'})")
        result("limit");assertFalse(output().contains("forged-v5"))
        run("for(let i=0;i<10000;i++)postMessage({t:'clear'})")
        result("limit");assertTrue(output().contains("已终止"))
        run("postMessage({t:'main-error',text:'keep-error-v5'});postMessage({t:'clear'});postMessage({t:'log',level:'sys',text:'forged-after-stop'})")
        result("error");assertTrue(output().contains("keep-error-v5"));assertFalse(output().contains("forged-after-stop"))
        run("for(let i=0;i<100;i++)console.log('x'.repeat(100000))")
        val chars=result("limit");assertTrue(chars.getInt("chars")<=200000)
    }

    @Test fun realWorkerCspBlocksNetworkAndScriptImportsInBothEditions() {
        run("""
            let violations=[];self.addEventListener('securitypolicyviolation',e=>violations.push(e.effectiveDirective));
            try { await fetch('https://example.com/v5-fetch');console.log('FETCH ALLOWED'); } catch(e) {console.log('fetch blocked');}
            try { importScripts('https://cdn.jsdelivr.net/v5.js');console.log('IMPORT ALLOWED'); } catch(e) {console.log('import blocked');}
            await new Promise(resolve=>{try{const ws=new WebSocket('wss://example.com/v5');ws.onopen=()=>{console.log('WS ALLOWED');ws.close();resolve()};ws.onerror=()=>{console.log('ws blocked');resolve()}}catch(e){console.log('ws blocked');resolve()}});
            await new Promise(resolve=>{const x=new XMLHttpRequest();x.onerror=()=>{console.log('xhr blocked');resolve()};try{x.open('GET','https://example.com/v5-xhr');x.send()}catch(e){console.log('xhr blocked');resolve()}});
            try {await fetch('file:///sdcard/v5-private');console.log('FILE ALLOWED')}catch(e){console.log('file blocked')}
            try {await fetch('content://v5-private');console.log('CONTENT ALLOWED')}catch(e){console.log('content blocked')}
            await new Promise(r=>setTimeout(r,100));console.log('violations',violations.join(','));
        """.trimIndent())
        result("done")
        val out=output();for(text in listOf("fetch blocked","import blocked","ws blocked","xhr blocked","file blocked","content blocked","connect-src","script-src"))assertTrue(text,out.contains(text))
        assertFalse(out.contains(" ALLOWED"))
    }

    @Test fun workerStorageIsNewForEachRunAndThemeChangesDoNotRerun() {
        val code="""
            const db=await new Promise((resolve,reject)=>{const q=indexedDB.open('v5-runner',1);q.onupgradeneeded=()=>q.result.createObjectStore('s');q.onsuccess=()=>resolve(q.result);q.onerror=()=>reject(q.error)});
            const tx=db.transaction('s','readwrite'),store=tx.objectStore('s');
            const previous=await new Promise((resolve,reject)=>{const q=store.get('value');q.onsuccess=()=>resolve(q.result);q.onerror=()=>reject(q.error)});
            store.put('saved','value');await new Promise((resolve,reject)=>{tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error)});db.close();
            const marker=self.location.origin+'/v5-marker';const cache=await caches.open('v5-cache');console.log('cache previous',!!(await cache.match(marker)));await cache.put(marker,new Response('saved'));
            console.log('db previous',previous);
        """.trimIndent()
        repeat(2) { run(code);result("done");assertTrue(output(),output().contains("db previous undefined"));assertTrue(output(),output().contains("cache previous false")) }
        var original:android.webkit.WebView?=null
        compose.runOnUiThread { original=find(compose.activity.window.decorView) }
        for(t in ReaderTheme.entries) {
            compose.runOnUiThread { theme.value=t }
            compose.waitUntil(10_000) { js("document.documentElement.dataset.theme === '${t.id}'")=="true" }
            compose.runOnUiThread { assertSame(original,find(compose.activity.window.decorView)) }
            assertEquals("done",JSONObject(js("window.__runResult")).getString("state"))
        }
    }
}
