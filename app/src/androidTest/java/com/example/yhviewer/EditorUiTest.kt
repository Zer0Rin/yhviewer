package com.example.yhviewer

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SdkSuppress(minSdkVersion=29)
class EditorUiTest : PermissionWebViewFixture() {
    private val created=mutableListOf<Uri>()
    private val vm get()=ViewModelProvider(compose.activity)[ShelfViewModel::class.java]
    @Before fun resetOwnWarning() { compose.activity.getSharedPreferences("editor_prefs",0).edit().clear().commit() }
    private fun fresh(name:String,text:String):Uri {
        val c=compose.activity.contentResolver
        val uri=c.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"v6-${System.nanoTime()}-$name")
            put(MediaStore.MediaColumns.MIME_TYPE,"application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/YHViewerV6Tests")
        })!!
        created+=uri;c.openOutputStream(uri)!!.use { it.write(text.toByteArray()) };return uri
    }
    private fun open(name:String,text:String):Pair<Uri,OpenedDoc.Single> {
        val uri=fresh(name,text)
        compose.runOnUiThread { vm.importAndOpen(uri) }
        compose.waitUntil(20_000) { var ready=false;compose.runOnUiThread { ready=vm.reader is ReaderState.Ready };ready }
        var doc:OpenedDoc.Single?=null
        compose.runOnUiThread {doc=(vm.reader as ReaderState.Ready).doc as OpenedDoc.Single}
        return uri to doc!!
    }
    private fun edit() {
        compose.onNodeWithText("编辑").performClick()
        compose.waitUntil(15_000) {js("!!window.editorApi")=="true"}
    }
    private fun text()=JsonStrings.decodeStringOrNull(js("window.editorApi.getText()"))!!
    private fun insert(value:String) {js("window.editorApi.insert(${MarkdownRenderer.toJsonString(value)},0)")}
    private fun read(uri:Uri)=compose.activity.contentResolver.openInputStream(uri)!!.use {it.readBytes().toString(Charsets.UTF_8)}
    @After fun cleanup() {
        compose.runOnUiThread {vm.backToShelf()}
        val c=compose.activity.applicationContext
        runBlocking { val db=AppDatabase.get(c);val r=ShelfRepository(c,db)
            created.forEach {uri -> db.bookDao().findBySource(uri.toString())?.let {r.delete(it.id)};c.contentResolver.delete(uri,null,null)}
        }
    }
    @Test fun saveWarnsThenUpdatesOnlyCopyAndReopensAfterActivityRecreation() {
        val original="console.log('original');"
        val (uri,doc)=open("save.js",original)
        edit();insert("// 中文 😀\n");val edited=text()
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("保存到书架副本").assertIsDisplayed()
        assertEquals(original,read(uri))
        compose.onNodeWithText("继续保存").performClick()
        compose.waitUntil(10_000) { var done=false;compose.runOnUiThread {done=((vm.reader as? ReaderState.Ready)?.doc as? OpenedDoc.Single)?.doc?.text==edited};done }
        compose.waitUntil(5_000) { js("window.editorApi.isDirty()") == "false" }
        compose.onNodeWithText("‹ 完成").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { compose.onNodeWithText("编辑").isDisplayed() }
        compose.onNodeWithText("编辑").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.runOnUiThread {vm.openBook(doc.bookId)}
        compose.waitUntil(10_000) {var done=false;compose.runOnUiThread {done=((vm.reader as? ReaderState.Ready)?.doc as? OpenedDoc.Single)?.doc?.text==edited};done}
        assertEquals(original,read(uri))
    }
    @Test fun dirtyBackCancelRetainsEditsAndDiscardKeepsSavedContent() {
        val original="# Original"
        val (_,doc)=open("discard.md",original)
        edit();insert("unsaved ")
        compose.onNodeWithText("‹ 完成").performClick()
        compose.onNodeWithText("保存修改？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertTrue(text().contains("unsaved"))
        compose.onNodeWithText("‹ 完成").performClick()
        compose.onNodeWithText("不保存").performClick()
        compose.onNodeWithText("编辑").assertIsDisplayed()
        val r=ShelfRepository(compose.activity,AppDatabase.get(compose.activity))
        assertEquals(original,(runBlocking {r.open(doc.bookId)} as OpenedDoc.Single).doc.text)
    }
    @Test fun runUsesUnsavedSnapshotWithoutWritingBook() {
        val original="console.log('saved-original');"
        val (uri,doc)=open("run.js",original)
        edit();insert("console.log('unsaved-native-marker');\n")
        compose.onNodeWithText("▶ 运行").performClick()
        var view:WebView?=null
        onView(isAssignableFrom(WebView::class.java)).inRoot(isDialog()).check {v,_->view=v as WebView}
        fun output():String { val latch=CountDownLatch(1);var result="null"
            compose.runOnUiThread {view!!.evaluateJavascript("document.getElementById('out').textContent") {result=it;latch.countDown()} }
            assertTrue(latch.await(5,TimeUnit.SECONDS));return result
        }
        compose.waitUntil(20_000) {output().contains("unsaved-native-marker")}
        assertEquals(original,read(uri))
        assertEquals(original,(runBlocking {ShelfRepository(compose.activity,AppDatabase.get(compose.activity)).open(doc.bookId)} as OpenedDoc.Single).doc.text)
        compose.onNodeWithText("关闭").performClick()
        compose.waitUntil(5_000) {js("!!window.editorApi")=="true"}
        assertTrue(text().contains("unsaved-native-marker"))
    }
    @Test fun saveAsWritesFreshUriAndAddsBookButRejectsKnownOriginal() {
        val (original,doc)=open("source.kt","original")
        val destination=fresh("export.kt","placeholder")
        val changed="fun main() { println(\"你好\") }"
        val outcome=runBlocking {vm.saveAs(destination,changed)}
        assertTrue(outcome.toString(),outcome.isSuccess)
        assertNotEquals(doc.bookId,outcome.getOrThrow())
        assertEquals(changed,read(destination))
        assertEquals("original",read(original))
        assertTrue(runBlocking {vm.saveAs(original,"overwrite")}.isFailure)
        assertEquals("original",read(original))
    }
    @Test fun missingEditorComponentRefusesSaveAndPreservesBookAndOriginal() {
        val (uri,doc)=open("missing.html","<h1>keep me</h1>")
        val template=Templates.get(compose.activity,EditorRenderer.TEMPLATE).replace("cm6.bundle.js","missing-editor.js")
        compose.runOnUiThread {compose.activity.setContent {EditorScreen(vm,doc,template) {}}}
        compose.waitUntil(15_000) {js("document.body.textContent").contains("编辑器组件未加载")}
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("编辑器未就绪，没有保存").assertIsDisplayed()
        assertEquals("<h1>keep me</h1>",read(uri))
        assertEquals(doc.doc.text,(runBlocking {ShelfRepository(compose.activity,AppDatabase.get(compose.activity)).open(doc.bookId)} as OpenedDoc.Single).doc.text)
    }
    @Test fun pythonButtonMatchesEditionWhileEditorRemainsAvailable() {
        open("gate.py","print('hello')")
        if(AppCapabilities.pythonAvailable) compose.onNodeWithText("▶ 运行").assertIsDisplayed()
        else compose.onNodeWithText("▶ 运行").assertDoesNotExist()
        edit()
        if(AppCapabilities.pythonAvailable) compose.onNodeWithText("▶ 运行").assertIsDisplayed()
        else compose.onNodeWithText("▶ 运行").assertDoesNotExist()
        val assets=compose.activity.assets.list("pyodide").orEmpty()
        assertEquals(AppCapabilities.pythonAvailable,assets.contains("pyodide.asm.wasm"))
    }
}
