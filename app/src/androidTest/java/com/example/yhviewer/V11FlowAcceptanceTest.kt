package com.example.yhviewer

import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Exercises the real app shell, its sandboxed chapters, native WebView scrolling and Compose wiring. */
@RunWith(AndroidJUnit4::class)
class V11FlowAcceptanceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val db get() = AppDatabase.get(context)
    private val repo get() = ShelfRepository(context, db)
    private lateinit var fixtures: V11GeneratedFixtures
    private lateinit var oldReading: ReadingPrefs
    private var oldTheme: String? = null

    @Before fun setup() {
        fixtures = V11GeneratedFixtures(context)
        oldReading = ReadingPrefsStore(context).load()
        oldTheme = context.getSharedPreferences("reader_prefs", 0).getString("md_theme", null)
        ReadingPrefsStore(context).save(ReadingPrefs(continuous = true))
        context.getSharedPreferences("reader_prefs", 0).edit().putString("md_theme", "paper").commit()
    }
    @After fun cleanup() {
        try { runBlocking { fixtures.clean() } }
        finally {
            ReadingPrefsStore(context).save(oldReading)
            context.getSharedPreferences("reader_prefs", 0).edit().apply {
                oldTheme?.let { putString("md_theme", it) } ?: remove("md_theme")
            }.commit()
        }
    }
    private fun launch() = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
    private fun model(s: ActivityScenario<MainActivity>, action: (ShelfViewModel) -> Unit) {
        s.onActivity { action(ViewModelProvider(it)[ShelfViewModel::class.java]) }
    }
    private fun chapter(s: ActivityScenario<MainActivity>): Int {
        var value = -1; model(s) { value = it.chapter }; return value
    }
    private fun web(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) repeat(view.childCount) { web(view.getChildAt(it))?.let { found -> return found } }
        return null
    }
    private fun view(s: ActivityScenario<MainActivity>): WebView {
        var result: WebView? = null; s.onActivity { result = web(it.window.decorView) }; return checkNotNull(result)
    }
    private fun url(s: ActivityScenario<MainActivity>): String? {
        var result: String? = null
        s.onActivity { result = web(it.window.decorView)?.url }
        return result
    }
    private fun js(s: ActivityScenario<MainActivity>, expression: String): String {
        val latch = CountDownLatch(1); var result = "null"
        s.onActivity { checkNotNull(web(it.window.decorView)).evaluateJavascript(expression) { result = it; latch.countDown() } }
        assertTrue("evaluateJavascript callback: $expression", latch.await(5, TimeUnit.SECONDS))
        return result
    }
    private fun string(s: ActivityScenario<MainActivity>, expression: String) = JSONTokener(js(s, expression)).nextValue().toString()
    private fun state(s: ActivityScenario<MainActivity>): Pair<Int, Float> {
        val parts = string(s, "YH.state()").split(','); return parts[0].toInt() to parts[1].toFloat()
    }
    private fun frames(s: ActivityScenario<MainActivity>) = JSONArray(js(s, "JSON.stringify([...document.querySelectorAll('section')].map(s=>Number(s.dataset.ch)))").let { JSONTokener(it).nextValue().toString() })
    private fun bounded(s: ActivityScenario<MainActivity>) {
        assertTrue("At most five live chapter frames", js(s, "document.querySelectorAll('iframe').length").toInt() in 1..5)
    }
    private fun ready(s: ActivityScenario<MainActivity>, expectedStart: Int? = null, previousView: WebView? = null) {
        val deadline = SystemClock.uptimeMillis() + 25_000
        val readyNow = {
            runCatching { (previousView == null || view(s) !== previousView) &&
                (expectedStart == null || js(s, "JSON.parse(document.getElementById('payload').textContent).start") == expectedStart.toString()) &&
                js(s, "!!window.YH && YH.state()!=='' && (()=>{const fs=[...document.querySelectorAll('iframe')];return fs.length>=1 && fs.length<=5 && fs.every(f=>f.contentDocument && f.contentDocument.readyState==='complete' && f.offsetHeight>0)})()") == "true" }.getOrDefault(false)
        }
        fun awaitReady() = compose.waitUntil((deadline - SystemClock.uptimeMillis()).coerceAtLeast(1)) { readyNow() }
        awaitReady()
        // DOM readiness can precede the Compose effect's poll timeout. Drain
        // that queued work before tests drive WebView outside Compose actions.
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        // Clock advancement can start a pending replacement navigation. Check
        // the same shell identity and loaded frames again within the original deadline.
        awaitReady()
        bounded(s)
    }
    private fun open(s: ActivityScenario<MainActivity>, id: Long) {
        model(s) { it.openBook(id) }
        try {
            compose.waitUntil(25_000) {
                var matches = false
                model(s) { matches = (it.reader as? ReaderState.Ready)?.doc?.bookId == id }
                matches && url(s)?.endsWith("/flow/index.html") == true
            }
        } catch (failure: Throwable) {
            var diagnostic = ""
            s.onActivity { activity ->
                val vm = ViewModelProvider(activity)[ShelfViewModel::class.java]
                diagnostic = "expectedId=$id actualId=${(vm.reader as? ReaderState.Ready)?.doc?.bookId} continuous=${vm.reading.continuous} reader=${vm.reader} url=${web(activity.window.decorView)?.url}"
            }
            throw AssertionError("Flow open failed: $diagnostic", failure)
        }
        compose.waitForIdle()
        ready(s)
    }
    private fun waitText(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun nativeScroll(s: ActivityScenario<MainActivity>, direction: Int) {
        s.onActivity { checkNotNull(web(it.window.decorView)).apply { scrollBy(0, direction * height / 2) } }
        SystemClock.sleep(150)
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        bounded(s)
    }
    private fun scrollUntilChapter(s: ActivityScenario<MainActivity>, target: Int, direction: Int) {
        repeat(70) {
            val actual = state(s)
            if (actual.first == target) {
                // DOM position is immediate; stop moving before waiting for the
                // debounced native title/progress callback to catch up.
                compose.waitUntil(10_000) { chapter(s) == target }
                return
            }
            assertTrue("Native scroll must not skip requested chapter $target: DOM=$actual VM=${chapter(s)} frames=${frames(s)}",
                if (direction > 0) actual.first < target else actual.first > target)
            nativeScroll(s, direction)
        }
        fail("Native parent scroll did not reach chapter $target; state=${state(s)}")
    }
    private fun persisted(id: Long) = runBlocking { Locator.decode(checkNotNull(db.bookDao().get(id)).locator) }

    private fun captureV14(name: String, s: ActivityScenario<MainActivity>, waitForIdle: Boolean = true) {
        var badge: TextView? = null
        s.onActivity { activity ->
            val density = activity.resources.displayMetrics.density
            badge = TextView(activity).apply {
                text = "Android WebView ${WebView.getCurrentWebViewPackage()?.versionName ?: "unknown"}"
                textSize = 11f
                setTextColor(Color.WHITE)
                setBackgroundColor(0x99000000.toInt())
                setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
                elevation = 20 * density
            }
            (activity.window.decorView as FrameLayout).addView(
                badge,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = (8 * density).toInt() },
            )
        }
        if (waitForIdle) {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(200)
        }
        exportV7Screenshot(name)
        s.onActivity { activity -> (activity.window.decorView as FrameLayout).removeView(badge) }
    }

    @Test fun continuousIsTheDefault() { assertTrue(ReadingPrefs().continuous) }

    @Test fun v16SettingsScreenShowsFlavorPrivacyLicenseAndReturnsToShelf() {
        launch().use { s ->
            compose.onNodeWithContentDescription("设置").performClick()
            waitText("隐私与安全")
            compose.onNodeWithText(AboutInfo.versionLine(BuildConfig.VERSION_NAME, BuildConfig.FLAVOR)).assertIsDisplayed()
            compose.onNodeWithText("作者").assertIsDisplayed()
            compose.onNodeWithText("项目主页").assertIsDisplayed()
            exportV7Screenshot("v16-settings-${BuildConfig.FLAVOR}.png")
            if (AppCapabilities.pythonAvailable) {
                compose.onNodeWithText("Pyodide").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("CPython").performScrollTo().assertIsDisplayed()
            } else {
                compose.onNodeWithText("Pyodide").assertDoesNotExist()
                compose.onNodeWithText("CPython").assertDoesNotExist()
            }
            compose.onNode(hasText("YHViewer") and hasClickAction()).performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("MIT License", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("Permission is hereby granted", substring = true)).assertExists()
            compose.onNodeWithText("关闭").performClick()
            Espresso.pressBack()
            waitText("书架")
        }
    }

    @Test fun v16BookGlassTopbarAlignsWithPageAndAaHasNoChapterProgress() {
        val id = runBlocking { fixtures.import(fixtures.epub(count = 20)) }
        launch().use { s ->
            open(s, id)
            compose.onNodeWithText("Aa").performClick()
            compose.onNodeWithText("进度").assertDoesNotExist()
            compose.onNodeWithContentDescription("章节进度").assertDoesNotExist()
            Espresso.pressBack()

            fun assertGlassMatches(themeLabel: String, screenshot: String) {
                compose.onNodeWithText("Aa").performClick()
                compose.onNodeWithText(themeLabel).performClick()
                Espresso.pressBack()
                compose.waitForIdle()
                SystemClock.sleep(500)
                assertEquals("Glass overlay should contain bottom and top regions ($themeLabel)", "2", js(s, "document.querySelectorAll('#yh-glass > div').length"))
                val rects = JSONArray(JSONTokener(js(s, "JSON.stringify([...document.querySelectorAll('#yh-glass > div')].map(e=>[parseFloat(e.style.top),parseFloat(e.style.height),parseFloat(e.style.borderRadius),parseFloat(getComputedStyle(e).opacity)]))")).nextValue().toString())
                val topBarRect = rects.getJSONArray(1)
                val bounds = compose.onNodeWithTag("book-top-bar-glass", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
                val wv = view(s)
                var viewTop = 0
                var scale = 1f
                s.onActivity {
                    val location = IntArray(2)
                    wv.getLocationOnScreen(location)
                    viewTop = location[1]
                    @Suppress("DEPRECATION")
                    run { scale = wv.scale }
                }
                val cssScreenTop = topBarRect.getDouble(0) * scale + viewTop
                assertEquals("Top glass should align with native top bar ($themeLabel)", bounds.top.toDouble(), cssScreenTop, (2f * scale).toDouble())
                assertEquals("Top glass should start at screen top ($themeLabel)", 0.0, cssScreenTop, (2f * scale).toDouble())
                assertEquals("Top glass corners should be square ($themeLabel)", 0.0, topBarRect.getDouble(2), .1)
                assertEquals("Bottom capsule glass should also be present ($themeLabel)", 1.0, rects.getJSONArray(0).getDouble(3), .01)
                exportV7Screenshot(screenshot)
            }

            assertGlassMatches("纸张（浅）", "v16-reader-glass-paper.png")
            assertGlassMatches("墨色（深）", "v16-reader-glass-ink.png")

            val wv = view(s)
            var scale = 1f
            s.onActivity {
                @Suppress("DEPRECATION")
                run { scale = wv.scale }
            }
            tapRenderedWebViewPoint(instrumentation, wv, wv.width / (2f * scale), wv.height / (2f * scale))
            compose.waitForIdle()
            SystemClock.sleep(700)
            val opacity = js(s, "JSON.stringify([...document.querySelectorAll('#yh-glass > div')].map(e=>parseFloat(getComputedStyle(e).opacity)))")
            assertFalse("Glass regions should fade with the hidden toolbars; opacity=$opacity", compose.onAllNodesWithTag("book-top-bar-glass", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
            assertTrue("Glass regions should become transparent with the hidden toolbars; opacity=$opacity", opacity.contains("0"))
        }
    }

    @Test fun tallEpubAaSheetStaysBelowStatusBarInContinuousMode() = verifyTallEpubAaSheet(true)

    @Test fun tallEpubAaSheetStaysBelowStatusBarInSinglePageMode() = verifyTallEpubAaSheet(false)

    private fun verifyTallEpubAaSheet(continuous: Boolean) {
        val id = runBlocking { fixtures.import(fixtures.epub(count = 1415)) }
        ReadingPrefsStore(context).save(ReadingPrefs(continuous = continuous))
        launch().use { s ->
            if (continuous) open(s, id) else {
                model(s) { it.openBook(id) }
                compose.waitUntil(25_000) {
                    var ready = false
                    model(s) { ready = (it.reader as? ReaderState.Ready)?.doc?.bookId == id }
                    ready && url(s) != null
                }
                compose.waitForIdle()
            }
            compose.onNodeWithText("Aa").performClick()
            compose.waitForIdle()
            SystemClock.sleep(500)
            val mode = if (continuous) "continuous" else "single"
            val first = compose.onNodeWithTag("reader-aa-sheet-handle", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow.top
            exportV7Screenshot("v15-tall-epub-$mode-500ms.png")
            SystemClock.sleep(1_500)
            val handle = compose.onNodeWithTag("reader-aa-sheet-handle", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
            assertEquals("Aa sheet top should stay fixed after opening (continuous=$continuous)", first, handle.top, 1f)
            val statusBarHeight = Resources.getSystem().getDimensionPixelSize(
                Resources.getSystem().getIdentifier("status_bar_height", "dimen", "android"),
            )
            val minTop = statusBarHeight + 56f * context.resources.displayMetrics.density
            assertTrue("Aa sheet should remain below status bar plus 56dp (continuous=$continuous): ${handle.top} < $minTop", handle.top >= minTop - 1f)
            exportV7Screenshot("v15-tall-epub-$mode-2000ms.png")
            compose.onNodeWithText("阅读时屏幕常亮").performScrollTo().assertIsDisplayed()
            exportV7Screenshot("v15-tall-epub-$mode-bottom.png")
        }
    }

    @Test fun v14ThemeUpdatesAllFramesSynchronouslyAndGlassStatesCanBeCaptured() {
        val id = runBlocking { fixtures.import(fixtures.txt()) }
        runBlocking { repo.savePosition(id, Locator(5, .4f), 12) }
        launch().use { s ->
            open(s, id)
            val original = view(s)
            val before = state(s)
            captureV14("v14-paper-capsule-webview.png", s)
            js(s, """
                window.__v14ThemeSync=null; window.__v14ThemeAfterTwoFrames=null;
                const setTheme=window.setTheme;
                window.setTheme=function(t){
                  setTheme(t);
                  window.__v14ThemeSync={outer:document.documentElement.dataset.theme,
                    frames:[...document.querySelectorAll('iframe')].map(f=>f.contentDocument?.documentElement?.dataset?.theme||null)};
                  requestAnimationFrame(()=>requestAnimationFrame(()=>window.__v14ThemeAfterTwoFrames=YH.state()));
                };
            """.trimIndent())
            model(s) { it.selectTheme(ReaderTheme.INK) }
            compose.waitUntil(10_000) { js(s, "window.__v14ThemeSync!==null") == "true" }
            val sameFrame = JSONObject(string(s, "JSON.stringify(window.__v14ThemeSync)"))
            assertEquals("ink", sameFrame.getString("outer"))
            val frameThemes = sameFrame.getJSONArray("frames")
            assertTrue("At least one chapter iframe should be mounted", frameThemes.length() >= 1)
            repeat(frameThemes.length()) { assertEquals("ink", frameThemes.getString(it)) }
            assertSame("Theme change must retain the existing WebView", original, view(s))
            compose.waitUntil(10_000) { js(s, "window.__v14ThemeAfterTwoFrames!==null") == "true" }
            val restored = string(s, "window.__v14ThemeAfterTwoFrames").split(',')
            assertEquals(before.first, restored[0].toInt())
            assertEquals(before.second, restored[1].toFloat(), .04f)

            captureV14("v14-ink-capsule-webview.png", s)
            model(s) { it.selectTheme(ReaderTheme.TERMINAL) }
            compose.waitUntil(10_000) { js(s, "document.documentElement.dataset.theme==='terminal'") == "true" }
            captureV14("v14-terminal-highlight-webview.png", s)

            compose.onNodeWithText("Aa").performClick()
            compose.waitForIdle()
            captureV14("v14-settings-sheet-webview.png", s)
            val slider = compose.onNodeWithContentDescription("字号")
            slider.performTouchInput {
                down(center)
                moveTo(centerRight, 500)
            }
            compose.waitForIdle()
            captureV14("v14-glass-slider-transparent-sheet-webview.png", s)
            slider.performTouchInput { up() }
            compose.waitForIdle()
            androidx.test.espresso.Espresso.pressBack()
        }
    }

    @Test fun idleFlowPollingAllowsAutomaticDebouncedPositionSave() {
        val id = runBlocking { fixtures.import(fixtures.txt()) }
        runBlocking { repo.savePosition(id, Locator(5, .4f), 12) }
        launch().use { s ->
            open(s, id)
            try {
                scrollUntilChapter(s, 6, 1)
            } catch (failure: Throwable) {
                val outer = runCatching { string(s, "JSON.stringify({ready:document.readyState,url:location.href,scrollY:scrollY,innerHeight:innerHeight,scrollHeight:document.documentElement.scrollHeight,frames:[...document.querySelectorAll('section')].map(s=>({chapter:s.dataset.ch,top:s.offsetTop,height:s.offsetHeight,frameReady:s.querySelector('iframe')?.contentDocument?.readyState}))})") }.getOrElse { "unavailable: $it" }
                throw AssertionError("Auto-save boundary diagnostics: outer=$outer", failure)
            }
            val expected = state(s)
            // No flushPosition: the ordinary 800 ms idle debounce must finish
            // even while the reader keeps polling for iframe link clicks.
            compose.waitUntil(10_000) {
                val p = persisted(id)
                p.chapter == expected.first && abs((p.flowRatio ?: p.ratio) - expected.second) < .04f
            }
            val saved = persisted(id)
            assertEquals(expected.first, saved.chapter)
            assertEquals(expected.second, saved.flowRatio ?: saved.ratio, .04f)
        }
    }
    @Test fun txtNativeBoundaryScrollPersistsAndReopens() = boundaries(false)
    @Test fun epubNativeBoundaryScrollPersistsAndReopens() = boundaries(true)
    private fun boundaries(epub: Boolean) {
        val id = runBlocking { fixtures.import(if (epub) fixtures.epub() else fixtures.txt()) }
        runBlocking { repo.savePosition(id, Locator(5, .4f), 12) }
        launch().use { s ->
            open(s, id)
            assertEquals(5, state(s).first)
            assertEquals(.4f, state(s).second, .035f)
            val original = view(s)
            scrollUntilChapter(s, 6, 1)
            assertSame("Ordinary forward boundary keeps WebView", original, view(s))
            compose.onNodeWithText("7 / 12").assertIsDisplayed()
            compose.onNode(hasText("第7章 Flow 7") and hasAnyAncestor(hasTestTag("BookReaderBackground"))).assertIsDisplayed()
            scrollUntilChapter(s, 5, -1)
            assertSame("Ordinary backward boundary keeps WebView", original, view(s))
            compose.onNodeWithText("6 / 12").assertIsDisplayed()
            if (InstrumentationRegistry.getArguments().getString("v7Screenshots") == "true") {
                js(s, "window.scrollTo(0,document.querySelector('[data-ch=\"6\"]').offsetTop-innerHeight/2)")
                SystemClock.sleep(300)
                exportV7Screenshot("v08-continuous-boundary.png")
            }
            val expected = state(s)
            model(s) { it.flushPosition() }
            compose.waitUntil(10_000) {
                val p = persisted(id)
                p.chapter == expected.first && abs((p.flowRatio ?: p.ratio) - expected.second) < .04f
            }
            val saved = persisted(id)
            assertEquals(expected.first, saved.chapter)
            assertEquals(expected.second, saved.flowRatio ?: saved.ratio, .04f)
            model(s) { it.backToShelf() }
            open(s, id)
            assertEquals("Reopen preserves exact chapter", expected.first, state(s).first)
            assertEquals("Reopen preserves reflow ratio", expected.second, state(s).second, .04f)
        }
    }

    /** Capture a real visible paragraph's text and CSS offset relative to the outer viewport. */
    private fun anchor(s: ActivityScenario<MainActivity>): JSONObject = JSONObject(string(s, """
        JSON.stringify((()=>{for(const f of document.querySelectorAll('iframe')) {
          const ft=f.getBoundingClientRect().top;
          for(const p of f.contentDocument.querySelectorAll('p')) { const r=p.getBoundingClientRect();
            if(ft+r.bottom>2 && ft+r.top<innerHeight) return {text:p.textContent,offset:ft+r.top}; }
        } return {text:'',offset:0};})())
    """.trimIndent()))
    private fun assertAnchor(before: JSONObject, after: JSONObject) {
        assertTrue("A visible book paragraph was captured", before.getString("text").isNotBlank())
        assertEquals("Visible text stays anchored", before.getString("text"), after.getString("text"))
        assertEquals("CSS viewport offset stays anchored", before.getDouble("offset"), after.getDouble("offset"), 3.0)
    }
    private fun liveLayout(s: ActivityScenario<MainActivity>): JSONObject = JSONObject(string(s, """
        JSON.stringify((()=>{const f=document.querySelector('[data-ch="5"] iframe'),d=f?.contentDocument;
          return {css:d?.getElementById('yh-reading')?.textContent||'',fontPx:d?parseFloat(d.defaultView.getComputedStyle(d.body).fontSize):0,
            height:f?.offsetHeight||0,bodyHeight:d?.body?.scrollHeight||0,state:YH.state()};})())
    """.trimIndent()))
    private fun waitForLiveLayout(s: ActivityScenario<MainActivity>, cssToken: String): JSONObject {
        var previous = ""
        var settled: JSONObject? = null
        compose.waitUntil(15_000) {
            val sample = liveLayout(s)
            val current = sample.toString()
            val ready = sample.getString("css").contains(cssToken) && sample.getDouble("height") >= sample.getDouble("bodyHeight") && sample.getString("state").isNotBlank()
            val stable = ready && current == previous
            previous = current
            if (stable) settled = sample
            stable
        }
        return checkNotNull(settled)
    }
    @Test fun prependAndHeightGrowthAboveViewportKeepVisibleParagraph() {
        val id = runBlocking { fixtures.import(fixtures.txt()) }
        runBlocking { repo.savePosition(id, Locator(6, .4f), 12) }
        launch().use { s ->
            open(s, id)
            js(s, "YH.hold(true);window.scrollTo(0,document.querySelector('[data-ch=\"6\"]').offsetTop+40)")
            val before = anchor(s)
            val previous = frames(s).toString()
            js(s, "YH.hold(false)")
            compose.waitUntil(15_000) { frames(s).toString() != previous && js(s, "!!document.querySelector('[data-ch=\"5\"] iframe') && document.querySelector('[data-ch=\"5\"] iframe').offsetHeight>0") == "true" }
            assertAnchor(before, anchor(s))
            js(s, "YH.hold(true)")
            val beforeGrowth = anchor(s)
            // Trusted test-only DOM manipulation models an image whose dimensions resolve late.
            js(s, """(()=>{const d=document.querySelector('[data-ch="5"] iframe').contentDocument;const img=d.createElement('img');img.id='late-test-image';img.alt='';img.style.cssText='display:block;width:100px;height:900px';d.body.prepend(img);})()""")
            compose.waitUntil(10_000) { js(s, "document.querySelector('[data-ch=\"5\"] iframe').offsetHeight >= document.querySelector('[data-ch=\"5\"] iframe').contentDocument.body.scrollHeight") == "true" }
            assertAnchor(beforeGrowth, anchor(s))
            bounded(s)
        }
    }
    @Test fun tinyChapterWindowStopsReloadingAtFiveFrames() {
        val id = runBlocking { fixtures.import(fixtures.txt(80, short = true)) }
        runBlocking { repo.savePosition(id, Locator(35, 0f), 80) }
        launch().use { s ->
            open(s, id)
            js(s, "window.testAdds=0;new MutationObserver(ms=>ms.forEach(m=>window.testAdds+=m.addedNodes.length)).observe(document.getElementById('flow'),{childList:true})")
            compose.waitUntil(10_000) { js(s, "document.querySelectorAll('iframe').length").toInt() == 5 }
            SystemClock.sleep(900)
            val additions = js(s, "window.testAdds")
            SystemClock.sleep(1300) // >3 layout timer intervals, so an append/evict loop is observable.
            assertEquals("A stable short-chapter window must not churn frames", additions, js(s, "window.testAdds"))
            bounded(s)
        }
    }

    @Test fun aaThemeFontAndContinuousToggleUseRealControls() {
        val id = runBlocking { fixtures.import(fixtures.txt()) }
        runBlocking { repo.savePosition(id, Locator(5, .4f), 12) }
        launch().use { s ->
            open(s, id)
            val original = view(s)
            val before = anchor(s)
            val beforeState = state(s)
            compose.onNodeWithText("Aa").performClick()
            // Vertical scroll uses a non-horizontal sibling, then the horizontal chip row.
            compose.onNodeWithText("主题").performScrollTo()
            compose.onNodeWithText("跟随系统").performTouchInput { swipeLeft() }
            compose.onNodeWithText("终端").performScrollTo().assertIsDisplayed().performClick()
            // The non-horizontal sibling below the font row brings the whole
            // row into the sheet viewport before its horizontal gesture.
            compose.onNodeWithText("章节连续滚动").performScrollTo()
            compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange)
                and hasAnyDescendant(hasText("跟随主题"))).performTouchInput { swipeLeft() }
            compose.onNodeWithText("无衬线").performScrollTo().assertIsDisplayed().performClick()
            Espresso.pressBack()
            ready(s)
            waitForLiveLayout(s, "--read-font:var(--font-sans)")
            assertSame(original, view(s))
            assertEquals(5, chapter(s))
            assertEquals("terminal", string(s, "document.documentElement.dataset.theme"))
            assertEquals(before.getString("text"), anchor(s).getString("text"))
            assertEquals("Theme/font change preserves chapter", beforeState.first, state(s).first)
            assertEquals("Theme/font change preserves ratio", beforeState.second, state(s).second, .04f)
            model(s) { assertEquals(ReadingFont.SANS, it.reading.font) }
            val beforeScale = state(s)
            val beforeScaleAnchor = anchor(s)
            val beforeFontPx = liveLayout(s).getDouble("fontPx")
            compose.onNodeWithText("Aa").performClick()
            compose.onNodeWithContentDescription("字号").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(120f) }
            Espresso.pressBack()
            ready(s)
            val scaled = waitForLiveLayout(s, "--read-scale:1.20")
            assertSame(original, view(s))
            assertEquals(5, chapter(s))
            assertTrue("Font-size control changes rendered CSS font size", scaled.getDouble("fontPx") > beforeFontPx)
            assertEquals("Font-size change preserves visible paragraph", beforeScaleAnchor.getString("text"), anchor(s).getString("text"))
            assertEquals("Font-size change preserves chapter", beforeScale.first, state(s).first)
            assertEquals("Font-size change preserves ratio", beforeScale.second, state(s).second, .04f)
            model(s) { assertEquals(120, it.reading.fontScale) }
            compose.onNodeWithText("Aa").performClick()
            compose.onNodeWithText("章节连续滚动").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("章节连续滚动").performClick()
            Espresso.pressBack()
            compose.waitUntil(15_000) { runCatching { url(s)?.endsWith("/doc/index.html") == true }.getOrDefault(false) }
            assertNotSame("Changing architecture creates chapter WebView", original, view(s))
            assertEquals("Legacy chapter DOM", "0", js(s, "document.querySelectorAll('iframe').length"))
            assertEquals("Legacy body remains chapter 6", "true", js(s, "document.body.innerText.includes('Anchor chapter 5')"))
            assertFalse(ReadingPrefsStore(context).load().continuous)
            model(s) { it.backToShelf() }
            model(s) { it.openBook(id) }
            compose.waitUntil(15_000) { runCatching { url(s)?.endsWith("/doc/index.html") == true }.getOrDefault(false) }
            model(s) { assertFalse(it.reading.continuous) }
        }
    }

    @Test fun txtActualFlowNativeFindTargetsThirdHitAndHoldsFill() = find(false)
    @Test fun epubActualFlowNativeFindTargetsThirdHitAndHoldsFill() = find(true)
    @Test fun findOpenedBeforeShellApiExistsHoldsUntilClose() {
        val id = runBlocking { fixtures.import(fixtures.txt()) }
        runBlocking { repo.savePosition(id, Locator(5, .9f), 12) }
        launch().use { s ->
            open(s, id)
            val shellUrl = checkNotNull(url(s))
            // Control the WebView load gap: Find opens while no YH API exists.
            s.onActivity { checkNotNull(web(it.window.decorView)).loadUrl("about:blank") }
            compose.waitUntil(10_000) { runCatching { url(s) == "about:blank" && js(s, "typeof window.YH") == "\"undefined\"" }.getOrDefault(false) }
            compose.onNodeWithText("搜索").performClick()
            s.onActivity { checkNotNull(web(it.window.decorView)).loadUrl(shellUrl) }
            ready(s)
            assertEquals("Find starts with the target chapter alone", 1, js(s, "document.querySelectorAll('iframe').length").toInt())
            js(s, "window.scrollTo(0,document.querySelector('section').offsetHeight-innerHeight+100)")
            SystemClock.sleep(1300) // More than three shell layout intervals.
            assertEquals("Early Find freezes chapter additions", 1, js(s, "document.querySelectorAll('iframe').length").toInt())
            compose.onNodeWithText("关闭").performClick()
            compose.waitUntil(10_000) { js(s, "document.querySelectorAll('iframe').length").toInt() > 1 }
            bounded(s)
        }
    }
    private fun find(epub: Boolean) {
        val id = runBlocking { fixtures.import(if (epub) fixtures.epub() else fixtures.txt()) }
        launch().use { s ->
            open(s, id)
            compose.onNodeWithText("搜索").performClick()
            compose.onNodeWithContentDescription("查找内容").performTextInput(V11GeneratedFixtures.NEEDLE)
            compose.waitUntil(15_000) { compose.onAllNodesWithText("无结果").fetchSemanticsNodes().isEmpty() && compose.onAllNodesWithText("1/3").fetchSemanticsNodes().isNotEmpty() }
            // Exercise real full-book search control, then select an actual repo search hit via VM.
            compose.onNodeWithText("全书").performClick()
            try {
                waitText("「${V11GeneratedFixtures.NEEDLE}」共 36 处")
            } catch (failure: Throwable) {
                val ui = runCatching { compose.onRoot(useUnmergedTree = true).printToString() }.getOrNull()
                var vm: ShelfViewModel? = null
                var bookId: Long? = null
                model(s) {
                    vm = it
                    bookId = (it.reader as? ReaderState.Ready)?.doc?.bookId
                }
                val directHits = runCatching { runBlocking { checkNotNull(vm).searchBook(V11GeneratedFixtures.NEEDLE).size } }.getOrNull()
                throw AssertionError("Whole-book result did not show 36: query=${V11GeneratedFixtures.NEEDLE} bookId=$bookId directHits=$directHits ui=$ui", failure)
            }
            Espresso.pressBack()
            var vm: ShelfViewModel? = null; model(s) { vm = it }
            val hits = runBlocking { checkNotNull(vm).searchBook(V11GeneratedFixtures.NEEDLE) }
            val hit = hits.filter { it.chapter == 6 }[2]
            model(s) { it.openHit(hit, V11GeneratedFixtures.NEEDLE, 2) }
            ready(s, expectedStart = hit.chapter)
            waitText("3/3")
            compose.onNodeWithText("3/3").assertIsDisplayed()
            val thirdVisible = """(()=>{const f=document.querySelector('[data-ch="6"] iframe');if(!f?.contentDocument)return false;const p=[...f.contentDocument.querySelectorAll('p')].find(p=>p.textContent.includes('occurrence 16'));if(!p)return false;const r=p.getBoundingClientRect(),t=f.getBoundingClientRect().top;return t+r.bottom>0 && t+r.top<innerHeight})()"""
            try {
                compose.waitUntil(15_000) { js(s, thirdVisible) == "true" }
            } catch (failure: Throwable) {
                val geometry = js(s, """(()=>{const f=document.querySelector('[data-ch="6"] iframe'),d=f?.contentDocument,p=d&&[...d.querySelectorAll('p')].find(p=>p.textContent.includes('occurrence 16')),r=p?.getBoundingClientRect();return JSON.stringify({state:YH.state(),outerScroll:scrollY,innerHeight,frameTop:f?.getBoundingClientRect().top,frameHeight:f?.offsetHeight,frameScroll:d?.documentElement.scrollTop,paragraphTop:r?.top,paragraphBottom:r?.bottom,selection:d?.getSelection()?.toString()})})()""")
                throw AssertionError("Native ordinal 3/3 did not make third occurrence visible: $geometry", failure)
            }
            assertEquals("The third occurrence is visible", "true", js(s, thirdVisible))
            assertEquals(6, chapter(s))
            assertEquals("Find reload starts with target chapter alone", "[6]", frames(s).toString())
            val original = view(s)
            repeat(3) { nativeScroll(s, 1) }
            SystemClock.sleep(900)
            assertEquals("Find UI holds chapter additions", "[6]", frames(s).toString())
            compose.onNodeWithText("关闭").performClick()
            compose.waitUntil(10_000) { frames(s).length() > 1 }
            assertSame(original, view(s))
            bounded(s)
        }
    }

    @Test fun aaBookmarkDirectoryJumpAndDeletePersistInContinuousMode() {
        val id = runBlocking { fixtures.import(fixtures.txt()) }
        runBlocking { repo.savePosition(id, Locator(5, .4f), 12) }
        launch().use { s ->
            open(s, id)
            compose.onNodeWithText("Aa").performClick()
            compose.onNodeWithText("添加当前位置书签").performScrollTo().performClick()
            // Adding a bookmark dismisses Aa itself; Back here would leave the book.
            compose.waitUntil(10_000) { runBlocking { repo.observeBookmarks(id).first().size == 1 } }
            waitText("目录")
            val bookmark = runBlocking { repo.observeBookmarks(id).first().single() }
            assertEquals(5, bookmark.chapter)
            compose.onNodeWithText("目录").performClick()
            val beforeDirectoryJump = view(s)
            compose.onNode(hasText("第8章 Flow 8") and hasClickAction()).performScrollTo().performClick()
            compose.waitUntil(10_000) { chapter(s) == 7 }; ready(s, 7, beforeDirectoryJump)
            compose.onNodeWithText("目录").performClick()
            compose.onNodeWithText("书签 · 1").performClick()
            val beforeBookmarkJump = view(s)
            compose.onNodeWithText(bookmark.label).performClick()
            compose.waitUntil(10_000) { chapter(s) == 5 }; ready(s, 5, beforeBookmarkJump)
            assertEquals(bookmark.ratio, state(s).second, .04f)
            compose.onNodeWithText("目录").performClick()
            compose.onNodeWithText("书签 · 1").performClick()
            compose.onNodeWithText("删除").performClick()
            compose.waitUntil(10_000) { runBlocking { repo.observeBookmarks(id).first().isEmpty() } }
            compose.onNodeWithText("书签 · 0").assertIsDisplayed()
        }
    }

    /** Dispatch a native WebView touch; JS only locates the real book link. */
    private fun clickLink(s: ActivityScenario<MainActivity>, id: String) {
        val targetFrame = "[...document.querySelectorAll('iframe')].find(f=>f.contentDocument?.getElementById('$id'))"
        js(s, """(()=>{const f=$targetFrame;const a=f.contentDocument.getElementById('$id');window.scrollTo(0,f.parentElement.offsetTop+a.getBoundingClientRect().top-80)})()""")
        var point: JSONObject? = null
        var previous = ""
        compose.waitUntil(15_000) {
            val sample = string(s, """JSON.stringify((()=>{const f=$targetFrame,a=f?.contentDocument.getElementById('$id');if(!a)return {ready:false};const r=a.getBoundingClientRect(),t=f.getBoundingClientRect().top,x=r.left+r.width/2,y=t+r.top+r.height/2,hit=f.contentDocument.elementFromPoint(x,y-t);return {x,y,chapter:f.parentElement.dataset.ch,href:a.href,ready:x>=0&&y>=0&&x<innerWidth&&y<innerHeight&&document.elementFromPoint(x,y)===f&&(hit===a||a.contains(hit))}})())""")
            val parsed = JSONObject(sample)
            val stable = parsed.getBoolean("ready") && sample == previous
            previous = sample
            if (stable) point = parsed
            stable
        }
        val target = checkNotNull(point)
        android.util.Log.i("V11Flow", "Native link $id target=$target state=${state(s)} frames=${frames(s)}")
        val native = tapRenderedWebViewPoint(instrumentation, view(s), target.getDouble("x").toFloat(), target.getDouble("y").toFloat())
        android.util.Log.i("V11Flow", "One rendered native tap link=$id $native")
    }
    @Test fun epubNativeInternalLinkJumpsChapterAndAnchor() {
        val id = runBlocking { fixtures.import(fixtures.epub()) }
        runBlocking { repo.savePosition(id, Locator(4, .4f), 12) }
        launch().use { s ->
            open(s, id); js(s, "YH.hold(true)")
            clickLink(s, "cross-link")
            try {
                compose.waitUntil(15_000) { chapter(s) == 7 }
            } catch (failure: Throwable) {
                val snapshot = js(s, "JSON.stringify({outer:location.href,state:YH.state(),frames:[...document.querySelectorAll('iframe')].map(f=>({chapter:f.parentElement.dataset.ch,src:f.src,url:f.contentDocument?.location.href,scroll:f.contentDocument?.documentElement.scrollTop}))})")
                throw AssertionError("EPUB native link did not reach chapter 7: VM=${chapter(s)} geometry=$snapshot", failure)
            }
            ready(s, 7)
            assertEquals(7, state(s).first)
            val offset = js(s, "(()=>{const f=document.querySelector('[data-ch=\"7\"] iframe');return f.getBoundingClientRect().top+f.contentDocument.getElementById('p12').getBoundingClientRect().top})()").toDouble()
            assertEquals("Native anchor lands at outer viewport top", 0.0, offset, 4.0)
        }
    }
    @Test fun epubScriptsRefreshAndNativeTopLinkCannotEscapeFlowShell() {
        val id = runBlocking { fixtures.import(fixtures.epub(malicious = true)) }
        runBlocking { repo.savePosition(id, Locator(4, .4f), 12) }
        launch().use { s ->
            open(s, id); js(s, "YH.hold(true)")
            val original = view(s)
            val outer = string(s, "location.href")
            val shell = string(s, "document.getElementById('payload').textContent")
            clickLink(s, "top-link")
            SystemClock.sleep(1200)
            assertSame(original, view(s))
            assertEquals(outer, string(s, "location.href"))
            assertEquals(shell, string(s, "document.getElementById('payload').textContent"))
            assertEquals("Book", string(s, "document.title"))
            assertEquals("false", js(s, "document.body.hasAttribute('data-escaped')"))
            assertEquals("true", js(s, "[...document.querySelectorAll('iframe')].every(f=>!f.sandbox.contains('allow-scripts'))"))
            // Test-only parent sets an untrusted child navigation to the trusted shell route.
            js(s, "document.querySelector('iframe').src='/flow/index.html'")
            SystemClock.sleep(700)
            assertEquals("Child cannot load the trusted shell", "false", js(s, "!!document.querySelector('iframe').contentWindow.YH"))
            assertEquals(outer, string(s, "location.href"))
        }
    }

    @Test fun tocFastScrollerThresholdAndFilteredOriginalNumberDuringNativeHold() {
        launch().use { s ->
            val count = mutableIntStateOf(39)
            s.onActivity { activity ->
                activity.setContent {
                    YHTheme(ReaderTheme.PAPER) {
                        Box(Modifier.width(320.dp)) {
                            ChapterDrawerContent(rememberDrawerState(DrawerValue.Open), "Generated TOC", List(count.intValue) { i -> "${if (i % 2 == 0) "Keep" else "Other"} Chapter ${i + 1}" }, 4, {}, {}, {})
                        }
                    }
                }
            }
            compose.onNodeWithTag("TocFastScroller").assertDoesNotExist()
            compose.runOnUiThread { count.intValue = 40 }
            compose.onNodeWithTag("TocFastScroller").assertIsDisplayed().assertContentDescriptionEquals("快速滚动目录")
            compose.runOnUiThread { count.intValue = 100 }
            compose.onNode(hasSetTextAction()).performTextInput("Keep")
            Espresso.closeSoftKeyboard()
            val track = compose.onNodeWithTag("TocFastScroller").assertIsDisplayed()
            track.performTouchInput { down(Offset(center.x, height.toFloat() / 2)); advanceEventTime(100) }
            try {
                compose.waitForIdle()
                // 50 filtered rows: mid-track index round(24.5)=25 -> original chapter 51.
                compose.onNodeWithText("第 51 章").assertIsDisplayed()
                compose.onAllNodesWithText("Keep Chapter 51").assertCountEquals(2)
                exportV7Screenshot("v08-toc-fast-scroll.png")
                track.performTouchInput { moveTo(Offset(center.x, height.toFloat() - 2)); advanceEventTime(100) }
                compose.onNodeWithText("第 99 章").assertIsDisplayed()
            } finally { track.performTouchInput { up() } }
            compose.onNodeWithText("第 99 章").assertDoesNotExist()
        }
    }

    @Test fun generatedLongLineNoHeadingFindsTextInDefaultFlow() {
        val source = fixtures.file("txt")
        source.bufferedWriter().use { out ->
            repeat(12000) { line -> out.append("无标题混合行 $line ").append("山海来信 longline ".repeat(if (line == 6000) 2000 else 2)).append(if (line == 6000) "specialfallbackneedle" else "").append(if (line % 2 == 0) "\r\n" else "\n") }
        }
        assertFallbackFind(source, "specialfallbackneedle")
    }

    @Test @ExternalFixturePack fun privateT05LongMixedTextFindsMiddleRecordInFlow() {
        val authority = "com.example.yhviewer.v10.fixtures"
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.MANAGE_DOCUMENTS)
        try {
            assertTrue(context.contentResolver.call(android.net.Uri.parse("content://$authority"), "fixtureReset", null, null)!!.getBoolean("ready"))
        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
        val uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
            android.provider.DocumentsContract.buildTreeDocumentUri(authority, "external:"),
            "external:04_TXT/05_混合_大文件_12000行.txt",
        )
        val source = fixtures.file("txt")
        // Copy private provider input to an owned URI: never delete another test's import.
        context.contentResolver.openInputStream(uri)!!.use { input ->
            source.outputStream().buffered().use { input.copyTo(it) }
        }
        assertFallbackFind(source, "记录 06000")
    }

    private fun assertFallbackFind(source: java.io.File, needle: String) {
        val id = runBlocking { fixtures.import(source) }
        val indexed = runBlocking { repo.open(id) } as OpenedDoc.Txt
        assertTrue("Long no-heading TXT uses legacy8000-character fallback parts", indexed.chapterCount > 1)
        indexed.titles.forEachIndexed { i, title -> assertEquals("第 ${i + 1} 部分", title) }
        launch().use { s ->
            open(s, id)
            var vm: ShelfViewModel? = null; model(s) { vm = it }
            val hit = runBlocking { checkNotNull(vm).searchBook(needle) }.single()
            assertTrue("Marker exercises a middle fallback part", hit.chapter > 0 && hit.chapter < indexed.chapterCount - 1)
            model(s) { it.openHit(hit, needle, 0) }
            ready(s, expectedStart = hit.chapter)
            waitText("1/1")
            assertEquals(hit.chapter, chapter(s))
            assertEquals("Target fallback part is the only find frame", "[${hit.chapter}]", frames(s).toString())
            bounded(s)
        }
    }
}
