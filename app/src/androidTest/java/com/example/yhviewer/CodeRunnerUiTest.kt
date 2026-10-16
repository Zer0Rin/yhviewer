package com.example.yhviewer

import android.content.ContentValues
import android.net.Uri
import android.os.Process
import android.provider.MediaStore
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion=29)
class CodeRunnerUiTest : PermissionWebViewFixture() {
    private val created=mutableListOf<Uri>()
    private val vm get()=ViewModelProvider(compose.activity)[ShelfViewModel::class.java]
    private fun open(code:String,extension:String="js") {
        val context=compose.activity
        val name="v5-run-${System.nanoTime()}.$extension"
        val uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,name)
            put(MediaStore.MediaColumns.MIME_TYPE,"application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/YHViewerV5Tests")
        })!!
        created+=uri;context.contentResolver.openOutputStream(uri)!!.use { it.write(code.toByteArray()) }
        compose.runOnUiThread { vm.importAndOpen(uri) }
        compose.waitUntil(20_000) { var ready=false;compose.runOnUiThread { ready=vm.reader is ReaderState.Ready && find(context.window.decorView)?.progress==100 };ready }
        compose.runOnUiThread { assertEquals("Synthetic fixture filename changed",name,(vm.reader as ReaderState.Ready).doc.name) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun runnerOrNull():WebView? {
        var result:WebView?=null
        onView(isAssignableFrom(WebView::class.java)).inRoot(isDialog()).check { view,_ -> result=view as? WebView }
        return result
    }
    private fun runner():WebView = runnerOrNull()!!
    private fun value(view:WebView,expression:String):String {
        val done=CountDownLatch(1);var result="null"
        compose.runOnUiThread { if(view.parent==null) done.countDown() else view.evaluateJavascript(expression) { result=it;done.countDown() } }
        assertTrue(done.await(5,TimeUnit.SECONDS));return result
    }
    private fun start():WebView {
        compose.waitUntil(10_000) { compose.onNodeWithText("▶ 运行").isDisplayed() }
        compose.onNodeWithText("▶ 运行").assertIsDisplayed().performClick()
        val view=runner();compose.waitUntil(15_000) { value(view,"!!window.__runResult")=="true" };return view
    }
    @After fun clean() {
        compose.runOnUiThread { vm.backToShelf() }
        val context=compose.activity.applicationContext
        runBlocking {
            val dao=AppDatabase.get(context).bookDao();val repo=ShelfRepository(context,AppDatabase.get(context))
            created.forEach { uri -> dao.findBySource(uri.toString())?.let { repo.delete(it.id) };context.contentResolver.delete(uri,null,null) }
        }
    }

    @Test fun buttonIsExplicitAndStopRestartCloseAndDocumentReplacementDisposeWorkers() {
        open("globalThis.mustNotRunInReader=true;while(true){}")
        assertEquals("undefined",js("typeof window.mustNotRunInReader").trim('"'))
        val first=start()
        compose.onNodeWithText("放宽到 30 秒").performClick()
        val longer=runner();assertNotSame(first,longer)
        compose.runOnUiThread { assertNull(first.parent) }
        compose.onNodeWithText("恢复 5 秒").performClick()
        val normal=runner();assertNotSame(longer,normal)
        compose.onNodeWithText("停止").performClick();compose.onNodeWithText("已停止").assertIsDisplayed()
        compose.runOnUiThread { assertNull(normal.parent) }
        compose.onNodeWithText("重新运行").performClick()
        val restarted=runner();assertNotSame(normal,restarted)
        compose.onNodeWithText("关闭").performClick()
        // The dialog window is disposed on a later frame after dismissing the sheet.
        compose.waitUntil(5_000) { var released=false;compose.runOnUiThread { released=restarted.parent==null };released }
        compose.runOnUiThread { assertNull(restarted.parent) }
        val again=start()
        open("print('python is read only')","py")
        compose.onNodeWithText("▶ 运行").assertDoesNotExist()
        compose.runOnUiThread { assertNull(again.parent) }
    }

    @Test fun sharedRendererLossKeepsCrashSheetVisibleAndRerunCreatesFreshEnvironment() {
        open("console.log('v5-ready')")
        val target=start();val pid=Process.myPid()
        compose.runOnUiThread { target.loadUrl("chrome://crash") }
        compose.waitUntil(20_000) { compose.onAllNodesWithText(RUNNER_CRASH_MESSAGE).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(pid,Process.myPid())
        // On modern engines Profiles may use different renderers; explicitly exercise the
        // parent reader error as well. Old engines fan out the actual crash to both views.
        compose.runOnUiThread { vm.onRendererGone(vm.documentSession,0) }
        compose.onNodeWithText(RUNNER_CRASH_MESSAGE).assertIsDisplayed()
        compose.onNodeWithText("重新运行").performClick()
        val fresh=runner();assertNotSame(target,fresh)
        compose.waitUntil(15_000) { value(fresh,"window.__runResult && window.__runResult.state")=="\"done\"" }
        compose.onNodeWithText("关闭").performClick()
        compose.onNode(hasText("渲染进程已终止",substring=true)).assertIsDisplayed()
    }

    @Test fun allocationBombTerminatesOrReportsFailureWithoutKillingApplication() {
        open("const a=[];while(true)a.push(new Array(1e7).fill(1));")
        val pid=Process.myPid()
        compose.onNodeWithText("▶ 运行").performClick()
        val terminal = AtomicReference<String?>(null)
        var queryPending = false // Accessed only on the UI thread.
        compose.waitUntil(20_000) {
            if (compose.onAllNodesWithText(RUNNER_CRASH_MESSAGE).fetchSemanticsNodes().isNotEmpty()) {
                terminal.set("renderer-crash")
            } else {
                val view = runnerOrNull()
                compose.runOnUiThread {
                    // A renderer can die after submitting this query and drop its callback.
                    // Never block the native crash-state check waiting for that renderer.
                    if (view != null && view.parent != null && !queryPending) {
                        queryPending = true
                        view.evaluateJavascript("window.__runResult && window.__runResult.state") { state ->
                            if (state == "\"error\"" || state == "\"timeout\"") terminal.set(state)
                            queryPending = false
                        }
                    }
                }
            }
            terminal.get() != null
        }
        assertEquals(pid,Process.myPid())
        android.util.Log.i("RunnerTest", "Allocation bomb: ${terminal.get()}; app PID unchanged")
        compose.onNodeWithText("关闭").performClick()
    }
}
