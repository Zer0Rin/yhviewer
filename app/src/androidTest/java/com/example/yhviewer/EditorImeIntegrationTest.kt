package com.example.yhviewer

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real Chromium input connection and native pointer events, against the shipped CM bundle.
 * This exercises the IME protocol; a real Chinese keyboard's candidate UI still needs manual QA.
 * No editorApi mutation is used to simulate typing or toolbar activation.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class EditorImeIntegrationTest : PermissionWebViewFixture() {
    private val created = mutableListOf<Uri>()
    private var lastTouch = "No touch delivered"
    private val vm get() = ViewModelProvider(compose.activity)[ShelfViewModel::class.java]

    private fun openEmptyEditor(): Uri {
        val resolver = compose.activity.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "v6-ime-${System.nanoTime()}.js")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/YHViewerV6Tests")
        })!!
        created += uri
        resolver.openOutputStream(uri)!!.use { it.write(byteArrayOf()) }
        compose.runOnUiThread { vm.importAndOpen(uri) }
        compose.waitUntil(20_000) {
            var ready = false
            compose.runOnUiThread { ready = vm.reader is ReaderState.Ready }
            ready
        }
        compose.onNodeWithText("编辑").performClick()
        compose.waitUntil(15_000) { js("!!window.editorApi") == "true" }
        js("""
            window.__imeTouchTrace = [];
            ['pointerdown', 'pointerup', 'click'].forEach(function(type) {
                document.addEventListener(type, function(e) {
                    window.__imeTouchTrace.push({type:e.type, target:e.target.outerHTML.slice(0,300),
                        x:e.clientX, y:e.clientY, trusted:e.isTrusted, prevented:e.defaultPrevented,
                        pointerType:e.pointerType});
                });
            });
            window.__imeCompositionTrace = [];
            ['compositionstart','compositionupdate','compositionend','beforeinput','input'].forEach(function(type) {
                document.addEventListener(type, function(e) {
                    window.__imeCompositionTrace.push({type:e.type,data:e.data,inputType:e.inputType,
                        composing:e.isComposing,trusted:e.isTrusted,time:performance.now(),
                        text:window.editorApi && window.editorApi.getText()});
                });
            });
        """.trimIndent())
        // Touch the actual contenteditable to establish a user-initiated editing/IME session.
        tapDom(".cm-content", firstLine = true)
        awaitEditorFocusAndIme()
        // Opening the IME can restart Chromium's InputConnection during the resize. Do
        // not begin a candidate-composition sequence until both native and DOM settle.
        awaitReachableToolbar("button[data-pair='()']")
        assertEquals("", editorText())
        return uri
    }

    private fun editorText() = JsonStrings.decodeStringOrNull(js("window.editorApi.getText()"))!!

    private fun awaitText(expected: String) {
        try { compose.waitUntil(10_000) { editorText() == expected } }
        catch (error: Throwable) {
            val diagnostic = captureFailure("Expected editor text: $expected")
            throw AssertionError(diagnostic, error)
        }
        assertEquals(expected, editorText())
    }

    private fun captureFailure(reason: String): String {
        val dom = js("""
            (function() {
                function rect(e) { if (!e) return null; var r=e.getBoundingClientRect();
                    return {left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height}; }
                function layout(e) { if (!e) return null; var s=getComputedStyle(e);
                    return {rect:rect(e),display:s.display,height:s.height,minHeight:s.minHeight,maxHeight:s.maxHeight,
                        flex:s.flex,flexDirection:s.flexDirection,flexBasis:s.flexBasis,position:s.position,
                        overflow:s.overflow,overflowY:s.overflowY,zIndex:s.zIndex,
                        scrollHeight:e.scrollHeight,clientHeight:e.clientHeight,scrollTop:e.scrollTop}; }
                var p=window.__imeLastDomPoint;
                var hit=p && document.elementFromPoint(p[0],p[1]);
                var v=window.visualViewport;
                return {text:window.editorApi && window.editorApi.getText(),
                    activeElement:document.activeElement.outerHTML.slice(0,500), documentFocus:document.hasFocus(),
                    innerWidth:innerWidth,innerHeight:innerHeight,devicePixelRatio:devicePixelRatio,
                    compatMode:document.compatMode,root:layout(document.documentElement),body:layout(document.body),
                    editorHost:layout(document.getElementById('editor')),cmEditor:layout(document.querySelector('.cm-editor')),
                    cmScroller:layout(document.querySelector('.cm-scroller')),bar:layout(document.getElementById('bar')),
                    scrollX:scrollX,scrollY:scrollY,barRect:rect(document.getElementById('bar')),
                    editorRect:rect(document.querySelector('.cm-content')),
                    visualViewport:v && {width:v.width,height:v.height,offsetLeft:v.offsetLeft,
                        offsetTop:v.offsetTop,pageLeft:v.pageLeft,pageTop:v.pageTop,scale:v.scale},
                    lastDomPoint:p,elementFromPoint:hit && hit.outerHTML.slice(0,500),events:window.__imeTouchTrace,
                    compositionEvents:window.__imeCompositionTrace};
            })()
        """.trimIndent())
        var currentNative = ""
        compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            val web = find(decor)
            val location = IntArray(2)
            web?.getLocationOnScreen(location)
            val insets = ViewCompat.getRootWindowInsets(decor)
            currentNative = "Native after failure: location=${location.contentToString()}, " +
                "size=${web?.width}x${web?.height}, focus=${web?.hasFocus()}, windowFocus=${web?.hasWindowFocus()}, " +
                "scroll=(${web?.scrollX},${web?.scrollY}), imeVisible=${insets?.isVisible(WindowInsetsCompat.Type.ime())}, " +
                "imeInsets=${insets?.getInsets(WindowInsetsCompat.Type.ime())}, decor=${decor.width}x${decor.height}"
        }
        val detail = "$reason\n$lastTouch\n$currentNative\nDOM after failure: $dom"
        val directory = compose.activity.externalCacheDir ?: compose.activity.cacheDir
        val basename = "v6-ime-failure-${System.nanoTime()}"
        val textFile = File(directory, "$basename.txt").apply { writeText(detail) }
        var screenshot = "Screenshot unavailable"
        try {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                val file = File(directory, "$basename.png")
                try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                finally { bitmap.recycle() }
                screenshot = file.absolutePath
            }
        } catch (error: Throwable) { screenshot = "Screenshot failed: $error" }
        val result = "$detail\nDiagnostic file: ${textFile.absolutePath}\nScreenshot: $screenshot"
        Log.e("EditorImeIntegration", result)
        return result
    }

    private fun awaitEditorFocusAndIme() {
        try {
            compose.waitUntil(10_000) {
                var nativeFocus = false
                var imeVisible = false
                compose.runOnUiThread {
                    nativeFocus = find(compose.activity.window.decorView)?.hasFocus() == true
                    imeVisible = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                nativeFocus && imeVisible && js("document.activeElement.matches('.cm-content')") == "true"
            }
        } catch (error: Throwable) {
            throw AssertionError(captureFailure("Editor focus or visible IME not established"), error)
        }
    }

    private fun inputConnection(): InputConnection {
        var connection: InputConnection? = null
        compose.runOnUiThread {
            val web = find(compose.activity.window.decorView)!!
            assertTrue("Native WebView must retain focus", web.hasFocus())
            connection = web.onCreateInputConnection(EditorInfo())
        }
        assertNotNull("Focused CodeMirror must expose Chromium's real InputConnection", connection)
        return connection!!
    }

    private fun input(connection: InputConnection, operation: InputConnection.() -> Boolean) {
        // Chromium's InputConnection forwards these platform IME calls to its renderer.
        // Honor its advertised IME handler instead of assuming the connection runs on main.
        var accepted = false
        var failure: Throwable? = null
        val done = CountDownLatch(1)
        val handler = connection.handler ?: Handler(Looper.getMainLooper())
        assertTrue("InputConnection handler rejected the operation", handler.post {
            try { accepted = connection.operation() }
            catch (error: Throwable) { failure = error }
            finally { done.countDown() }
        })
        assertTrue("IME operation timed out", done.await(5, TimeUnit.SECONDS))
        failure?.let { throw AssertionError("IME operation failed on its connection handler", it) }
        assertTrue("Chromium rejected the IME operation", accepted)
    }

    private fun tapDom(selector: String, firstLine: Boolean = false) {
        if (!firstLine) awaitReachableToolbar(selector)
        // DOM inspection supplies coordinates only: Instrumentation delivers DOWN/UP through
        // Android's input pipeline, exercising the production pointerdown preventDefault handler.
        val coordinates = JSONArray(js("""
            (function() {
                var e = document.querySelector(${MarkdownRenderer.toJsonString(selector)});
                if (!e) throw new Error('Missing touch target');
                e.scrollIntoView({block:'nearest', inline:'nearest'});
                var r = e.getBoundingClientRect();
                var p=[${if (firstLine) "r.left + 8, r.top + 10" else "r.left + r.width / 2, r.top + r.height / 2"}];
                window.__imeLastDomPoint=p;
                return [p[0], p[1], window.innerWidth];
            })()
        """.trimIndent()))
        val location = IntArray(2)
        var scale = 1f
        var nativeState = ""
        compose.runOnUiThread {
            val web = find(compose.activity.window.decorView)!!
            web.getLocationOnScreen(location)
            scale = web.width.toFloat() / coordinates.getDouble(2).toFloat()
            val visible = Rect()
            web.getGlobalVisibleRect(visible)
            val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            nativeState = "WebView location=${location.contentToString()}, size=${web.width}x${web.height}, " +
                "visibleRect=$visible, focus=${web.hasFocus()}, windowFocus=${web.hasWindowFocus()}, " +
                "imeVisible=${insets?.isVisible(WindowInsetsCompat.Type.ime())}, " +
                "imeInsets=${insets?.getInsets(WindowInsetsCompat.Type.ime())}, " +
                "webScroll=(${web.scrollX},${web.scrollY}), " +
                "decorSize=${compose.activity.window.decorView.width}x${compose.activity.window.decorView.height}"
        }
        val x = location[0] + coordinates.getDouble(0).toFloat() * scale
        val y = location[1] + coordinates.getDouble(1).toFloat() * scale
        lastTouch = "Touch selector=$selector DOM=$coordinates scale=$scale screen=($x,$y); $nativeState"
        Log.i("EditorImeIntegration", lastTouch)
        if (!firstLine) {
            val reachable = js("""
                (function() {
                    var e=document.querySelector(${MarkdownRenderer.toJsonString(selector)});
                    var r=e.getBoundingClientRect();
                    var host=document.getElementById('editor').getBoundingClientRect();
                    var bar=document.getElementById('bar').getBoundingClientRect();
                    var hit=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);
                    return host.height > 0 && host.bottom <= bar.top+1 && bar.bottom <= innerHeight+1 &&
                        !!hit && hit.closest('button') === e;
                })()
            """.trimIndent())
            if (reachable != "true") throw AssertionError(captureFailure("Toolbar layout overlaps editor or is not touch reachable"))
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).let {
            try { instrumentation.sendPointerSync(it) } finally { it.recycle() }
        }
        MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0).let {
            try { instrumentation.sendPointerSync(it) } finally { it.recycle() }
        }
        instrumentation.waitForIdleSync()
    }

    private fun awaitReachableToolbar(selector: String) {
        compose.waitForIdle()
        var previous = "null"
        try {
            compose.waitUntil(10_000) {
                // IME visibility can become true before Compose commits imePadding. DOM
                // geometry may still describe the full-height WebView, behind the keyboard.
                val nativeViewport = nativeImeViewport()
                if (nativeViewport == null) {
                    previous = "null"
                    return@waitUntil false
                }
                val geometry = js("""
                    (function() {
                        var e=document.querySelector(${MarkdownRenderer.toJsonString(selector)});
                        var r=e.getBoundingClientRect();
                        var host=document.getElementById('editor').getBoundingClientRect();
                        var bar=document.getElementById('bar').getBoundingClientRect();
                        var hit=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);
                        var rootHeight=document.documentElement.getBoundingClientRect().height;
                        if (!(host.height>0 && host.bottom<=bar.top+1 && Math.abs(bar.bottom-innerHeight)<=1 &&
                            Math.abs(rootHeight-innerHeight)<=1 && scrollY===0 && hit && hit.closest('button')===e)) return null;
                        return [innerHeight,host.height,bar.top,bar.bottom,r.left,r.top,r.width,r.height];
                    })()
                """.trimIndent())
                val signature = "$nativeViewport|$geometry"
                val stable = geometry != "null" && signature == previous
                previous = signature
                stable
            }
        } catch (error: Throwable) {
            throw AssertionError(captureFailure("Toolbar did not settle into a nonoverlapping, touch-reachable viewport"), error)
        }
    }

    /** Returns a stability signature only when native content is above the visible IME. */
    private fun nativeImeViewport(): String? {
        var signature: String? = null
        compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            val web = find(decor)
            val insets = ViewCompat.getRootWindowInsets(decor)
            if (web != null && insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                val webLocation = IntArray(2)
                val decorLocation = IntArray(2)
                web.getLocationOnScreen(webLocation)
                decor.getLocationOnScreen(decorLocation)
                val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val keyboardTop = decorLocation[1] + decor.height - imeBottom
                if (webLocation[1] + web.height <= keyboardTop + 2 && web.scrollY == 0) {
                    signature = "${webLocation.contentToString()}/${web.width}x${web.height}/$imeBottom"
                }
            }
        }
        return signature
    }

    @After fun cleanupOnlyOwnedDocuments() {
        compose.runOnUiThread { vm.backToShelf() }
        val context = compose.activity.applicationContext
        runBlocking {
            val db = AppDatabase.get(context)
            val repository = ShelfRepository(context, db)
            created.forEach { uri ->
                db.bookDao().findBySource(uri.toString())?.let { repository.delete(it.id) }
                context.contentResolver.delete(uri, null, null)
            }
        }
    }

    @Test fun cjkCompositionReplacesCandidatesAndCommitsExactUnicodeThroughInputConnection() {
        val source = openEmptyEditor()
        val connection = inputConnection()
        // This test acts as an IME while the system keyboard remains visible. Defer
        // selection notifications until our composing session finishes, so the active
        // Latin keyboard cannot finish our candidate region between injected calls.
        input(connection) { beginBatchEdit() }
        try {
            input(connection) { setComposingText("ni", 1) }
            awaitText("ni")
            input(connection) { setComposingText("你", 1) }
            awaitText("你")
            input(connection) { setComposingText("你好", 1) }
            awaitText("你好")
            input(connection) { commitText("你好，世界😀", 1) }
            input(connection) { finishComposingText() }
            awaitText("你好，世界😀")
            input(connection) { setComposingText("再见", 1) }
            input(connection) { finishComposingText() }
            awaitText("你好，世界😀再见")
        } finally {
            // endBatchEdit may return false when the final nesting level closes normally.
            input(connection) { endBatchEdit(); true }
        }
        Log.i("EditorImeIntegration", "CJK composition events: ${js("window.__imeCompositionTrace")}")
        awaitEditorFocusAndIme()
        assertEquals("true", js("window.editorApi.isDirty()"))
        assertEquals("", compose.activity.contentResolver.openInputStream(source)!!.use {
            it.readBytes().toString(Charsets.UTF_8)
        })
    }


    @Test fun nativeSymbolTouchKeepsImeAndInsertsNextCjkCommitBetweenPairedBrackets() {
        openEmptyEditor()
        tapDom("button[data-pair='()']")
        awaitText("()")
        awaitEditorFocusAndIme()
        // A fresh connection reflects the selection after the native toolbar touch.
        val connection = inputConnection()
        input(connection) { setComposingText("zhong", 1) }
        awaitText("(zhong)")
        input(connection) { commitText("中文😀", 1) }
        input(connection) { finishComposingText() }
        awaitText("(中文😀)")
        awaitEditorFocusAndIme()
    }

    @Test fun nativeUndoRedoAndSearchOperateOnRealEditorWithoutChangingSource() {
        openEmptyEditor()
        tapDom("button[data-pair='()']")
        awaitText("()")
        tapDom("button[data-act='undo']")
        awaitText("")
        awaitEditorFocusAndIme()
        tapDom("button[data-act='redo']")
        awaitText("()")
        awaitEditorFocusAndIme()
        tapDom("button[data-act='search']")
        compose.waitUntil(5_000) { js("!!document.querySelector('.cm-search input')") == "true" }
        assertEquals("()", editorText())
    }
}
