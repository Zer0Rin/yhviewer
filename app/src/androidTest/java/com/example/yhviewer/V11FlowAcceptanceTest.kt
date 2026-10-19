package com.example.yhviewer

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
        compose.waitUntil(25_000) { runCatching { (previousView == null || view(s) !== previousView) &&
            (expectedStart == null || js(s, "JSON.parse(document.getElementById('payload').textContent).start") == expectedStart.toString()) && js(s, "!!window.YH && YH.state()!=='' && [...document.querySelectorAll('iframe')].every(f=>f.contentDocument && f.contentDocument.readyState==='complete' && f.offsetHeight>0)") == "true" }.getOrDefault(false) }
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

    @Test fun continuousIsTheDefault() { assertTrue(ReadingPrefs().continuous) }
    @Test fun idleFlowPollingAllowsAutomaticDebouncedPositionSave() {
        val id = runBlocking { fixtures.import(fixtures.txt()) }
        runBlocking { repo.savePosition(id, Locator(5, .4f), 12) }
        launch().use { s ->
            open(s, id)
            scrollUntilChapter(s, 6, 1)
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
            assertSame(original, view(s))
            assertEquals(5, chapter(s))
            assertEquals("terminal", string(s, "document.documentElement.dataset.theme"))
            assertEquals(before.getString("text"), anchor(s).getString("text"))
            model(s) { assertEquals(ReadingFont.SANS, it.reading.font) }
            compose.onNodeWithText("Aa").performClick()
            compose.onNodeWithContentDescription("字号").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(120f) }
            Espresso.pressBack()
            ready(s)
            assertSame(original, view(s))
            assertEquals(5, chapter(s))
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
    private fun find(epub: Boolean) {
        val id = runBlocking { fixtures.import(if (epub) fixtures.epub() else fixtures.txt()) }
        launch().use { s ->
            open(s, id)
            compose.onNodeWithText("搜索").performClick()
            compose.onNodeWithContentDescription("查找内容").performTextInput(V11GeneratedFixtures.NEEDLE)
            compose.waitUntil(15_000) { compose.onAllNodesWithText("无结果").fetchSemanticsNodes().isEmpty() && compose.onAllNodesWithText("1/3").fetchSemanticsNodes().isNotEmpty() }
            // Exercise real full-book search control, then select an actual repo search hit via VM.
            compose.onNodeWithText("全书").performClick()
            waitText("「${V11GeneratedFixtures.NEEDLE}」共 36 处")
            Espresso.pressBack()
            var vm: ShelfViewModel? = null; model(s) { vm = it }
            val hits = runBlocking { checkNotNull(vm).searchBook(V11GeneratedFixtures.NEEDLE) }
            val hit = hits.filter { it.chapter == 6 }[2]
            model(s) { it.openHit(hit, V11GeneratedFixtures.NEEDLE, 2) }
            ready(s)
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
            ready(s)
            waitText("1/1")
            assertEquals(hit.chapter, chapter(s))
            assertEquals("Target fallback part is the only find frame", "[${hit.chapter}]", frames(s).toString())
            bounded(s)
        }
    }
}
