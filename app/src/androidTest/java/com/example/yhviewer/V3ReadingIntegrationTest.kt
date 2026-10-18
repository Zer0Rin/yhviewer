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
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefs get() = context.getSharedPreferences("reader_prefs", 0)
    private var oldPrefs: Map<String, *> = emptyMap<String, Any>()
    private val created = mutableListOf<Uri>()

    @Before fun prepare() {
        oldPrefs = prefs.all.toMap()
        ReadingPrefsStore(context).save(ReadingPrefs())
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
            "第${chapter}章 阅读\n" + (1..100).joinToString("\n") { "阅读段落 $chapter / $it。正文应该保持相同的阅读位置，并且支持选字和翻页。" }
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
    private fun ready(s: ActivityScenario<MainActivity>) {
        compose.waitUntil(25_000) { js(s, "!!window.setReading && document.querySelectorAll('p').length>50") == "true" }
        compose.waitUntil(10_000) { var yes = false; s.onActivity { a -> yes = web(a.window.decorView)!!.contentHeight > 1000 }; yes }
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
        val duration = if (hold) ViewConfiguration.getLongPressTimeout() + 100L else 30L
        repeat(if (double) 2 else 1) {
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
    private fun scroll(s: ActivityScenario<MainActivity>, ratio: Float) = s.onActivity { a -> web(a.window.decorView)!!.let { w ->
        @Suppress("DEPRECATION") val range = (w.contentHeight*w.scale).roundToInt()-w.height
        w.scrollTo(0, (range*ratio).roundToInt())
    } }

    @Test fun centerTapKeepsViewportAndRatioAndReadingStyleKeepsSameWebView() {
        launch().use { s ->
            ready(s)
            scroll(s, .4f)
            var original: WebView? = null
            var height = 0
            s.onActivity { a -> original=web(a.window.decorView); height=original!!.height }
            tap(s, .5f)
            compose.onNodeWithText("Aa").assertDoesNotExist()
            s.onActivity { a -> assertEquals(height, web(a.window.decorView)!!.height); assertEquals(.4f, web(a.window.decorView)!!.scrollRatio(), .025f) }
            tap(s, .5f)
            compose.onNodeWithText("Aa").performClick()
            compose.onNodeWithText("主题").assertIsDisplayed()
            compose.onNodeWithText("无衬线").performScrollTo().assertIsDisplayed().performClick()
            androidx.test.espresso.Espresso.pressBack()
            compose.waitUntil(10_000) { js(s, "document.getElementById('reading').textContent.includes('--font-sans')") == "true" }
            s.onActivity { a -> assertSame(original, web(a.window.decorView)); assertEquals(.4f, web(a.window.decorView)!!.scrollRatio(), .025f) }
        }
    }

    @Test fun confirmedTapPagesButDoubleTapAndLongPressDoNotAndPreviousStartsAtEnd() {
        launch().use { s ->
            ready(s)
            scroll(s, 0f)
            tap(s, .9f)
            s.onActivity { a -> web(a.window.decorView)!!.let { assertEquals(it.height*.9f, it.scrollY.toFloat(), 3f) } }
            scroll(s, 0f)
            tap(s, .9f, double=true)
            s.onActivity { a -> assertEquals(0, web(a.window.decorView)!!.scrollY) }
            tap(s, .9f, hold=true)
            s.onActivity { a -> assertEquals(0, web(a.window.decorView)!!.scrollY) }
            js(s, "window.getSelection().removeAllRanges()")
            s.onActivity { a -> ViewModelProvider(a)[ShelfViewModel::class.java].goToChapter(1) }
            ready(s)
            tap(s, .1f)
            compose.waitUntil(10_000) { var end=false; s.onActivity { a ->
                end=ViewModelProvider(a)[ShelfViewModel::class.java].chapter==0 && web(a.window.decorView)!!.scrollRatio()>.97f
            }; end }
        }
    }

    @Test fun keepAwakeAndTapPagingSettingsPersistAndRestoreHostView() {
        launch().use { s ->
            ready(s)
            var originalKeep = false
            s.onActivity { originalKeep=it.window.decorView.keepScreenOn }
            s.onActivity { a -> ViewModelProvider(a)[ShelfViewModel::class.java].updateReading(ReadingPrefs(tapToTurn=false, keepScreenOn=true)) }
            compose.waitForIdle()
            scroll(s, .3f)
            tap(s, .9f)
            s.onActivity { a -> assertEquals(.3f, web(a.window.decorView)!!.scrollRatio(), .025f) }
            tap(s, .5f)
            compose.onNodeWithText("‹ 书架").performClick()
            compose.waitForIdle()
            s.onActivity { a -> assertEquals(originalKeep, a.window.decorView.keepScreenOn) }
            assertFalse(ReadingPrefsStore(context).load().tapToTurn)
            assertTrue(ReadingPrefsStore(context).load().keepScreenOn)
        }
    }
}
