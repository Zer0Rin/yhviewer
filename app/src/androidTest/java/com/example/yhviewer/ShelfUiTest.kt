package com.example.yhviewer

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
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
    private fun exportScreenshot(name: String) {
        compose.waitForIdle()
        exportV7Screenshot(name)
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
                compose.onAllNodes(hasText("·", substring=true)).onLast().assertIsDisplayed()
                search("ALPHA")
                waitText("Alpha_a_b.html")
                search("中文")
                waitText("中文50%.md")
                search("Alpha")
                waitText("Alpha_a_b.html")
                compose.onNodeWithText("Alpha_a_b.html").performTouchInput { longClick() }
                waitText("已选 1 项")
                compose.onNodeWithContentDescription("收藏").performClick()
                compose.onNodeWithContentDescription("取消").performClick()
                compose.waitUntil(10_000) { runBlocking { dao.findBySource(alpha.toString())!!.favorite } }
                search("")
                compose.onNodeWithContentDescription("收藏，1 项").performClick()
                waitText("Alpha_a_b.html")
                compose.waitUntil(10_000) { compose.onAllNodesWithText("beta.md").fetchSemanticsNodes().isEmpty() }
                compose.onNodeWithText("Alpha_a_b.html").performTouchInput { longClick() }
                waitText("已选 1 项")
                compose.onNodeWithText("删除").performClick()
                waitText("从书架移除？")
                compose.onNode(hasText("取消") and hasAnyAncestor(isDialog())).performClick()
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                androidx.test.espresso.Espresso.pressBack()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("已选 1 项").fetchSemanticsNodes().isEmpty() }
                compose.onNodeWithText("Alpha_a_b.html").assertIsDisplayed()
                compose.onNodeWithText("Alpha_a_b.html").performTouchInput { longClick() }
                waitText("已选 1 项")
                compose.onNodeWithText("删除").performClick()
                waitText("从书架移除？")
                compose.onNodeWithText("移除").performClick()
                waitText("这里还没有内容")
                assertNull(runBlocking { dao.findBySource(alpha.toString()) })
                assertNotNull(context.contentResolver.openInputStream(alpha)?.use { it.read() })
            }
        } finally { cleanup() }
    }


    @Test fun selectionCreatesGroupMovesAndDeletesGroupWithoutDeletingDocuments() {
        val uri = document("group-selection.md", "# Group selection")
        val db = AppDatabase.get(context)
        val repo = ShelfRepository(context, db)
        val groupName = "测试分组${System.nanoTime().toString().takeLast(6)}"
        try {
            ActivityScenario.launch<MainActivity>(intent(uri)).use { scenario ->
                waitText("group-selection.md")
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                waitText("书架")
                waitText("group-selection.md")
                exportScreenshot("light-shelf.png")
                compose.onNodeWithText("group-selection.md").performTouchInput { longClick() }
                waitText("已选 1 项")
                scenario.recreate()
                waitText("已选 1 项")
                compose.onNodeWithText("详情").assertIsEnabled()
                exportScreenshot("selection.png")
                compose.onNodeWithText("移到分组").performClick()
                waitText("＋ 新建分组并移入")
                compose.onNodeWithText("＋ 新建分组并移入").performClick()
                waitText("新建分组并移入 1 项")
                compose.onAllNodes(hasSetTextAction()).onLast().performTextInput(groupName)
                compose.onNodeWithText("确定").performClick()
                compose.waitUntil(10_000) { runBlocking { dao.findBySource(uri.toString())?.groupId != null } }
                val before = runBlocking { dao.findBySource(uri.toString())!! }
                compose.onNodeWithContentDescription("$groupName，1 项").performClick()
                scenario.recreate()
                waitText("group-selection.md")
                compose.onNodeWithContentDescription("$groupName，1 项").assertIsSelected()
                search("query-that-matches-no-documents")
                waitText("没有匹配「query-that-matches-no-documents」的内容")
                compose.onNodeWithContentDescription("$groupName，0 项").performTouchInput { longClick() }
                waitText("删除分组")
                compose.onNodeWithText("删除分组").performClick()
                waitText("删除分组「$groupName」？")
                waitText("里面的 1 项会回到「未分组」，文件和阅读进度都不会删除。")
                compose.onNodeWithText("删除分组").performClick()
                compose.waitUntil(10_000) { runBlocking { dao.findBySource(uri.toString())?.groupId == null } }
                val after = runBlocking { dao.findBySource(uri.toString())!! }
                assertEquals(before.copy(groupId = null), after)
                assertNotNull(context.contentResolver.openInputStream(uri)?.use { it.read() })
            }
        } finally {
            runBlocking { repo.observeGroups().first().filter { it.name == groupName }.forEach { repo.deleteGroup(it.id) } }
            cleanup()
        }
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
                // Backgrounding now saves progress. Every import/identity field must stay unchanged.
                assertEquals(before, after.copy(locator = before.locator, progress = before.progress))
                assertEquals(Locator.START, Locator.decode(after.locator))
                assertEquals(0f, after.progress, 0f)
                assertEquals(1,runBlocking { dao.observe("",false).first().count { it.sourceUri==uri.toString() } })
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(WebView::class.java)).perform(androidx.test.espresso.action.ViewActions.click())
                compose.waitUntil(10_000) { js(scenario,"location.hash") == "\"#section\"" }
                compose.waitForIdle()
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitUntil(10_000) { js(scenario,"location.hash") == "\"\"" }
                compose.onAllNodesWithText("书架").assertCountEquals(0)
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                waitText("书架")
                waitText("rotation-copy.html")
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
