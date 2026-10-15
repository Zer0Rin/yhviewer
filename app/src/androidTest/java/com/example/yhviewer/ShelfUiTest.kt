package com.example.yhviewer

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ShelfUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val created = mutableListOf<Uri>()
    private val dao get() = AppDatabase.get(context).bookDao()

    private fun document(name: String, text: String, html: Boolean = false): Uri {
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, if (html) "text/html" else "text/markdown")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/YHViewerUiTests")
            })!!
        context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
        created += uri
        return uri
    }
    private fun intent(uri: Uri, html: Boolean = false) = Intent(Intent.ACTION_VIEW)
        .setClass(context, MainActivity::class.java)
        .setDataAndType(uri, if (html) "text/html" else "text/markdown")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    private fun waitText(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun search(value: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(value)
        compose.waitForIdle()
    }
    private fun cleanup() = runBlocking {
        created.forEach { uri ->
            dao.findBySource(uri.toString())?.let { ShelfRepository(context, AppDatabase.get(context)).delete(it.id) }
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test fun emptyShelfSearchFavoritesOrderingAndConfirmedDelete() {
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                waitText("书架")
                val chinese = document("中文50%.md", "# 中文")
                val alpha = document("Alpha_a_b.html", "<h1>Alpha</h1>", true)
                val beta = document("beta.md", "# Beta")
                for ((uri, name, html) in listOf(Triple(chinese,"中文50%.md",false), Triple(alpha,"Alpha_a_b.html",true),Triple(beta,"beta.md",false))) {
                    scenario.onActivity { it.startActivity(intent(uri,html)) }
                    waitText(name)
                    scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                    waitText("书架")
                    waitText(name)
                }
                assertTrue(compose.onNodeWithText("beta.md").fetchSemanticsNode().boundsInRoot.top < compose.onNodeWithText("Alpha_a_b.html").fetchSemanticsNode().boundsInRoot.top)
                assertTrue(compose.onNodeWithText("Alpha_a_b.html").fetchSemanticsNode().boundsInRoot.top < compose.onNodeWithText("中文50%.md").fetchSemanticsNode().boundsInRoot.top)
                search("50%")
                waitText("中文50%.md")
                compose.waitUntil(10_000) { compose.onAllNodesWithText("beta.md").fetchSemanticsNodes().isEmpty() }
                search("a_b")
                waitText("Alpha_a_b.html")
                compose.onNodeWithText("HTML").assertIsDisplayed()
                compose.onNode(hasText("·", substring=true)).assertIsDisplayed()
                search("ALPHA")
                waitText("Alpha_a_b.html")
                search("中文")
                waitText("中文50%.md")
                search("Alpha")
                waitText("Alpha_a_b.html")
                compose.onNodeWithContentDescription("收藏").performClick()
                compose.waitUntil(10_000) { runBlocking { dao.findBySource(alpha.toString())!!.favorite } }
                search("")
                compose.onNodeWithText("只看收藏").performClick()
                waitText("Alpha_a_b.html")
                compose.waitUntil(10_000) { compose.onAllNodesWithText("beta.md").fetchSemanticsNodes().isEmpty() }
                compose.onNodeWithContentDescription("取消收藏").assertIsDisplayed()
                compose.onNodeWithText("Alpha_a_b.html").performTouchInput { longClick() }
                waitText("从书架移除？")
                compose.onNodeWithText("取消").performClick()
                compose.onNodeWithText("Alpha_a_b.html").assertIsDisplayed()
                compose.onNodeWithText("Alpha_a_b.html").performTouchInput { longClick() }
                waitText("从书架移除？")
                compose.onNodeWithText("移除").performClick()
                waitText("没有匹配的文档")
                assertNull(runBlocking { dao.findBySource(alpha.toString()) })
                assertNotNull(context.contentResolver.openInputStream(alpha)?.use { it.read() })
            }
        } finally { cleanup() }
    }

    private fun web(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) web(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun js(scenario: ActivityScenario<MainActivity>, expression: String): String {
        val done = CountDownLatch(1)
        var result = "null"
        scenario.onActivity { a -> web(a.window.decorView)?.evaluateJavascript(expression) { result=it;done.countDown() } ?: done.countDown() }
        assertTrue(done.await(5,TimeUnit.SECONDS))
        return result
    }

    @Test fun rotationKeepsImportAndCopyThenBackUnwindsWebReaderShelf() {
        val uri = document("rotation-copy.html", "<h1>Offline</h1><a href='#section' style='position:fixed;inset:0'>Jump</a><p id='section'>Target</p>",true)
        try {
            ActivityScenario.launch<MainActivity>(intent(uri,true)).use { scenario ->
                waitText("rotation-copy.html")
                compose.waitUntil(20_000) { js(scenario,"!!document.querySelector('h1')") == "true" }
                val before = runBlocking { dao.findBySource(uri.toString())!! }
                context.contentResolver.delete(uri,null,null)
                scenario.recreate()
                waitText("rotation-copy.html")
                compose.waitUntil(20_000) { js(scenario,"document.querySelector('h1')?.textContent") == "\"Offline\"" }
                val after = runBlocking { dao.findBySource(uri.toString())!! }
                assertEquals(before,after)
                assertEquals(1,runBlocking { dao.observe("",false).first().count { it.sourceUri==uri.toString() } })
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(WebView::class.java)).perform(androidx.test.espresso.action.ViewActions.click())
                compose.waitUntil(10_000) { js(scenario,"location.hash") == "\"#section\"" }
                compose.waitForIdle()
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitUntil(10_000) { js(scenario,"location.hash") == "\"\"" }
                compose.onAllNodesWithText("书架").assertCountEquals(0)
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                waitText("书架")
                compose.onNodeWithText("rotation-copy.html").performClick()
                compose.waitUntil(20_000) { js(scenario,"document.querySelector('h1')?.textContent") == "\"Offline\"" }
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                waitText("书架")
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitUntil(10_000) { scenario.state==Lifecycle.State.DESTROYED }
            }
        } finally { cleanup() }
    }
}
