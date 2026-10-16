package com.example.yhviewer

import android.os.Looper
import android.webkit.WebView
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Native pulls values from a trusted bundled page; this never exposes a JS bridge. */
class WebViewHandle {
    var webView: WebView? = null
        private set
    private val pending = mutableMapOf<CancellableContinuation<String?>, WebView>()

    internal fun attach(view: WebView) {
        check(Looper.myLooper() == Looper.getMainLooper())
        webView?.takeIf { it !== view }?.let(::detach)
        webView = view
    }

    internal fun detach(view: WebView) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (webView === view) webView = null
        val callbacks = pending.filterValues { it === view }.keys.toList()
        callbacks.forEach { continuation ->
            pending.remove(continuation)
            if (continuation.isActive) continuation.resume(null)
        }
    }

    internal suspend fun evaluate(script: String): String? = withContext(Dispatchers.Main.immediate) {
        withTimeoutOrNull(5_000) {
            val view = webView ?: return@withTimeoutOrNull null
            suspendCancellableCoroutine { continuation ->
                pending[continuation] = view
                continuation.invokeOnCancellation { view.post { pending.remove(continuation) } }
                try {
                    view.evaluateJavascript(script) { result ->
                        if (pending.remove(continuation) != null && continuation.isActive) {
                            continuation.resume(result.takeIf { webView === view })
                        }
                    }
                } catch (_: RuntimeException) {
                    pending.remove(continuation)
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    suspend fun editorText(): String? = evaluate("window.editorApi ? window.editorApi.getText() : null")
        ?.let(JsonStrings::decodeStringOrNull)

    suspend fun editorDirty(): Boolean = JsonStrings.decodeBoolean(
        evaluate("!!(window.editorApi && window.editorApi.isDirty())"),
    )

    fun markSaved(snapshot: String) {
        check(Looper.myLooper() == Looper.getMainLooper())
        webView?.evaluateJavascript("window.editorApi && window.editorApi.markSaved(${MarkdownRenderer.toJsonString(snapshot)})", null)
    }
}
