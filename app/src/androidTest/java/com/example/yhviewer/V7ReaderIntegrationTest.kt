package com.example.yhviewer

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real ReaderScreen, rather than a standalone drawer demonstration. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class V7ReaderIntegrationTest {
    private lateinit var legacyChapterMode: LegacyChapterMode
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var created: Uri? = null
    private var originalTheme: String? = null

    @Before fun rememberPreferences() {
        legacyChapterMode = LegacyChapterMode(context)
        originalTheme = context.getSharedPreferences("reader_prefs", 0).getString("md_theme", null)
    }

    @After fun cleanup() {
        legacyChapterMode.restore()
        val edit = context.getSharedPreferences("reader_prefs", 0).edit()
        originalTheme?.let { edit.putString("md_theme", it) } ?: edit.remove("md_theme")
        assertTrue(edit.commit())
        created?.let { uri ->
            runBlocking {
                val db = AppDatabase.get(context)
                db.bookDao().findBySource(uri.toString())?.let { ShelfRepository(context, db).delete(it.id) }
            }
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun launch(): ActivityScenario<MainActivity> {
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "v7-drawer-${System.nanoTime()}.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/YHViewerV7Tests")
        })!!
        created = uri
        val text = (1..25).joinToString("\n") { chapter ->
            "第${chapter}章 Drawer $chapter\n" + (1..40).joinToString("\n") { "章节 $chapter 的阅读正文，第 $it 段。" }
        }
        context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
        return ActivityScenario.launch(Intent(Intent.ACTION_VIEW).setClass(context, MainActivity::class.java)
            .setDataAndType(uri, "text/plain").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    @Test fun drawerFiltersOriginalChapterNumbersAndBackClosesBeforeLeavingReader() {
        launch().use { scenario ->
            compose.waitUntil(25_000) { compose.onAllNodesWithText("目录").fetchSemanticsNodes().isNotEmpty() }
            compose.onRoot().performTouchInput {
                swipe(Offset(width * .08f, height * .5f), Offset(width * .8f, height * .5f))
            }
            compose.onNodeWithText("目录 · 25 章").assertIsNotDisplayed()
            compose.onNodeWithText("目录").performClick()
            compose.onNodeWithText("目录 · 25 章").assertIsDisplayed()
            compose.onNode(hasSetTextAction()).performTextInput("23")
            compose.onNodeWithText("第23章 Drawer 23").performClick()
            compose.waitUntil(10_000) {
                var picked = false
                scenario.onActivity { picked = ViewModelProvider(it)[ShelfViewModel::class.java].chapter == 22 }
                picked
            }
            compose.onNodeWithText("目录").performClick()
            compose.onNodeWithText("读到第 23 章，共 25 章").assertIsDisplayed()
            compose.onNode(hasSetTextAction()).assert(
                SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,
                    androidx.compose.ui.text.AnnotatedString("")),
            )
            // Selection semantics are on the containing row, not the text node.
            compose.onNode(isSelected()).assertIsDisplayed()
            exportV7Screenshot("toc.png")
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithText("目录 · 25 章").assertIsNotDisplayed()
            scenario.onActivity {
                assertTrue(ViewModelProvider(it)[ShelfViewModel::class.java].reader is ReaderState.Ready)
            }
        }
    }

    @Test fun terminalReaderPaintsItsNativeBackgroundAndChrome() {
        val overlay = "com.android.internal.display.cutout.emulation.tall"
        fun shell(command: String): String = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand(command).use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText() }
            }
        val immersiveConfirmation = shell("settings get secure immersive_mode_confirmations").trim()
        // The first-use system onboarding dims/obscures the app independently of its colors.
        shell("settings put secure immersive_mode_confirmations confirmed")
        val originalOverlay = shell("cmd overlay list").lineSequence().firstOrNull { it.trim().endsWith(overlay) }
        val enabled = originalOverlay?.trim()?.startsWith("[x]") == true
        if (originalOverlay != null && !enabled) shell("cmd overlay enable $overlay")
        try {
            launch().use { scenario ->
                compose.waitUntil(25_000) { compose.onAllNodesWithText("目录").fetchSemanticsNodes().isNotEmpty() }
                scenario.onActivity { ViewModelProvider(it)[ShelfViewModel::class.java].selectTheme(ReaderTheme.TERMINAL) }
                compose.waitForIdle()
                // Hide reader/system bars before sampling: visible status-bar contrast treatment
                // is a system overlay and may change the composed pixel above the native background.
                compose.onNodeWithTag("BookReaderBackground").performTouchInput { click(center) }
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Aa").fetchSemanticsNodes().isEmpty() }
                compose.waitForIdle()
                val expected = androidx.compose.ui.graphics.Color(ReaderPalette.TERMINAL.bg)
                // Insets/system-bar animation runs outside Compose's idling resource.
                try {
                    compose.waitUntil(10_000) {
                        compose.onNodeWithTag("BookReaderBackground").captureToImage().toPixelMap()[1, 1] == expected
                    }
                } catch (failure: Throwable) {
                    val node = compose.onNodeWithTag("BookReaderBackground")
                    val capture = node.captureToImage()
                    val pixels = capture.toPixelMap()
                    val samples = listOf(
                        1 to 1, capture.width / 2 to 1, capture.width - 2 to 1,
                        1 to 20, 1 to 100, 1 to (capture.height / 2),
                    ).filter { (x, y) -> x < capture.width && y < capture.height }
                        .joinToString { (x, y) -> "($x,$y)=#${pixels[x,y].toArgb().toUInt().toString(16)}" }
                    android.util.Log.e("V7ReaderPixels", "expected=#${expected.toArgb().toUInt().toString(16)} " +
                        "bounds=${node.fetchSemanticsNode().boundsInRoot} image=${capture.width}x${capture.height} " +
                        "space=${capture.colorSpace} samples=$samples")
                    scenario.onActivity { activity ->
                        val decor = activity.window.decorView
                        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(decor)
                        val vm = ViewModelProvider(activity)[ShelfViewModel::class.java]
                        android.util.Log.e("V7ReaderPixels", "theme=${vm.theme} decor=${decor.width}x${decor.height} " +
                            "background=${decor.background} cutout=${insets?.displayCutout} " +
                            "systemVisible=${insets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.systemBars())} " +
                            "status=${insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())} " +
                            "cutoutInsets=${insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout())}")
                    }
                    // Explicit screenshot acceptance runs retain a native failure image too.
                    exportV7Screenshot("terminal-reader-failure.png")
                    throw failure
                }
                val image = compose.onNodeWithTag("BookReaderBackground").captureToImage().toPixelMap()
                // With chrome hidden, the top-left pixel paints the native inset background.
                assertEquals(expected, image[1, 1])
                exportV7Screenshot("terminal-reader.png")
            }
        } finally {
            if (originalOverlay != null && !enabled) shell("cmd overlay disable $overlay")
            if (immersiveConfirmation == "null" || immersiveConfirmation.isEmpty()) {
                shell("settings delete secure immersive_mode_confirmations")
            } else {
                // Settings values are read from the device; quote before passing to its shell.
                val quoted = "'" + immersiveConfirmation.replace("'", "'\"'\"'") + "'"
                shell("settings put secure immersive_mode_confirmations $quoted")
            }
        }
    }

}
