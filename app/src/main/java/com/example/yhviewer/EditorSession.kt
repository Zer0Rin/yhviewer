package com.example.yhviewer

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** In-memory editing session, retained only for this ViewModel's lifetime.
 * The application-context view is the exact draft and saved baseline: configuration changes
 * reparent it without a reload or an asynchronous last-keystroke snapshot race.
 * No Activity, Compose callbacks, document permissions, or JS-to-native bridge are retained.
 */
class EditorSession internal constructor(
    context: Context,
    val doc: OpenedDoc.Single,
    wrap: Boolean,
    theme: ReaderTheme,
    parentScope: CoroutineScope,
    template: String? = null,
) {
    private val app = context.applicationContext
    val handle = WebViewHandle()
    val snackbar = SnackbarHostState()
    val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    val lang = EditorRenderer.langFor(doc.doc)
    val html = EditorRenderer.render(doc.doc.text, lang, wrap, template ?: Templates.get(app, EditorRenderer.TEMPLATE), theme)
    var confirmLeave by mutableStateOf(false)
    var pendingSave by mutableStateOf<String?>(null)
    var closeAfterSave by mutableStateOf(false)
    var exportSnapshot by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var crashed by mutableStateOf(false)
        internal set
    var pageLoads by mutableIntStateOf(0)
        internal set
    var incomingUri by mutableStateOf<Uri?>(null)
        internal set
    private var view: WebView? = null
    private var profile: DocProfiles.Session? = null
    private var lease: Any? = null
    private var closed = false

    internal fun mount(owner: Any): WebView {
        check(!closed && !crashed)
        val retained = view ?: createRetainedEditorView(app, this).also { (web, session) ->
            view = web
            profile = session
            handle.attach(web)
        }.first
        (retained.parent as? ViewGroup)?.removeView(retained)
        lease = owner
        retained.onResume()
        return retained
    }

    internal fun unmount(owner: Any) {
        // An old Compose holder may be released after the new Activity has mounted the view.
        if (lease !== owner) return
        lease = null
        view?.let { web ->
            (web.parent as? ViewGroup)?.removeView(web)
            web.onPause()
        }
    }

    internal fun rendererGone() {
        crashed = true
        releaseView()
    }

    private fun releaseView() {
        val web = view ?: return
        view = null
        lease = null
        val session = profile
        profile = null
        handle.detach(web)
        (web.parent as? ViewGroup)?.removeView(web)
        try { web.destroy() }
        finally { if (session != null) DocProfiles.release(app, session) }
    }

    internal fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        releaseView()
        exportSnapshot = null
        pendingSave = null
        incomingUri = null
    }
}
