package com.example.yhviewer

import android.app.Instrumentation
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.webkit.WebView
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** DOM evaluation does not mean the compositor has drawn its new hit-test geometry. */
internal fun awaitRenderedWebView(instrumentation: Instrumentation, view: WebView) {
    val drawn = CountDownLatch(1)
    val active = AtomicBoolean(true)
    var listener: ViewTreeObserver.OnDrawListener? = null
    instrumentation.runOnMainSync {
        view.postVisualStateCallback(SystemClock.uptimeMillis(), object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                if (!active.get()) return
                var posted = false
                listener = ViewTreeObserver.OnDrawListener {
                    if (!posted) {
                        posted = true
                        // Run after the guaranteed next draw, not inside onDraw itself.
                        view.post { drawn.countDown() }
                    }
                }
                view.viewTreeObserver.addOnDrawListener(listener)
                view.invalidate()
            }
        })
        view.invalidate()
    }
    try { assertTrue("WebView visual state and next draw ready", drawn.await(5, TimeUnit.SECONDS)) }
    finally {
        active.set(false)
        instrumentation.runOnMainSync {
            listener?.let { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(it) }
        }
    }
}

/** One actual native pointer transaction at a visually committed CSS point. */
internal fun tapRenderedWebViewPoint(
    instrumentation: Instrumentation,
    view: WebView,
    cssX: Float,
    cssY: Float,
    onAction: ((Int, String) -> Unit)? = null,
): String {
    awaitRenderedWebView(instrumentation, view)
    var screenX = 0f
    var screenY = 0f
    var metrics = ""
    instrumentation.runOnMainSync {
        assertTrue("Native tap view remains visible and attached", view.isAttachedToWindow && view.isShown)
        @Suppress("DEPRECATION") val scale = view.scale
        val x = cssX * scale
        val y = cssY * scale
        assertTrue("Native link tap is inside WebView", x >= 0 && y >= 0 && x < view.width && y < view.height)
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        screenX = location[0] + x
        screenY = location[1] + y
        metrics = "view=${System.identityHashCode(view)} scale=$scale local=[$x,$y] screen=[$screenX,$screenY]"
    }
    // Instrumentation synchronizes input-window transactions and injects into the
    // target's real window; do not send both actions directly inside one UI task.
    val downTime = SystemClock.uptimeMillis()
    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, screenX, screenY, 0)
        try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
        if (onAction != null) {
            var state = ""
            instrumentation.runOnMainSync {
                state = "focus=${view.isFocused} windowFocus=${view.hasWindowFocus()} scrollY=${view.scrollY} visible=${view.isShown}"
            }
            onAction(action, state)
        }
    }
    return metrics
}
