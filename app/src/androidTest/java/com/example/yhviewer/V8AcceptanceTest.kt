package com.example.yhviewer

import android.content.res.Configuration
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Native v8 acceptance with generated content only; captures are explicitly opt-in.
 *
 * Use -e v7Screenshots true on an empty emulator shelf to export the six README images.
 * Normal runs may coexist with existing records: teardown removes only this test's imports.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class V8AcceptanceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val db get() = AppDatabase.get(context)
    private val repo get() = ShelfRepository(context, db)
    private val uris = mutableListOf<Uri>()
    private val groups = mutableListOf<Long>()
    private val ids = mutableListOf<Long>()
    private var oldTheme: String? = null
    private var oldGrid: Boolean? = null
    private var preferencesRemembered = false
    private var chinese = 0L
    private var crimson = 0L
    private var markdown = 0L
    private var bookGroupName = ""
    private val bookTitles = listOf("1-1432", "星河旅人", "night train", "山海来信")

    @Before fun syntheticShelf() {
        val readerPrefs = context.getSharedPreferences("reader_prefs", 0)
        val shelfPrefs = context.getSharedPreferences("shelf_prefs", 0)
        oldTheme = readerPrefs.getString("md_theme", null)
        oldGrid = if (shelfPrefs.contains("books_grid")) shelfPrefs.getBoolean("books_grid", true) else null
        preferencesRemembered = true
        // A screenshot run must never accidentally publish existing user content.
        if (InstrumentationRegistry.getArguments().getString("v7Screenshots") == "true") runBlocking {
            check(db.bookDao().allIds().isEmpty() && repo.observeGroups().first().isEmpty()) {
                "Screenshot acceptance requires an empty emulator shelf; existing content was preserved"
            }
        }
        assertTrue(readerPrefs.edit().putString("md_theme", "paper").commit())
        assertTrue(shelfPrefs.edit().putBoolean("books_grid", true).commit())
        runBlocking {
            crimson = import("1-1432.epub", "application/epub+zip", epub("1-1432", "M. Rowan", 0xFF5D6773.toInt()))
            import("星河旅人.epub", "application/epub+zip", epub("星河旅人", "林知远", 0xFFB36C4B.toInt()))
            import("night-train.txt", "text/plain", chapters(false).toByteArray())
            chinese = import("山海来信.txt", "text/plain", chapters(true).toByteArray())
            markdown = import("field-notes.md", "text/markdown", """
                # 山海间的阅读笔记

                纸张主题让书架、阅读顶栏与正文共用一张温和的底色。

                ## 在旅途中

                - 第一站：清晨的港口
                - 第二站：山间的小屋

                > 记下读过的风景，也记下尚未走过的路。

                | 章节 | 记录 |
                | --- | --- |
                | Chapter 1 | Crimson |

                ```kotlin
                val journey = "山海来信"
                println(journey)
                ```
            """.trimIndent().toByteArray())
            import("hello.html", "text/html", "<h1>离线小札</h1><p>所有内容均为测试生成。</p>".toByteArray())
            val relaxed = import("reading-log.html", "text/html", "<h1>阅读日志</h1><p>本地交互示例，无外部请求。</p>".toByteArray())
            repo.setPermissions(relaxed, DocPermissions.STANDARD.copy(scriptedNavigation = true))
            // MediaStore may append .txt to a .kt display name under text/plain.
            // The generic binary MIME preserves the supplied code-file extension.
            val kotlin = import("Journey.kt", "application/octet-stream", "fun main() { println(\"Hello, reader\") }".toByteArray())
            val kotlinBook = checkNotNull(db.bookDao().get(kotlin))
            assertEquals("Kotlin fixture must retain its actual provider filename", "Journey.kt", kotlinBook.name)
            assertEquals("Kotlin fixture must import as code rather than a third TXT book", DocType.CODE, kotlinBook.type)
            import("reading-plan.json", "application/json", "{\"title\":\"山海来信\",\"chapters\":25}".toByteArray())
            val existingGroups = repo.observeGroups().first()
            val suffix = if (existingGroups.any { it.name in listOf("旅途书单", "阅读笔记") }) " ${System.nanoTime().toString().takeLast(5)}" else ""
            bookGroupName = "旅途书单$suffix"
            groups += repo.createGroup(bookGroupName)
            groups += repo.createGroup("阅读笔记$suffix")
            repo.moveToGroup(ids.take(4), groups[0])
            repo.moveToGroup(listOf(markdown, relaxed), groups[1])
            repo.savePosition(chinese, Locator(12, .5f), 25)
            // Explicit times avoid same-millisecond ordering ties between imports.
            val now = System.currentTimeMillis()
            ids.forEachIndexed { index, id -> db.bookDao().touch(id, now - (ids.size - index) * 1_000) }
            db.bookDao().touch(chinese, now)
        }
    }

    @After fun restoreOwnedFixtures() {
        try {
            runBlocking {
                // Resolve by source too, so a failed import after committing its row is cleaned up.
                uris.forEach { uri ->
                    db.bookDao().findBySource(uri.toString())?.let { repo.delete(it.id) }
                }
                groups.forEach { repo.deleteGroup(it) }
            }
        } finally {
            uris.forEach { context.contentResolver.delete(it, null, null) }
            if (preferencesRemembered) {
                val reader = context.getSharedPreferences("reader_prefs", 0).edit()
                oldTheme?.let { reader.putString("md_theme", it) } ?: reader.remove("md_theme")
                assertTrue(reader.commit())
                val shelf = context.getSharedPreferences("shelf_prefs", 0).edit()
                oldGrid?.let { shelf.putBoolean("books_grid", it) } ?: shelf.remove("books_grid")
                assertTrue(shelf.commit())
            }
        }
    }

    private suspend fun import(name: String, mime: String, bytes: ByteArray): Long {
        val uri = checkNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/YHViewerV8Tests")
        }))
        uris += uri
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        return repo.import(uri).also { ids += it }
    }

    private fun launch() = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
    private fun waitText(text: String) = compose.waitUntil(25_000) {
        compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    private fun vm(scenario: ActivityScenario<MainActivity>, action: (ShelfViewModel) -> Unit) {
        scenario.onActivity { action(ViewModelProvider(it)[ShelfViewModel::class.java]) }
        compose.waitForIdle()
    }
    private fun open(scenario: ActivityScenario<MainActivity>, id: Long) {
        vm(scenario) { it.openBook(id) }
        compose.waitUntil(25_000) {
            var ready = false
            scenario.onActivity { ready = (ViewModelProvider(it)[ShelfViewModel::class.java].reader as? ReaderState.Ready)?.doc?.bookId == id }
            ready
        }
        compose.waitForIdle()
    }
    // The closed TOC remains composed offscreen with duplicate chapter action labels.
    private fun chapterAction(label: String) = compose.onNode(
        hasText(label) and hasAnyAncestor(hasTestTag("BookReaderBackground")),
    )
    private fun capture(name: String) { compose.waitForIdle(); exportV7Screenshot(name) }
    private fun shelfColor(expected: ReaderPalette) {
        compose.waitUntil(10_000) {
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            pixels[1, pixels.height / 2] == Color(expected.bg)
        }
    }
    private fun assertBars(scenario: ActivityScenario<MainActivity>, darkIcons: Boolean) {
        compose.waitUntil(10_000) {
            var correct = false
            scenario.onActivity {
                val controller = WindowCompat.getInsetsController(it.window, it.window.decorView)
                correct = controller.isAppearanceLightStatusBars == darkIcons && controller.isAppearanceLightNavigationBars == darkIcons
                assertFalse("Navigation contrast must not add a white scrim", it.window.isNavigationBarContrastEnforced)
            }
            correct
        }
    }

    @Test fun dotProgressExposesItsCurrentAccessibleRange() {
        launch().use { scenario ->
            val fraction = mutableStateOf(.5f)
            scenario.onActivity { activity ->
                activity.setContent { YHTheme(ReaderTheme.PAPER) { DotProgress(fraction.value, Modifier.testTag("AccessibleProgress")) } }
            }
            fun assertProgress(value: Float) = compose.onNodeWithTag("AccessibleProgress").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(value, 0f..1f)),
            )
            assertProgress(.5f)
            compose.runOnUiThread { fraction.value = .75f }
            assertProgress(.75f)
        }
    }

    @Test fun themeChangeInsideBookRestoresCurrentSystemBarIconsOnShelf() {
        launch().use { scenario ->
            waitText("书架")
            for (theme in listOf(ReaderTheme.INK, ReaderTheme.PAPER)) {
                open(scenario, crimson)
                waitText("目录")
                vm(scenario) { it.selectTheme(theme) }
                assertBars(scenario, darkIcons = theme == ReaderTheme.PAPER)
                // Exercise both immersive disposal and the return to the shell palette.
                compose.onNodeWithTag("BookReaderBackground").performTouchInput { click(center) }
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Aa").fetchSemanticsNodes().isEmpty() }
                vm(scenario) { it.backToShelf() }
                waitText("书架")
                shelfColor(if (theme == ReaderTheme.PAPER) ReaderPalette.PAPER else ReaderPalette.INK)
                assertBars(scenario, darkIcons = theme == ReaderTheme.PAPER)
            }
        }
    }

    @Test fun autoShelfFollowsSystemNightModeAndRestoresSystemSetting() {
        fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText() }
        }
        val originalNight = shell("cmd uimode night").substringAfter(':').trim()
        check(originalNight in setOf("yes", "no", "auto", "custom_schedule", "custom_bedtime")) {
            "Cannot safely restore unknown system night mode: $originalNight"
        }
        try {
            shell("cmd uimode night no")
            launch().use { scenario ->
                waitText("书架")
                vm(scenario) { it.selectTheme(ReaderTheme.AUTO) }
                shelfColor(ReaderPalette.PAPER)
                assertBars(scenario, darkIcons = true)
                for ((nightMode, nightMask, palette) in listOf(
                    Triple("yes", Configuration.UI_MODE_NIGHT_YES, ReaderPalette.INK),
                    Triple("no", Configuration.UI_MODE_NIGHT_NO, ReaderPalette.PAPER),
                )) {
                    var previous: MainActivity? = null
                    scenario.onActivity { previous = it }
                    shell("cmd uimode night $nightMode")
                    // The shell returns before recreation; inspecting the old activity can pass
                    // against its old palette while a new activity is still loading.
                    compose.waitUntil(25_000) {
                        var changed = false
                        scenario.onActivity {
                            changed = it !== previous &&
                                (it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == nightMask
                        }
                        changed
                    }
                    waitText("书架")
                    scenario.onActivity { assertEquals(ReaderTheme.AUTO, ViewModelProvider(it)[ShelfViewModel::class.java].theme) }
                    shelfColor(palette)
                    assertBars(scenario, darkIcons = !palette.dark)
                }
            }
        } finally {
            shell("cmd uimode night $originalNight")
        }
    }

    /** Run this selector in each navigation mode; the harness owns mode switching. */
    @Test fun navigationBarBackgroundMatchesThemeInCurrentNavigationMode() {
        fun assertNativeBottomBackground(palette: ReaderPalette) {
            compose.waitForIdle()
            // System-bar/inset animations are outside Compose's idling resource. Sample the
            // native screenshot, including system bars, rather than the Compose content root.
            compose.waitUntil(15_000) {
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                    ?: error("Unable to capture native navigation background")
                try {
                    bitmap.getPixel(1, bitmap.height - 2) == Color(palette.bg).toArgb()
                } finally {
                    bitmap.recycle()
                }
            }
        }
        launch().use { scenario ->
            waitText("书架")
            for ((theme, palette) in listOf(
                ReaderTheme.PAPER to ReaderPalette.PAPER,
                ReaderTheme.INK to ReaderPalette.INK,
                ReaderTheme.TERMINAL to ReaderPalette.TERMINAL,
            )) {
                vm(scenario) { it.selectTheme(theme) }
                shelfColor(palette)
                assertBars(scenario, darkIcons = !palette.dark)
                assertNativeBottomBackground(palette)
                open(scenario, markdown)
                waitText("field-notes.md")
                compose.waitUntil(25_000) {
                    js(scenario, "document.readyState==='complete' && !!document.querySelector('#content h1') && document.documentElement.dataset.theme === '${theme.id}'") == "true"
                }
                assertBars(scenario, darkIcons = !palette.dark)
                assertNativeBottomBackground(palette)
                vm(scenario) { it.backToShelf() }
                waitText("书架")
            }
        }
    }

    @Test fun generatedShelfCapturesPaperInkAndThreeSelectedItems() {
        launch().use { scenario ->
            waitText("继续阅读 ›")
            compose.onNodeWithText("读到 50%").assertIsDisplayed()
            compose.onNodeWithText("封面").assertIsSelected()
            assertEquals(9, ids.size)
            runBlocking {
                assertEquals(2, db.bookDao().getAll(ids).count { it.type == DocType.EPUB && repo.covers.exists(it.id) })
                assertEquals(2, db.bookDao().getAll(ids).count { it.type == DocType.TXT })
            }
            shelfColor(ReaderPalette.PAPER)
            capture("shelf.png")
            vm(scenario) { it.selectTheme(ReaderTheme.INK) }
            shelfColor(ReaderPalette.INK)
            capture("shelf-ink.png")
            compose.onNode(hasText("山海来信") and hasClickAction()).performTouchInput { longClick() }
            waitText("已选 1 项")
            // The other three books occupy one row below the continue-reading hero.
            for (title in bookTitles.take(2)) {
                compose.onNode(hasText(title) and hasClickAction()).performScrollTo().performClick()
            }
            waitText("已选 3 项")
            compose.onNodeWithText("移到分组").assertIsDisplayed()
            compose.onNodeWithText("删除").assertIsDisplayed()
            capture("v4-selection.png")
            scenario.recreate()
            waitText("已选 3 项")
        }
    }

    @Test fun gridUsesThreeEqualColumnsAndPersistsAcrossNewActivity() {
        // Without a hero, four books expose a partial second row.
        runBlocking { repo.savePosition(chinese, Locator.START, 25) }
        launch().use {
            waitText("书架")
            compose.onNodeWithContentDescription("$bookGroupName，4 项").performClick()
            waitText("封面")
            compose.onNodeWithText("封面").assertIsSelected()
            val firstRow = listOf("山海来信", "night train", "星河旅人")
            val bounds = firstRow.map { title ->
                compose.onNode(hasText(title) and hasClickAction()).performScrollTo().fetchSemanticsNode().boundsInRoot
            }
            assertEquals(bounds[0].width, bounds[1].width, 1f)
            assertEquals(bounds[1].width, bounds[2].width, 1f)
            assertEquals(bounds[0].top, bounds[1].top, 1f)
            assertEquals(bounds[1].top, bounds[2].top, 1f)
            assertTrue(bounds[0].left < bounds[1].left && bounds[1].left < bounds[2].left)
            val last = compose.onNode(hasText("1-1432") and hasClickAction()).performScrollTo().fetchSemanticsNode().boundsInRoot
            assertEquals("Partial row must retain column width", bounds[0].width, last.width, 1f)
            compose.onNodeWithText("列表").performScrollTo().performClick()
            compose.onNodeWithText("列表").assertIsSelected()
        }
        // Closing and launching replaces both the Activity and its ViewModel, unlike recreate().
        launch().use {
            waitText("列表")
            compose.onNodeWithText("列表").assertIsSelected()
            compose.onNodeWithText("封面").performClick()
        }
        launch().use {
            waitText("封面")
            compose.onNodeWithText("封面").assertIsSelected()
        }
    }

    @Test fun chineseTocAndTerminalReaderKeepChapterCapsuleVisible() {
        launch().use { scenario ->
            open(scenario, chinese)
            waitText("目录")
            waitForBookContent(scenario, "paper", "rgb(250, 249, 245)", "山海之间")
            compose.onNodeWithText("目录").performClick()
            waitText("目录 · 25 章")
            compose.onNodeWithText("山海来信.txt").assertIsDisplayed()
            compose.onNodeWithText("读到第 13 章，共 25 章").assertIsDisplayed()
            capture("toc.png")
            androidx.test.espresso.Espresso.pressBack()
            open(scenario, crimson)
            vm(scenario) { it.selectTheme(ReaderTheme.TERMINAL) }
            waitText("Chapter 1: Crimson")
            waitForBookContent(scenario, "terminal", "rgb(29, 42, 50)", "The morning train")
            chapterAction("下一章 ›").assertIsDisplayed()
            capture("v4-terminal-reader.png")
        }
    }

    @Test fun selectionCapsuleKeepsFourActionsVisibleAt360DpAndLargeFontScale() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                activity.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                        YHTheme(ReaderTheme.INK) {
                            Box(Modifier.width(360.dp)) {
                                SelectionBar(allFavorite = true, single = true, onMove = {}, onFavorite = {}, onDelete = {}, onDetails = {})
                            }
                        }
                    }
                }
            }
            val bounds = listOf("移到分组", "取消收藏", "详情", "删除").map { label ->
                val node = compose.onNodeWithText(label).assertIsDisplayed().assertIsEnabled()
                node.fetchSemanticsNode().boundsInRoot.also {
                    assertTrue("$label must retain a positive hit area", it.width > 0f && it.height > 0f)
                }
            }
            bounds.zipWithNext().forEach { (left, right) ->
                assertTrue("Capsule actions must not overlap", left.right <= right.left + 1f)
            }
        }
    }

    @Test fun continueReadingLongTitleRetainsItsButtonAtLargeFontScale() {
        val longTitle = "山海之间的漫长旅途与远方来信：写给每一个仍在路上的阅读者"
        runBlocking {
            val book = checkNotNull(db.bookDao().get(chinese))
            db.bookDao().update(book.copy(name = "$longTitle.txt", author = "林知远"))
        }
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[ShelfViewModel::class.java]
                val density = activity.resources.displayMetrics.density
                activity.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                        YHTheme(model.theme) { AppRoot(model) }
                    }
                }
            }
            waitText("继续阅读 ›")
            val button = compose.onNodeWithText("继续阅读 ›").performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val card = compose.onNode(hasText(longTitle) and hasClickAction()).fetchSemanticsNode().boundsInRoot
            assertTrue("Continue-reading button cannot collapse below a long title", button.width > 0f && button.height > 0f)
            assertTrue("Continue-reading button must remain inside its card", button.bottom <= card.bottom + 1f)
        }
    }

    @Test fun chapterCapsuleKeepsBothActionsReachableAtLargeFontScale() {
        launch().use { scenario ->
            open(scenario, crimson)
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[ShelfViewModel::class.java]
                val density = activity.resources.displayMetrics.density
                activity.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                        YHTheme(model.theme) { AppRoot(model) }
                    }
                }
            }
            waitText("下一章 ›")
            chapterAction("‹ 上一章").assertIsDisplayed().assertIsNotEnabled()
            chapterAction("下一章 ›").assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithText("2 / 2").assertIsDisplayed()
            chapterAction("‹ 上一章").assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithText("1 / 2").assertIsDisplayed()
        }
    }

    private fun web(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) web(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun js(scenario: ActivityScenario<MainActivity>, expression: String): String {
        val latch = CountDownLatch(1)
        var result = "null"
        scenario.onActivity { activity ->
            web(activity.window.decorView)?.evaluateJavascript(expression) { result = it; latch.countDown() } ?: latch.countDown()
        }
        assertTrue("WebView callback timeout", latch.await(5, TimeUnit.SECONDS))
        return result
    }

    private fun waitForBookContent(
        scenario: ActivityScenario<MainActivity>, theme: String, cssBackground: String, bodyText: String,
    ) {
        try {
            var epub = false
            scenario.onActivity {
                epub = (ViewModelProvider(it)[ShelfViewModel::class.java].reader as? ReaderState.Ready)?.doc is OpenedDoc.Epub
            }
            if (epub) {
                // EPUB intentionally disables JavaScript. Verify the actual served XHTML and
                // native loading state rather than enabling scripts merely to inspect the DOM.
                compose.waitUntil(25_000) {
                    var loaded = false
                    scenario.onActivity { activity ->
                        val view = web(activity.window.decorView)
                        loaded = view != null && view.progress == 100 && view.contentHeight > 100 &&
                            view.url?.endsWith("/OPS/one.xhtml") == true
                    }
                    loaded
                }
                scenario.onActivity { activity ->
                    val view = checkNotNull(web(activity.window.decorView))
                    assertFalse("EPUB scripts must remain disabled during acceptance", view.settings.javaScriptEnabled)
                    val request = object : WebResourceRequest {
                        override fun getUrl() = Uri.parse(view.url)
                        override fun isForMainFrame() = true
                        override fun isRedirect() = false
                        override fun hasGesture() = false
                        override fun getMethod() = "GET"
                        override fun getRequestHeaders() = emptyMap<String, String>()
                    }
                    val response = checkNotNull(view.webViewClient.shouldInterceptRequest(view, request))
                    val served = checkNotNull(response.data).use { it.readBytes().toString(Charsets.UTF_8) }
                    assertTrue("Served chapter must contain the selected theme", served.contains("data-theme=\"$theme\""))
                    assertTrue("Served chapter must contain theme styles", served.contains(EpubInjector.LINKS))
                    assertTrue("Served chapter must contain its heading and real body content", served.contains("<h1>") && served.contains(bodyText))
                }
            } else {
                compose.waitUntil(25_000) {
                    js(scenario, """
                        document.readyState === 'complete' && !!document.querySelector('h1') &&
                        document.documentElement.getAttribute('data-theme') === '$theme' &&
                        getComputedStyle(document.documentElement).backgroundColor === '$cssBackground' &&
                        [...document.querySelectorAll('p')].some(p => p.textContent.includes('$bodyText'))
                    """.trimIndent()) == "true"
                }
            }
            // DOM/loading readiness alone does not guarantee a WebView frame is ready to draw.
            val visualReady = CountDownLatch(1)
            scenario.onActivity { activity ->
                checkNotNull(web(activity.window.decorView)).postVisualStateCallback(
                    System.nanoTime(), object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) { visualReady.countDown() }
                    },
                )
            }
            assertTrue("Book content did not reach the WebView compositor", visualReady.await(10, TimeUnit.SECONDS))
            compose.waitForIdle()
            if (epub) {
                val expected = Color(ReaderPalette.of(ReaderTheme.fromId(theme), systemDark = false).bg).toArgb()
                compose.waitUntil(15_000) {
                    var sampleX = 0
                    var sampleY = 0
                    scenario.onActivity { activity ->
                        val view = checkNotNull(web(activity.window.decorView))
                        val location = IntArray(2)
                        view.getLocationOnScreen(location)
                        sampleX = location[0] + 1
                        sampleY = location[1] + view.height / 2
                    }
                    val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                    try { bitmap.getPixel(sampleX, sampleY) == expected }
                    finally { bitmap.recycle() }
                }
            }
        } catch (failure: Throwable) {
            val dom = runCatching { js(scenario, """
                JSON.stringify({href:location.href,readyState:document.readyState,
                    theme:document.documentElement.getAttribute('data-theme'),
                    bg:getComputedStyle(document.documentElement).backgroundColor,title:document.title,
                    h1:document.querySelector('h1')?.textContent,body:document.body?.innerText?.slice(0,500)})
            """.trimIndent()) }.getOrElse { "DOM inspection failed: $it" }
            var native = ""
            scenario.onActivity { activity ->
                val view = web(activity.window.decorView)
                val model = ViewModelProvider(activity)[ShelfViewModel::class.java]
                val doc = (model.reader as? ReaderState.Ready)?.doc
                native = "url=${view?.url} progress=${view?.progress} contentHeight=${view?.contentHeight} " +
                    "javaScriptEnabled=${view?.settings?.javaScriptEnabled} vmTheme=${model.theme} " +
                    "doc=${doc?.javaClass?.simpleName}(id=${doc?.bookId},name=${doc?.name},chapters=${doc?.chapterCount}) chapter=${model.chapter}"
            }
            val diagnostic = "Book readiness failed: requestedTheme=$theme expectedBg=$cssBackground DOM=$dom native={$native}"
            android.util.Log.e("V8BookReadiness", diagnostic, failure)
            throw AssertionError(diagnostic, failure)
        }
    }

    @Test fun markdownAndEditorChromeShareTheSelectedReaderPalette() {
        launch().use { scenario ->
            open(scenario, markdown)
            waitText("field-notes.md")
            for ((theme, palette, css) in listOf(
                Triple(ReaderTheme.PAPER, ReaderPalette.PAPER, "rgb(250, 249, 245)"),
                Triple(ReaderTheme.INK, ReaderPalette.INK, "rgb(38, 38, 36)"),
                Triple(ReaderTheme.TERMINAL, ReaderPalette.TERMINAL, "rgb(29, 42, 50)"),
            )) {
                vm(scenario) { it.selectTheme(theme) }
                compose.waitUntil(25_000) {
                    js(scenario, "document.readyState==='complete' && !!document.querySelector('#content h1') && getComputedStyle(document.documentElement).backgroundColor === '$css'") == "true"
                }
                val title = compose.onNodeWithText("field-notes.md").fetchSemanticsNode().boundsInRoot
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                assertEquals("Native top bar must match Markdown's CSS background", Color(palette.bg), pixels[1, title.center.y.toInt()])
                assertBars(scenario, darkIcons = theme == ReaderTheme.PAPER)
                if (theme == ReaderTheme.PAPER) capture("markdown-paper.png")
                compose.onNodeWithText("编辑").performClick()
                waitText("‹ 完成")
                // Native chrome appears before the trusted editor page finishes loading.
                // leave() intentionally prompts when isDirty() is unavailable; wait for a
                // ready, clean editor so this test exercises the normal unmodified exit.
                compose.waitUntil(25_000) {
                    js(scenario, "!!window.editorApi && window.editorApi.isDirty() === false") == "true"
                }
                val editorTitle = compose.onNodeWithText("field-notes.md").fetchSemanticsNode().boundsInRoot
                val editorPixels = compose.onRoot().captureToImage().toPixelMap()
                assertEquals("Editor toolbar follows the same reader theme", Color(palette.bg), editorPixels[1, editorTitle.center.y.toInt()])
                compose.onNodeWithText("‹ 完成").performClick()
                waitText("编辑")
            }
            vm(scenario) { it.backToShelf() }
            waitText("书架")
            shelfColor(ReaderPalette.TERMINAL)
        }
        launch().use { scenario ->
            waitText("书架")
            scenario.onActivity { assertEquals(ReaderTheme.TERMINAL, ViewModelProvider(it)[ShelfViewModel::class.java].theme) }
            shelfColor(ReaderPalette.TERMINAL)
        }
    }

    private fun chapters(chinese: Boolean): String = (1..25).joinToString("\n\n") { number ->
        val heading = if (chinese) "第${number}章 山海之间" else "Chapter $number: Night train"
        heading + "\n" + (1..18).joinToString("\n\n") { "山海之间，一封远方的来信。章节 $number，第 $it 段，这是生成的示例正文。" }
    }

    private fun epub(title: String, author: String, color: Int): ByteArray {
        val cover = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888)
        val image = ByteArrayOutputStream()
        try {
            val canvas = Canvas(cover)
            canvas.drawColor(color)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFF3EBDD.toInt(); strokeWidth = 2f }
            canvas.drawLine(30f, 48f, 270f, 48f, paint)
            canvas.drawLine(30f, 398f, 270f, 398f, paint)
            paint.textSize = 32f; paint.typeface = Typeface.create("serif", Typeface.NORMAL)
            canvas.drawText(title, 30f, 180f, paint)
            paint.textSize = 16f
            canvas.drawText(author, 30f, 218f, paint)
            paint.textSize = 12f
            canvas.drawText("YH / FICTIONAL EDITION", 30f, 382f, paint)
            check(cover.compress(Bitmap.CompressFormat.PNG, 100, image))
        } finally { cover.recycle() }
        return ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                fun entry(path: String, data: ByteArray) { zip.putNextEntry(ZipEntry(path)); zip.write(data); zip.closeEntry() }
                fun text(path: String, data: String) = entry(path, data.toByteArray())
                text("mimetype", "application/epub+zip")
                text("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
                text("OPS/book.opf", """
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">generated-$title</dc:identifier><dc:title>$title</dc:title><dc:creator>$author</dc:creator><dc:language>en</dc:language></metadata>
                    <manifest><item id="cover" href="cover.png" media-type="image/png" properties="cover-image"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="one" href="one.xhtml" media-type="application/xhtml+xml"/><item id="two" href="two.xhtml" media-type="application/xhtml+xml"/></manifest>
                    <spine><itemref idref="one"/><itemref idref="two"/></spine></package>
                """.trimIndent())
                text("OPS/nav.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="one.xhtml">Chapter 1: Crimson</a></li><li><a href="two.xhtml">Chapter 2: Dawn</a></li></ol></nav></body></html>""")
                for ((path, heading) in listOf("one" to "Chapter 1: Crimson", "two" to "Chapter 2: Dawn")) {
                    text("OPS/$path.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>$heading</title></head><body><h1>$heading</h1>" +
                        (1..14).joinToString("") { "<p>The morning train carried a letter across the quiet mountains. Page $it of a generated story.</p>" } + "</body></html>")
                }
                entry("OPS/cover.png", image.toByteArray())
            }
        }.toByteArray()
    }
}
