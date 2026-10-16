package com.example.yhviewer

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelProvider
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Every URI and shelf row is created by this test, never a user's source document. */
@SdkSuppress(minSdkVersion = 29)
class EditorLifecycleUiTest : PermissionWebViewFixture() {
    private val created = mutableListOf<Uri>()
    private val vm get() = ViewModelProvider(compose.activity)[ShelfViewModel::class.java]
    private fun fresh(name: String, text: String): Uri {
        val resolver = compose.activity.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "v6-lifecycle-${System.nanoTime()}-$name")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/YHViewerV6Tests")
        })!!
        created += uri
        resolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
        return uri
    }
    private fun open(): Uri {
        val uri = fresh("original.js", "console.log('original');")
        compose.runOnUiThread { vm.importAndOpen(uri) }
        compose.waitUntil(20_000) { compose.onNodeWithText("编辑").isDisplayed() }
        compose.onNodeWithText("编辑").performClick()
        compose.waitUntil(15_000) { js("!!window.editorApi") == "true" }
        return uri
    }
    private fun insert(text: String) { js("window.editorApi.insert(${MarkdownRenderer.toJsonString(text)},0)") }
    private fun text() = JsonStrings.decodeStringOrNull(js("window.editorApi.getText()"))
    private fun incoming(uri: Uri) {
        compose.runOnUiThread {
            compose.activity.startActivity(Intent(compose.activity, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW).setData(uri).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }
    @After fun cleanup() {
        compose.runOnUiThread { vm.backToShelf() }
        val context = compose.activity.applicationContext
        runBlocking {
            val db = AppDatabase.get(context)
            val repo = ShelfRepository(context, db)
            created.forEach { uri ->
                db.bookDao().findBySource(uri.toString())?.let { repo.delete(it.id) }
                context.contentResolver.delete(uri, null, null)
            }
        }
    }
    @Test fun recreationPreservesExactDraftAndDirtyBaseline() {
        open()
        insert("// 最后一个字 😀\r\n")
        val draft = text()
        var retained: android.webkit.WebView? = null
        compose.runOnUiThread {
            retained = find(compose.activity.window.decorView)
            assertSame(compose.activity.applicationContext, retained!!.context)
        }
        compose.activityRule.scenario.recreate()
        compose.runOnUiThread { assertSame(retained, find(compose.activity.window.decorView)) }
        compose.onNodeWithText("‹ 完成").assertIsDisplayed()
        assertEquals(draft, text())
        assertEquals("true", js("window.editorApi.isDirty()"))
        compose.onNodeWithText("‹ 完成").performClick()
        compose.onNodeWithText("保存修改？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertEquals(draft, text())
    }
    @Test fun recreationPreservesSavedBaselineAndLaterTyping() {
        open(); insert("// saved\n")
        compose.runOnUiThread { compose.activity.getSharedPreferences("editor_prefs",0).edit().putBoolean("copy_warning_seen",true).commit() }
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10_000) { js("window.editorApi.isDirty()") == "false" }
        insert("// later\n"); val draft = text()
        compose.activityRule.scenario.recreate()
        assertEquals(draft, text())
        assertEquals("true", js("window.editorApi.isDirty()"))
    }
    @Test fun recreationWhileSaveAsPickerOpenRetainsExportSnapshotAndExactDraft() {
        open(); insert("// picker snapshot 😀\n"); val draft = text()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
        val original = compose.activity
        val originalVm = vm
        val originalSession = originalVm.editorSession
        val stopped = java.util.concurrent.CountDownLatch(1)
        val recreated = java.util.concurrent.CountDownLatch(1)
        val replacement = java.util.concurrent.atomic.AtomicReference<MainActivity>()
        val callback = androidx.test.runner.lifecycle.ActivityLifecycleCallback { activity, stage ->
            if (activity === original && stage == androidx.test.runner.lifecycle.Stage.STOPPED) stopped.countDown()
            if (activity is MainActivity && activity !== original && activity.taskId == original.taskId &&
                stage == androidx.test.runner.lifecycle.Stage.CREATED) {
                replacement.set(activity)
                recreated.countDown()
            }
        }
        var pickerOpen = false
        instrumentation.runOnMainSync { monitor.addLifecycleCallback(callback) }
        try {
            compose.onNodeWithText("⋯").performClick()
            compose.onNodeWithText("另存为…").performClick()
            pickerOpen = true
            assertTrue("SAF picker must background the editor before recreation", stopped.await(15, java.util.concurrent.TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertEquals(draft, originalVm.editorSession!!.exportSnapshot)
                // ActivityScenario.recreate() first forces RESUMED, which cannot happen while
                // the real external picker is on top. Recreate the stopped Activity directly.
                original.recreate()
            }
            assertTrue("Background editor Activity was not recreated", recreated.await(15, java.util.concurrent.TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val activity = replacement.get()!!
                assertTrue("Original Activity must actually be destroyed", original.isDestroyed)
                assertNotEquals(androidx.test.runner.lifecycle.Stage.RESUMED, monitor.getLifecycleStageOf(activity))
                val restoredVm = ViewModelProvider(activity)[ShelfViewModel::class.java]
                assertSame(originalVm, restoredVm)
                assertSame(originalSession, restoredVm.editorSession)
                assertEquals(draft, restoredVm.editorSession!!.exportSnapshot)
            }
            assertTrue(instrumentation.uiAutomation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK,
            ))
            pickerOpen = false
            compose.waitUntil(10_000) { compose.onNodeWithText("‹ 完成").isDisplayed() }
            assertEquals(draft, text())
            assertEquals("true", js("window.editorApi.isDirty()"))
        } finally {
            instrumentation.runOnMainSync { monitor.removeLifecycleCallback(callback) }
            if (pickerOpen) instrumentation.uiAutomation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK,
            )
        }
    }
    @Test fun discardedEditorDetachesHandleAndNewEditorUsesFreshView() {
        open(); insert("// discard\n")
        lateinit var oldHandle: WebViewHandle
        var oldView: android.webkit.WebView? = null
        compose.runOnUiThread { oldHandle = vm.editorSession!!.handle; oldView = oldHandle.webView }
        compose.onNodeWithText("‹ 完成").performClick()
        compose.onNodeWithText("不保存").performClick()
        compose.runOnUiThread { assertNull(oldHandle.webView); assertNull(vm.editorSession) }
        compose.onNodeWithText("编辑").performClick()
        compose.waitUntil(15_000) { js("!!window.editorApi") == "true" }
        compose.runOnUiThread { assertNotSame(oldView, vm.editorSession!!.handle.webView) }
        assertEquals("false", js("window.editorApi.isDirty()"))
        assertEquals("console.log('original');", text())
    }
    @Test fun incomingCancelKeepsDraftAndDoesNotImportDestination() {
        open(); insert("// keep\n"); val draft = text()
        val destination = fresh("cancel.js", "destination")
        incoming(destination)
        compose.onNodeWithText("保存修改？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertEquals(draft, text())
        assertNull(runBlocking { AppDatabase.get(compose.activity).bookDao().findBySource(destination.toString()) })
    }
    @Test fun incomingDiscardSwitchesWithoutSavingDraft() {
        val original = open(); insert("// discard\n")
        val destination = fresh("discard.js", "destination")
        incoming(destination)
        compose.onNodeWithText("保存修改？").assertIsDisplayed()
        compose.onNodeWithText("不保存").performClick()
        compose.waitUntil(20_000) { compose.onNodeWithText("编辑").isDisplayed() }
        runBlocking {
            val db = AppDatabase.get(compose.activity)
            val row = db.bookDao().findBySource(original.toString())!!
            assertEquals("console.log('original');", (ShelfRepository(compose.activity, db).open(row.id) as OpenedDoc.Single).doc.text)
        }
    }
    @Test fun incomingSavePreservesDraftBeforeSwitching() {
        val original = open(); insert("// save before switching\n"); val draft = text()
        compose.runOnUiThread { compose.activity.getSharedPreferences("editor_prefs",0).edit().putBoolean("copy_warning_seen",true).commit() }
        val destination = fresh("save.js", "destination")
        incoming(destination)
        compose.onNodeWithText("保存修改？").assertIsDisplayed()
        compose.onAllNodesWithText("保存").filter(hasAnyAncestor(isDialog())).onFirst().performClick()
        compose.waitUntil(20_000) { compose.onNodeWithText("编辑").isDisplayed() }
        runBlocking {
            val db = AppDatabase.get(compose.activity)
            val row = db.bookDao().findBySource(original.toString())!!
            assertEquals(draft, (ShelfRepository(compose.activity, db).open(row.id) as OpenedDoc.Single).doc.text)
        }
    }
    @Test fun incomingSameSourceSaveNeverReimportsOverSavedDraft() {
        val original = open(); insert("// same source\n"); val draft = text()
        compose.runOnUiThread { compose.activity.getSharedPreferences("editor_prefs",0).edit().putBoolean("copy_warning_seen",true).commit() }
        incoming(original)
        compose.onNodeWithText("保存修改？").assertIsDisplayed()
        compose.onAllNodesWithText("保存").filter(hasAnyAncestor(isDialog())).onFirst().performClick()
        compose.waitUntil(20_000) { compose.onNodeWithText("编辑").isDisplayed() }
        compose.onNodeWithText("编辑").performClick()
        compose.waitUntil(15_000) { js("!!window.editorApi") == "true" }
        assertEquals(draft, text())
    }
}
