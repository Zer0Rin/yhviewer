package com.example.yhviewer

import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.yhviewer.ui.theme.YHViewerTheme
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MarkdownRenderingIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val source get() = instrumentation.context.assets.open("render-test.md").bufferedReader().use { it.readText() }

    private fun findWeb(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun js(expression: String): String {
        val done = CountDownLatch(1)
        var value = "null"
        compose.runOnUiThread {
            val web = findWeb(compose.activity.window.decorView)
            if (web == null) done.countDown()
            else web.evaluateJavascript(expression) { value = it; done.countDown() }
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        return value
    }
    private fun load(markdown: String) {
        val template = MarkdownRenderer.template(compose.activity).replace("<head>", "<head><script>window.__alerts=[];window.alert=x=>window.__alerts.push(x);window.__imageBlocked=false;document.addEventListener('securitypolicyviolation',e=>{if(e.effectiveDirective==='img-src')window.__imageBlocked=true});</script>")
        val html = MarkdownRenderer.render(markdown, template)
        compose.runOnUiThread { compose.activity.setContent { YHViewerTheme { HtmlView(html, Modifier.fillMaxSize()) } } }
        compose.waitUntil(25_000) { js("document.readyState") == "\"complete\"" && js("!!document.querySelector('#content')?.textContent") == "true" }
    }

    @Test fun renderingSafetyHighlightThemeAndExternalLink() {
        val dark = InstrumentationRegistry.getArguments().getString("dark") == "true"
        val markdown = source + "\n\n| Wide | Value |\n|---|---|\n| Width | " + "W".repeat(160) + " |\n"
        load(markdown)
        assertEquals("true", js("document.querySelector('#content h1').textContent==='第 4 步渲染测试' && !!document.querySelector('blockquote') && !!document.querySelector('ol ul') && document.querySelectorAll('table').length===2 && !!document.querySelector('strong') && !!document.querySelector('em')"))
        assertEquals("true", js("Array.from(document.querySelectorAll('a')).some(a=>a.href==='https://example.com/linkify')"))
        assertEquals("true", js("!!hljs.getLanguage('kotlin') && !!hljs.getLanguage('python') && document.querySelectorAll('pre.hljs .hljs-keyword').length>=2"))
        assertEquals("0", js("window.__alerts.length"))
        assertEquals("0", js("document.querySelectorAll('#content script').length"))
        assertEquals("true", js("document.querySelector('#content').textContent.includes(\"<script>alert('md html leaked')</script>\") && document.querySelector('#content').textContent.includes(\"</script><script>alert('json breakout')</script>\")"))
        assertEquals(markdown, JSONTokener(js("JSON.parse(document.querySelector('#md-src').textContent)")).nextValue())
        compose.waitUntil(10_000) { js("window.__imageBlocked") == "true" }
        assertEquals("true", js("Array.from(document.images).every(i=>i.complete && i.naturalWidth===0)"))
        assertEquals("true", js("(()=>{let t=Array.from(document.querySelectorAll('table')).pop();return getComputedStyle(t).overflowX==='auto' && t.scrollWidth>t.clientWidth && document.body.scrollWidth<=innerWidth})()"))
        assertEquals(dark.toString(), js("matchMedia('(prefers-color-scheme: dark)').matches"))
        val keyword = js("getComputedStyle(document.querySelector('pre.hljs .hljs-keyword')).color")
        android.util.Log.i("MarkdownTest", "dark=$dark keyword=$keyword")
        assertEquals(if (dark) "\"rgb(229, 143, 180)\"" else "\"rgb(162, 65, 107)\"", keyword)
        val monitor = Instrumentation.ActivityMonitor(IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE); addDataScheme("https")
        }, Instrumentation.ActivityResult(0,null), true)
        instrumentation.addMonitor(monitor)
        try {
            js("document.querySelector('#content a').style.cssText='position:fixed;inset:20%;z-index:999;background:white'")
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(WebView::class.java))
                .perform(androidx.test.espresso.action.ViewActions.click())
            compose.waitUntil(10_000) { monitor.hits==1 }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun libraryAvailabilityMatchesBuildVariant() {
        val missing = InstrumentationRegistry.getArguments().getString("missingMarkdown") == "true"
        load(source)
        if (!missing) {
            assertEquals("\"function\"", js("typeof window.markdownit"))
            assertEquals("true", js("!!document.querySelector('#content h1') && !document.querySelector('.fallback-note')"))
            return
        }
        assertEquals("\"undefined\"", js("typeof window.markdownit"))
        assertEquals("true", js("document.querySelector('.fallback-note').textContent.includes('未加载')"))
        assertEquals(source, JSONTokener(js("document.querySelector('#content pre').textContent")).nextValue())
        assertEquals("0", js("window.__alerts.length"))
        assertEquals("0", js("document.querySelectorAll('#content script').length"))
    }
}
