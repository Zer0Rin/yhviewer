package com.example.yhviewer

import android.content.Context
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.ServiceWorkerController
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID

object AppCapabilities {
    val pythonAvailable: Boolean = BuildConfig.PYTHON_RUNTIME
    val networkCapable: Boolean = BuildConfig.NETWORK_CAPABLE
    val persistentStorageSupported: Boolean get() = DocProfiles.isSupported()
    fun effective(p: DocPermissions): DocPermissions = p.effective(networkCapable, persistentStorageSupported)
}

/** Profile mutations run on the UI thread. [isSupported] is a read-only feature query and may
 * be called from any thread (including capability checks before a WebView exists).
 * Revocation invalidates the durable name before attempting physical deletion.
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
    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "DocProfiles mutations require the main thread" }
    }

    data class Session(val profile: String?, val host: String, val ephemeral: Boolean)

    @MainThread
    fun attach(view: WebView, bookId: Long?): Session {
        requireMainThread()
        var name: String? = null
        var newDurableName = false
        try {
            if (!isSupported()) {
                CookieManager.getInstance().setAcceptCookie(false)
                lockWorkers(ServiceWorkerController.getInstance())
                return Session(null, "session-${UUID.randomUUID()}.${Sandbox.HOST}", true)
            }
            val durable = bookId != null
            val profileName = if (bookId != null) {
                val p = prefs(view.context)
                p.getString(bookId.toString(), null) ?: "$PREFIX$bookId-${UUID.randomUUID()}".also {
                    name = it
                    newDurableName = true
                    check(p.edit().putString(bookId.toString(), it).commit()) { "Unable to save storage profile" }
                }
            } else "$TEMP${UUID.randomUUID()}"
            name = profileName
            // setProfile must precede settings, clients, loadUrl, and other WebView access.
            WebViewCompat.setProfile(view, profileName)
            val profile = ProfileStore.getInstance().getOrCreateProfile(profileName)
            profile.cookieManager.setAcceptCookie(false)
            lockWorkers(profile.serviceWorkerController)
            val session = Session(profileName, Sandbox.HOST, !durable)
            // Ownership moves to the returned Session only after all WebKit setup succeeds.
            active[profileName] = (active[profileName] ?: 0) + 1
            return session
        } catch (failure: Throwable) {
            // Destroy even when setProfile throws after partially binding the view.
            runCatching { view.destroy() }.exceptionOrNull()?.let(failure::addSuppressed)
            name?.let { failedName ->
                if (newDurableName && bookId != null) {
                    runCatching {
                        val p = prefs(view.context)
                        if (p.getString(bookId.toString(), null) == failedName) {
                            check(p.edit().remove(bookId.toString()).commit()) { "Unable to remove failed storage profile" }
                        }
                    }.exceptionOrNull()?.let(failure::addSuppressed)
                }
                if (bookId == null || newDurableName) {
                    runCatching { tryDelete(failedName) }.exceptionOrNull()?.let(failure::addSuppressed)
                }
            }
            throw failure
        }
    }

    private fun decrementActive(name: String) {
        val remaining = (active[name] ?: return) - 1
        if (remaining > 0) active[name] = remaining else active.remove(name)
    }

    private fun lockWorkers(controller: ServiceWorkerController) {
        controller.serviceWorkerWebSettings.apply {
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
        }
    }

    @MainThread
    fun revoke(context: Context, bookId: Long) {
        requireMainThread()
        val p = prefs(context)
        val name = p.getString(bookId.toString(), null)
        // Durable tombstone: even if deleteProfile fails while a view is alive, regrant uses a new name.
        check(p.edit().remove(bookId.toString()).commit()) { "Unable to revoke storage profile" }
        name?.let(::tryDelete)
    }

    @MainThread
    fun release(context: Context, session: Session) {
        requireMainThread()
        val name = session.profile ?: run {
            WebStorage.getInstance().deleteOrigin("https://${session.host}")
            return
        }
        decrementActive(name)
        if (session.ephemeral || name !in prefs(context).all.values) tryDelete(name)
    }

    private fun tryDelete(name: String): Boolean {
        if (!isSupported() || name in active) return false
        return runCatching { ProfileStore.getInstance().deleteProfile(name) }.getOrDefault(false)
    }

    @MainThread
    fun cleanup(context: Context, keepBookIds: Set<Long>): Int {
        requireMainThread()
        // Old fallback WebViews used the default profile; clear their per-origin storage at startup.
        WebStorage.getInstance().deleteAllData()
        val p = prefs(context)
        val edit = p.edit()
        p.all.keys.filter { it.toLongOrNull() !in keepBookIds }.forEach { edit.remove(it) }
        check(edit.commit()) { "Unable to clean storage registry" }
        if (!isSupported()) return 0
        val keep = p.all.values.toSet()
        return ProfileStore.getInstance().allProfileNames
            // doc- belongs to an older profile naming scheme and must still be collected.
            .filter { (it.startsWith(PREFIX) || it.startsWith(TEMP) || it.startsWith("doc-")) && it !in keep }
            .count(::tryDelete)
    }
}
