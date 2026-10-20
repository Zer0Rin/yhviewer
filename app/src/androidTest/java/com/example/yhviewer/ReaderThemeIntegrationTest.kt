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
    private lateinit var legacyChapterMode: LegacyChapterMode
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
        legacyChapterMode = LegacyChapterMode(context)
        originalTheme = prefs.getString("md_theme", null)
        originalNight = shell("cmd uimode night").substringAfter(':').trim()
        assertTrue(prefs.edit().putString("md_theme", "auto").commit())
        shell("cmd uimode night no")
    }
    @After fun restore() {
        legacyChapterMode.restore()
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
    private fun waitJs(scenario: ActivityScenario<MainActivity>, expression: String, expected: String = "true") {
        try { compose.waitUntil(25_000) { js(scenario, expression) == expected } }
        catch (e: Throwable) {
            android.util.Log.e("ReaderPositionTest", "expr=$expression DOM=" + js(scenario, "JSON.stringify({before:window.__beforeRatio,y:scrollY,h:innerHeight,total:document.documentElement.scrollHeight,theme:document.documentElement.dataset.theme})"))
            scenario.onActivity { a -> val w=web(a.window.decorView);android.util.Log.e("ReaderPositionTest", "native=${w?.scrollRatio()} vm=${androidx.lifecycle.ViewModelProvider(a)[ShelfViewModel::class.java].initialRatio()}") }
            throw e
        }
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) {
        compose.waitUntil(25_000) {
            var ready=false
            scenario.onActivity { ready=web(it.window.decorView)?.progress==100 }
            ready
        }
        waitJs(scenario, "document.readyState==='complete' && !!document.querySelector('#content h1')")
    }
    private fun changeNight(scenario: ActivityScenario<MainActivity>, mode: String) {
        var previous: MainActivity? = null
        scenario.onActivity { previous = it }
        shell("cmd uimode night $mode")
        // The configuration command returns before Activity recreation. Do not inspect the old
        // DOM and then accidentally assert against a new, still blank WebView.
        compose.waitUntil(25_000) {
            var changed = false
            scenario.onActivity { changed = it !== previous }
            changed
        }
        ready(scenario)
    }
    private fun select(label: String) {
        compose.onNodeWithText("Aa").performClick()
        compose.onNode(hasText(label, substring = true)).performClick()
        androidx.test.espresso.Espresso.pressBack()
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
                js(scenario, "window.__beforeRatio=scrollY/(document.documentElement.scrollHeight-innerHeight)")
                select(label)
                theme(scenario, id)
                // Themes reflow text; v2 preserves a relative position, while keeping the same DOM.
                waitJs(scenario, "document.body.offsetHeight>0 && Math.abs(scrollY/(document.documentElement.scrollHeight-innerHeight)-window.__beforeRatio)<.03")
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
            changeNight(scenario, "yes")
            waitJs(scenario, "matchMedia('(prefers-color-scheme: dark)').matches")
            assertEquals("\"rgb(38, 38, 36)\"", js(scenario, "getComputedStyle(document.documentElement).backgroundColor"))
            select("纸张（浅）")
            theme(scenario, "paper")
            assertEquals("\"rgb(250, 249, 245)\"", js(scenario, "getComputedStyle(document.documentElement).backgroundColor"))
            select("跟随系统")
            changeNight(scenario, "no")
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
            compose.onNodeWithText("终端").assertIsSelected()
        }
    }

    @Test fun htmlAaAdjustsTextSizeWithoutShowingThemeOptions() {
        assertTrue(prefs.edit().putString("md_theme", "ink").commit())
        val html = "<h1>Original HTML</h1><script>window.themeCalls=0;window.setTheme=()=>window.themeCalls++</script>"
        val savedTrusted = AppCapabilities.effective(DocPermissions.TRUSTED)
        launch(document("theme-original.html", html, true), true).use { scenario ->
            waitJs(scenario, "document.readyState==='complete' && typeof window.setTheme==='function'")
            compose.onNodeWithText("Aa").performClick()
            compose.onNodeWithText("字号").assertIsDisplayed()
            compose.onNodeWithText("主题").assertDoesNotExist()
            androidx.test.espresso.Espresso.pressBack()
            assertEquals("0", js(scenario, "window.themeCalls"))
            assertEquals("\"Original HTML\"", js(scenario, "document.querySelector('h1').textContent"))
            assertEquals("null", js(scenario, "document.documentElement.getAttribute('data-theme')"))
            compose.onNodeWithText("权限").performClick()
            compose.onNodeWithText("信任").performClick()
            compose.waitUntil(10_000) {
                var changed=false
                scenario.onActivity { a ->
                    val doc=(androidx.lifecycle.ViewModelProvider(a)[ShelfViewModel::class.java].reader as? ReaderState.Ready)?.doc as? OpenedDoc.Single
                    changed=doc?.permissions==savedTrusted
                }
                changed
            }
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithText("已放宽").assertIsDisplayed()
            scenario.recreate()
            waitJs(scenario, "document.readyState==='complete' && typeof window.setTheme==='function'")
            scenario.onActivity { a ->
                val doc=(androidx.lifecycle.ViewModelProvider(a)[ShelfViewModel::class.java].reader as ReaderState.Ready).doc as OpenedDoc.Single
                assertEquals(savedTrusted,doc.permissions)
                assertFalse(doc.permissions.frames || doc.permissions.network)
                val stored=runBlocking { AppDatabase.get(context).bookDao().get(doc.bookId)!! }
                assertEquals(savedTrusted.toBits(),stored.permissions)
                if (!AppCapabilities.networkCapable) {
                    assertFalse(DocPermissions.fromBits(stored.permissions).effective(networkCapable = true).needsNetwork)
                }
            }
            assertEquals("0", js(scenario, "window.themeCalls"))
        }
    }
    @Test fun initialThemeWaitsForPageAndInvalidNativeValuesAreRejected() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            val selected = mutableStateOf(ReaderTheme.INK)
            val html = MarkdownRenderer.render(source, MarkdownRenderer.template(context))
            scenario.onActivity { activity ->
                activity.setContent { YHViewerTheme { HtmlView(PageSource.Html(html), Modifier.fillMaxSize(), selected.value) } }
            }
            ready(scenario)
            theme(scenario, "ink")
            compose.runOnUiThread { selected.value = ReaderTheme.fromId("ink');window.__themeInjected=1;//") }
            compose.waitForIdle()
            theme(scenario, "auto")
            assertEquals("\"undefined\"", js(scenario, "typeof window.__themeInjected"))
        }
    }

}
