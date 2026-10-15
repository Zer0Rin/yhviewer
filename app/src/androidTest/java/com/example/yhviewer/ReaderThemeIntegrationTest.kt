package com.example.yhviewer

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.example.yhviewer.ui.theme.YHViewerTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ReaderThemeIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefs get() = context.getSharedPreferences("reader_prefs", 0)
    private val created = mutableListOf<Uri>()
    private var originalTheme: String? = null
    private var originalNight = "no"

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText() }
    }
    @Before fun prepare() {
        originalTheme = prefs.getString("md_theme", null)
        originalNight = shell("cmd uimode night").substringAfter(':').trim()
        assertTrue(prefs.edit().putString("md_theme", "auto").commit())
        shell("cmd uimode night no")
    }
    @After fun restore() {
        runBlocking {
            val repo = ShelfRepository(context, AppDatabase.get(context))
            created.forEach { uri ->
                AppDatabase.get(context).bookDao().findBySource(uri.toString())?.let { repo.delete(it.id) }
                context.contentResolver.delete(uri, null, null)
            }
        }
        val edit = prefs.edit()
        if (originalTheme == null) edit.remove("md_theme") else edit.putString("md_theme", originalTheme)
        assertTrue(edit.commit())
        shell("cmd uimode night $originalNight")
    }
    private val source get() = instrumentation.context.assets.open("render-test.md").bufferedReader().use { it.readText() }
    private fun document(name: String, text: String, html: Boolean = false): Uri {
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (html) "text/html" else "text/markdown")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/YHViewerThemeTests")
        })!!
        context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
        created += uri
        return uri
    }
    private fun launch(uri: Uri, html: Boolean = false) = ActivityScenario.launch<MainActivity>(
        Intent(Intent.ACTION_VIEW).setClass(context, MainActivity::class.java)
            .setDataAndType(uri, if (html) "text/html" else "text/markdown")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    private fun web(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) web(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun js(scenario: ActivityScenario<MainActivity>, expression: String): String {
        val latch = CountDownLatch(1)
        var result = "null"
        scenario.onActivity { a ->
            web(a.window.decorView)?.evaluateJavascript(expression) { result = it; latch.countDown() } ?: latch.countDown()
        }
        assertTrue("JS callback timeout", latch.await(5, TimeUnit.SECONDS))
        return result
    }
    private fun waitJs(scenario: ActivityScenario<MainActivity>, expression: String, expected: String = "true") =
        compose.waitUntil(25_000) { js(scenario, expression) == expected }
    private fun ready(scenario: ActivityScenario<MainActivity>) = waitJs(scenario,
        "document.readyState==='complete' && !!document.querySelector('#content h1')")
    private fun select(label: String) {
        compose.onNodeWithText("Aa").performClick()
        compose.onNode(hasText(label, substring = true)).performClick()
    }
    private fun theme(scenario: ActivityScenario<MainActivity>, id: String) = waitJs(scenario,
        "document.documentElement.getAttribute('data-theme')", "\"$id\"")

    @Test fun menuSwitchesAllThemesWithoutReloadOrScrollLoss() {
        val markdown = source + "\n\n### Third level\n\n#### Fourth level\n\n##### Fifth level\n\n###### Sixth level\n\n| Wide | Value |\n|---|---|\n| Width | " + "W".repeat(160) + " |\n" +
            (1..70).joinToString("\n\n") { "Paragraph $it: enough content to retain a mid-document scroll position in every theme." }
        launch(document("theme-switch.md", markdown)).use { scenario ->
            ready(scenario)
            compose.onNodeWithText("Aa").assertIsDisplayed()
            var initialWeb: WebView? = null
            scenario.onActivity { initialWeb = web(it.window.decorView) }
            js(scenario, "window.__themeSentinel='same-document'")
            for ((id, label) in listOf("paper" to "纸张（浅）", "ink" to "墨色（深）", "terminal" to "终端", "auto" to "跟随系统")) {
                js(scenario, "scrollTo(0,600)")
                waitJs(scenario, "Math.abs(scrollY-600)<2")
                select(label)
                theme(scenario, id)
                // Flush CSS/layout, then verify pixel position and same document/WebView.
                waitJs(scenario, "document.body.offsetHeight>0 && Math.abs(scrollY-600)<2")
                assertEquals("\"same-document\"", js(scenario, "window.__themeSentinel"))
                scenario.onActivity { assertSame(initialWeb, web(it.window.decorView)) }
                assertEquals("true", js(scenario, "document.documentElement.scrollWidth<=innerWidth && document.body.scrollWidth<=innerWidth"))
                assertEquals("true", js(scenario, "(()=>{let t=Array.from(document.querySelectorAll('table')).pop();return getComputedStyle(t).overflowX==='auto' && t.scrollWidth>t.clientWidth})()"))
                assertEquals("true", js(scenario, "(()=>{let k=document.querySelector('pre.hljs .hljs-keyword');let probe=document.createElement('span');probe.style.color='var(--hl-keyword)';document.body.append(probe);let ok=getComputedStyle(k).color===getComputedStyle(probe).color;probe.remove();return ok})()"))
                js(scenario, "window.setTheme('not-a-theme')")
                theme(scenario, id)
            }
            select("终端")
            theme(scenario, "terminal")
            assertEquals("true", js(scenario, """
                (() => {
                    const style = el => getComputedStyle(el);
                    const size = el => parseFloat(style(el).fontSize);
                    const body = size(document.body);
                    const heads = [...document.querySelectorAll('h1,h2,h3,h4,h5,h6')];
                    return heads.every(h => !getComputedStyle(h, '::before').content.includes('#')
                        && style(h).color === 'rgb(233, 162, 59)' && style(h).fontWeight === '700')
                        && Math.abs(size(document.querySelector('h1')) / body - 1.3) < .01
                        && Math.abs(size(document.querySelector('h2')) / body - 1.15) < .01
                        && [...document.querySelectorAll('h3,h4,h5,h6')].every(h => Math.abs(size(h) - body) < .1)
                        && getComputedStyle(document.querySelector('ul > li'), '::before').content.includes('-')
                        && getComputedStyle(document.querySelector('blockquote'), '::before').content.includes('>')
                        && style(document.querySelector('a')).textDecorationLine.includes('underline');
                })()
            """.trimIndent()))
        }
    }

    @Test fun explicitInkAndPaperOverrideSystemWhileAutoFollowsIt() {
        launch(document("theme-system.md", source)).use { scenario ->
            ready(scenario)
            select("墨色（深）")
            theme(scenario, "ink")
            assertEquals("\"rgb(48, 48, 45)\"", js(scenario, "getComputedStyle(document.querySelector('pre')).backgroundColor"))
            assertEquals("false", js(scenario, "matchMedia('(prefers-color-scheme: dark)').matches"))
            select("跟随系统")
            theme(scenario, "auto")
            assertEquals("\"rgb(250, 249, 245)\"", js(scenario, "getComputedStyle(document.documentElement).backgroundColor"))
            shell("cmd uimode night yes")
            ready(scenario)
            waitJs(scenario, "matchMedia('(prefers-color-scheme: dark)').matches")
            assertEquals("\"rgb(38, 38, 36)\"", js(scenario, "getComputedStyle(document.documentElement).backgroundColor"))
            select("纸张（浅）")
            theme(scenario, "paper")
            assertEquals("\"rgb(250, 249, 245)\"", js(scenario, "getComputedStyle(document.documentElement).backgroundColor"))
            select("跟随系统")
            shell("cmd uimode night no")
            ready(scenario)
            waitJs(scenario, "!matchMedia('(prefers-color-scheme: dark)').matches")
            assertEquals("\"rgb(250, 249, 245)\"", js(scenario, "getComputedStyle(document.documentElement).backgroundColor"))
        }
    }

    @Test fun selectionSurvivesActivityAndViewModelReplacement() {
        val uri = document("theme-persist.md", source)
        launch(uri).use { scenario ->
            ready(scenario)
            select("终端")
            theme(scenario, "terminal")
        }
        launch(uri).use { scenario ->
            ready(scenario)
            theme(scenario, "terminal")
            compose.onNodeWithText("Aa").performClick()
            compose.onNodeWithText("✓ 终端").assertIsDisplayed()
        }
    }

    @Test fun htmlHasNoThemeMenuAndItsOwnSetThemeIsNeverCalled() {
        assertTrue(prefs.edit().putString("md_theme", "ink").commit())
        val html = "<h1>Original HTML</h1><script>window.themeCalls=0;window.setTheme=()=>window.themeCalls++</script>"
        launch(document("theme-original.html", html, true), true).use { scenario ->
            waitJs(scenario, "document.readyState==='complete' && typeof window.setTheme==='function'")
            compose.onNodeWithText("Aa").assertDoesNotExist()
            assertEquals("0", js(scenario, "window.themeCalls"))
            assertEquals("\"Original HTML\"", js(scenario, "document.querySelector('h1').textContent"))
            assertEquals("null", js(scenario, "document.documentElement.getAttribute('data-theme')"))
        }
    }
    @Test fun initialThemeWaitsForPageAndInvalidNativeValuesAreRejected() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            val selected = mutableStateOf("ink")
            val html = MarkdownRenderer.render(source, MarkdownRenderer.template(context))
            scenario.onActivity { activity ->
                activity.setContent { YHViewerTheme { HtmlView(html, Modifier.fillMaxSize(), selected.value) } }
            }
            ready(scenario)
            theme(scenario, "ink")
            compose.runOnUiThread { selected.value = "ink');window.__themeInjected=1;//" }
            compose.waitForIdle()
            theme(scenario, "ink")
            assertEquals("\"undefined\"", js(scenario, "typeof window.__themeInjected"))
        }
    }

}
