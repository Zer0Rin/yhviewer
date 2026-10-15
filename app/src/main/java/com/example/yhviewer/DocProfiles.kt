package com.example.yhviewer

import android.content.Context
import android.webkit.CookieManager
import android.webkit.ServiceWorkerController
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID

object AppCapabilities {
    val networkCapable: Boolean = BuildConfig.NETWORK_CAPABLE
    val persistentStorageSupported: Boolean get() = DocProfiles.isSupported()
    fun effective(p: DocPermissions): DocPermissions = p.effective(networkCapable).let {
        if (persistentStorageSupported) it else it.copy(persistentStorage = false)
    }
}

/** UI-thread only. Revocation invalidates the durable name before attempting physical deletion.
 * A WebView can keep an old profile alive, but no future view may attach to that generation.
 * Loaded profile deletion may be deferred until next process start; generations are never reused.
 * Ephemeral views never share a profile. Older engines use an unguessable per-render origin.
 */
object DocProfiles {
    private const val PREFIX = "yh-doc-"
    private const val TEMP = "yh-session-"
    private val active = mutableMapOf<String, Int>()
    private fun prefs(context: Context) = context.getSharedPreferences("document_profiles", Context.MODE_PRIVATE)
    fun isSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    data class Session(val profile: String?, val host: String, val ephemeral: Boolean)

    fun attach(view: WebView, bookId: Long?): Session {
        if (!isSupported()) {
            CookieManager.getInstance().setAcceptCookie(false)
            lockWorkers(ServiceWorkerController.getInstance())
            return Session(null, "session-${UUID.randomUUID()}.${Sandbox.HOST}", true)
        }
        val durable = bookId != null
        val name = if (bookId != null) {
            val p = prefs(view.context)
            p.getString(bookId.toString(), null) ?: "$PREFIX$bookId-${UUID.randomUUID()}".also {
                check(p.edit().putString(bookId.toString(), it).commit()) { "Unable to save storage profile" }
            }
        } else "$TEMP${UUID.randomUUID()}"
        // setProfile must precede settings, clients, loadUrl, and other WebView access.
        WebViewCompat.setProfile(view, name)
        val profile = ProfileStore.getInstance().getOrCreateProfile(name)
        profile.cookieManager.setAcceptCookie(false)
        lockWorkers(profile.serviceWorkerController)
        active[name] = (active[name] ?: 0) + 1
        return Session(name, Sandbox.HOST, !durable)
    }

    private fun lockWorkers(controller: ServiceWorkerController) {
        controller.serviceWorkerWebSettings.apply {
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
        }
    }

    fun revoke(context: Context, bookId: Long) {
        val p = prefs(context)
        val name = p.getString(bookId.toString(), null)
        // Durable tombstone: even if deleteProfile fails while a view is alive, regrant uses a new name.
        check(p.edit().remove(bookId.toString()).commit()) { "Unable to revoke storage profile" }
        name?.let(::tryDelete)
    }

    fun release(context: Context, session: Session) {
        val name = session.profile ?: run {
            WebStorage.getInstance().deleteOrigin("https://${session.host}")
            return
        }
        val remaining = (active[name] ?: 1) - 1
        if (remaining > 0) active[name] = remaining else active.remove(name)
        if (session.ephemeral || name !in prefs(context).all.values) tryDelete(name)
    }

    private fun tryDelete(name: String): Boolean {
        if (!isSupported() || name in active) return false
        return runCatching { ProfileStore.getInstance().deleteProfile(name) }.getOrDefault(false)
    }

    fun cleanup(context: Context, keepBookIds: Set<Long>): Int {
        val p = prefs(context)
        val edit = p.edit()
        p.all.keys.filter { it.toLongOrNull() !in keepBookIds }.forEach { edit.remove(it) }
        check(edit.commit()) { "Unable to clean storage registry" }
        if (!isSupported()) return 0
        val keep = p.all.values.toSet()
        return ProfileStore.getInstance().allProfileNames
            .filter { (it.startsWith(PREFIX) || it.startsWith(TEMP) || it.startsWith("doc-")) && it !in keep }
            .count(::tryDelete)
    }
}
