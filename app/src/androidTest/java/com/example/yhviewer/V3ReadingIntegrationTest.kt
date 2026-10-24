package com.example.yhviewer

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
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
import kotlin.math.abs
import kotlin.math.roundToInt

/** Run only on the controller's disposable emulator; never on a user's populated device. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class V3ReadingIntegrationTest {
    private lateinit var legacyChapterMode: LegacyChapterMode
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefs get() = context.getSharedPreferences("reader_prefs", 0)
    private var oldPrefs: Map<String, *> = emptyMap<String, Any>()
    private val created = mutableListOf<Uri>()

    @Before fun prepare() {
        legacyChapterMode = LegacyChapterMode(context)
        oldPrefs = prefs.all.toMap()
        ReadingPrefsStore(context).save(ReadingPrefs(continuous = false))
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
        listOf("read_scale", "read_line", "read_margin", "read_font", "read_tap_turn", "read_keep_on").forEach { key ->
            when (val value = oldPrefs[key]) {
                is Int -> edit.putInt(key, value)
                is Float -> edit.putFloat(key, value)
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                else -> edit.remove(key)
            }
        }
        assertTrue(edit.commit())
    }
    private fun document(): Uri {
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "v3-reading-${System.nanoTime()}.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/YHViewerV3Tests")
        })!!
        val text = (1..3).joinToString("\n") { chapter ->
            "第${chapter}章 阅读\n" + (1..400).joinToString("\n") { "阅读段落 $chapter / $it。正文应该保持相同的阅读位置，并且支持选字和翻页。" }
        }
        context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
        created += uri
        return uri
    }
    private fun launch() = ActivityScenario.launch<MainActivity>(Intent(Intent.ACTION_VIEW)
        .setClass(context, MainActivity::class.java).setDataAndType(document(), "text/plain")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    private fun web(v: View): WebView? {
        if (v is WebView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) web(v.getChildAt(i))?.let { return it }
        return null
    }
    private fun js(s: ActivityScenario<MainActivity>, expression: String): String {
        val done = CountDownLatch(1)
        var result = "null"
        s.onActivity { a -> web(a.window.decorView)?.evaluateJavascript(expression) { result = it; done.countDown() } ?: done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        return result
    }
    private fun readyView(s: ActivityScenario<MainActivity>): WebView {
        var ready: WebView? = null
        compose.waitUntil(25_000) {
            val done = CountDownLatch(1)
            s.onActivity { a ->
                val model = ViewModelProvider(a)[ShelfViewModel::class.java]
                val chapter = model.chapter
                val view = web(a.window.decorView)
                if (view == null || view.contentHeight <= 1000 || view.width <= 0) done.countDown()
                else {
                    val title = MarkdownRenderer.toJsonString("第${chapter + 1}章 阅读")
                    view.evaluateJavascript("!!window.setReading && document.readyState === 'complete' && document.querySelectorAll('p').length > 50 && document.getElementById('title').textContent === $title") { value ->
                        if (value == "true" && web(a.window.decorView) === view && model.chapter == chapter) ready = view
                        done.countDown()
                    }
                }
            }
            assertTrue("current chapter DOM readiness callback", done.await(5, TimeUnit.SECONDS))
            ready != null
        }
        return ready!!
    }

    private fun ready(s: ActivityScenario<MainActivity>) {
        readyView(s)
        // Initial layout restores only after three stable height samples.
        instrumentation.waitForIdleSync()
        compose.waitUntil(10_000) {
            scroll(s, .01f)
            var reporting = false
            s.onActivity { a -> reporting = ViewModelProvider(a)[ShelfViewModel::class.java].initialRatio() > .005f }
            reporting
        }
        scroll(s, 0f)
    }
    private fun tap(s: ActivityScenario<MainActivity>, x: Float, double: Boolean = false, hold: Boolean = false) {
        readyView(s)
        val duration = if (hold) ViewConfiguration.getLongPressTimeout() + 100L else 30L
        repeat(if (double) 2 else 1) {
            if (!hold) {
                // One UI task with synthetic event times: a slow emulator can no longer stretch a
                // 30 ms tap past the long-press timeout between two separate onActivity calls.
                s.onActivity { a -> web(a.window.decorView)!!.let { w ->
                    val start = android.os.SystemClock.uptimeMillis()
                    val down = MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, w.width*x, w.height*.5f, 0)
                    w.dispatchTouchEvent(down); down.recycle()
                    val up = MotionEvent.obtain(start, start + duration, MotionEvent.ACTION_UP, w.width*x, w.height*.5f, 0)
                    w.dispatchTouchEvent(up); up.recycle()
                } }
                if (double) Thread.sleep(50)
                return@repeat
            }
            s.onActivity { a -> web(a.window.decorView)!!.let { w ->
                val start = android.os.SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, w.width*x, w.height*.5f, 0)
                w.dispatchTouchEvent(down); down.recycle()
            } }
            Thread.sleep(duration)
            s.onActivity { a -> web(a.window.decorView)!!.let { w ->
                val now = android.os.SystemClock.uptimeMillis()
                val up = MotionEvent.obtain(now-duration, now, MotionEvent.ACTION_UP, w.width*x, w.height*.5f, 0)
                w.dispatchTouchEvent(up); up.recycle()
            } }
            if (double) Thread.sleep(50)
        }
        Thread.sleep(ViewConfiguration.getDoubleTapTimeout() + 200L)
    }
    /** Wait for the toolbar toggle to settle (exit/enter animations included) before asserting on it. */
    private fun barsShown(shown: Boolean) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText("Aa").fetchSemanticsNodes().isNotEmpty() == shown
    }
    private fun quickRightTaps(s: ActivityScenario<MainActivity>, count: Int) {
        readyView(s)
        repeat(count) {
            s.onActivity { a -> web(a.window.decorView)!!.let { target ->
                val start = android.os.SystemClock.uptimeMillis()
                val x = target.width * .9f
                val y = target.height * .5f
                val down = MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, x, y, 0)
                target.dispatchTouchEvent(down); down.recycle()
                val upTime = android.os.SystemClock.uptimeMillis()
                val up = MotionEvent.obtain(start, upTime, MotionEvent.ACTION_UP, x, y, 0)
                target.dispatchTouchEvent(up); up.recycle()
            } }
            if (it != count - 1) Thread.sleep(20)
        }
    }
    private fun longPressText(s: ActivityScenario<MainActivity>) {
        val point = org.json.JSONObject(js(s, """(()=>{const p=[...document.querySelectorAll('p')].find(e=>{const r=e.getBoundingClientRect();return r.width>100&&r.height>16&&r.top>40&&r.top<innerHeight-80});if(!p)return null;const r=p.getBoundingClientRect();return JSON.stringify({x:r.left+Math.min(24,r.width/3),y:r.top+Math.min(10,r.height/2)})})()""").let { org.json.JSONTokener(it).nextValue().toString() })
        readyView(s)
        var x = 0f
        var y = 0f
        s.onActivity { a -> web(a.window.decorView)!!.let { target ->
            @Suppress("DEPRECATION") val scale = target.scale
            x = point.getDouble("x").toFloat()*scale
            y = point.getDouble("y").toFloat()*scale
            val downAt = android.os.SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(downAt, downAt, MotionEvent.ACTION_DOWN, x, y, 0)
            target.dispatchTouchEvent(down); down.recycle()
        } }
        Thread.sleep(ViewConfiguration.getLongPressTimeout()+250L)
        s.onActivity { a -> web(a.window.decorView)!!.let { target ->
            val now = android.os.SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(now-ViewConfiguration.getLongPressTimeout()-250L, now, MotionEvent.ACTION_UP, x, y, 0)
            target.dispatchTouchEvent(up); up.recycle()
        } }
        Thread.sleep(150)
    }
    private fun scroll(s: ActivityScenario<MainActivity>, ratio: Float) {
        val view = readyView(s)
        s.onActivity { a ->
            assertSame("scroll current ready chapter view", view, web(a.window.decorView))
            @Suppress("DEPRECATION") val range = (view.contentHeight * view.scale).roundToInt() - view.height
            view.scrollTo(0, (range * ratio).roundToInt())
        }
    }

    @Test fun centerTapKeepsViewportAndRatioAndReadingStyleKeepsSameWebView() {
        launch().use { s ->
            ready(s)
            scroll(s, .4f)
            var original: WebView? = null
            var height = 0
            s.onActivity { a -> original=web(a.window.decorView); height=original!!.height }
            tap(s, .5f)
            barsShown(false)
            s.onActivity { a -> assertEquals(height, web(a.window.decorView)!!.height); assertEquals(.4f, web(a.window.decorView)!!.scrollRatio(), .025f) }
            tap(s, .5f)
            barsShown(true)
            compose.onNodeWithText("Aa").performClick()
            compose.onNodeWithText("主题").assertIsDisplayed()
            // Font chips have a nearer horizontal scroll parent; scroll the outer
            // vertical settings column via a sibling outside that row first.
            compose.onNodeWithText("阅读时屏幕常亮").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("无衬线").performScrollTo().assertIsDisplayed().performClick()
            androidx.test.espresso.Espresso.pressBack()
            compose.waitUntil(10_000) { js(s, "document.getElementById('reading').textContent.includes('--font-sans')") == "true" }
            s.onActivity { a -> assertSame(original, web(a.window.decorView)); assertEquals(.4f, web(a.window.decorView)!!.scrollRatio(), .025f) }
        }
    }

    @Test fun tapPagesImmediatelyRapidTapsAccumulateAndLongPressStillSelects() {
        launch().use { s ->
            ready(s)
            scroll(s, 0f)
            var startY = 0
            s.onActivity { a -> startY = web(a.window.decorView)!!.scrollY }
            quickRightTaps(s, 1)
            compose.waitUntil(50) {
                var moved = false
                s.onActivity { a -> moved = web(a.window.decorView)!!.scrollY != startY }
                moved
            }
            compose.waitUntil(5_000) {
                var settled = false
                s.onActivity { a -> val w = web(a.window.decorView)!!; settled = kotlin.math.abs(w.scrollY - w.height*.9f) < 3 }
                settled
            }
            scroll(s, 0f)
            val pageHeight = readyView(s).height
            quickRightTaps(s, 3)
            Thread.sleep(500)
            var actualY = 0
            var maxY = 0
            s.onActivity { a -> val w = web(a.window.decorView)!!; actualY = w.scrollY; @Suppress("DEPRECATION") val contentHeight = (w.contentHeight*w.scale).roundToInt(); maxY = (contentHeight-w.height).coerceAtLeast(0) }
            assertEquals("three quick right taps should accumulate 3 x 0.9 screen heights (max=$maxY)", pageHeight*2.7f, actualY.toFloat(), 2f)
            scroll(s, 0f)
            longPressText(s)
            assertTrue("long press continues to select text", js(s, "String(window.getSelection()).length").toInt() > 0)
            js(s, "window.getSelection().removeAllRanges()")
            s.onActivity { a -> ViewModelProvider(a)[ShelfViewModel::class.java].goToChapter(1) }
            ready(s)
            tap(s, .1f)
            compose.waitUntil(10_000) { var end=false; s.onActivity { a ->
                end=ViewModelProvider(a)[ShelfViewModel::class.java].chapter==0 && (web(a.window.decorView)?.scrollRatio() ?: 0f)>.97f
            }; end }
            tap(s, .9f)
            compose.waitUntil(10_000) {
                var next = false
                s.onActivity { a -> next = ViewModelProvider(a)[ShelfViewModel::class.java].chapter == 1 }
                next
            }
            s.onActivity { a -> assertEquals("chapter-end tap advances exactly one chapter", 1, ViewModelProvider(a)[ShelfViewModel::class.java].chapter) }
        }
    }

    @Test fun keepAwakeAndTapPagingSettingsPersistAndRestoreHostView() {
        launch().use { s ->
            ready(s)
            var originalKeep = false
            s.onActivity { originalKeep=it.window.decorView.keepScreenOn }
            s.onActivity { a -> ViewModelProvider(a)[ShelfViewModel::class.java].updateReading(ReadingPrefs(tapToTurn=false, keepScreenOn=true, continuous=false)) }
            compose.waitForIdle()
            scroll(s, .3f)
            tap(s, .9f)
            barsShown(false) // tap paging is off, so a side tap only toggles the toolbar
            s.onActivity { a -> assertEquals(.3f, web(a.window.decorView)!!.scrollRatio(), .025f) }
            tap(s, .5f)
            barsShown(true)
            compose.onNodeWithText("‹ 书架").performClick()
            compose.waitForIdle()
            s.onActivity { a -> assertEquals(originalKeep, a.window.decorView.keepScreenOn) }
            assertFalse(ReadingPrefsStore(context).load().tapToTurn)
            assertTrue(ReadingPrefsStore(context).load().keepScreenOn)
        }
    }
}
