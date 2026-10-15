package com.example.yhviewer

import android.animation.ObjectAnimator
import android.view.GestureDetector
import android.view.MotionEvent
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 沙箱的四道门（纵深防御，任何一道失效，其他几道仍然挡得住）：
 *  1. 系统权限：Manifest 不声明 INTERNET
 *  2. WebView 设置：禁止 file:// 和 content://，不开 JS 桥，不允许自动弹窗；EPUB 章节直接关闭 JS
 *  3. 请求拦截：shouldInterceptRequest 只放行虚拟域名，其余一律 403
 *  4. CSP 响应头：我们自己的模板允许内联脚本；EPUB 章节 script-src 'none'
 */
object Sandbox {
    const val TAG = "Sandbox"
    const val HOST = "appassets.androidplatform.net"
    const val DOC_URL = "https://$HOST/doc/index.html"

    private val COMMON = listOf(
        "default-src 'none'",
        "style-src 'self' 'unsafe-inline' data:",
        "img-src 'self' data: blob:",
        "font-src 'self' data:",
        "media-src 'self' data: blob:",
        "connect-src 'none'",
        "frame-src 'none'",
        "form-action 'none'",
        "base-uri 'none'",
    )

    /** 单页文档（HTML/MD/代码/TXT 模板）：AI 生成的 HTML 依赖内联脚本 */
    val CSP = (COMMON + "script-src 'self' 'unsafe-inline' 'unsafe-eval' data: blob:").joinToString("; ")

    /** EPUB 章节：电子书不需要脚本，一律禁止 */
    val BOOK_CSP = (COMMON + "script-src 'none'").joinToString("; ")

    fun isLocal(url: Uri): Boolean = url.scheme == "https" && url.host == HOST && url.port in setOf(-1, 443)

    /** 章节路径逐段编码（文件名里可能有空格、中文） */
    fun bookUrl(path: String): String =
        "https://$HOST/book/" + path.split('/').joinToString("/") { Uri.encode(it) }
}

/** WebView 要显示的内容 */
sealed interface PageSource {
    /** 我们生成的整页 HTML，挂在 /doc/index.html */
    data class Html(val html: String) : PageSource

    /** EPUB 解压目录里的一个章节文件，挂在 /book/<path> */
    data class BookFile(val rootDir: File, val path: String, val fragment: String? = null) : PageSource
}

private fun noStoreResponse(mime: String?, encoding: String?, csp: String, body: java.io.InputStream?) =
    WebResourceResponse(
        mime, encoding, 200, "OK",
        mapOf("Content-Security-Policy" to csp, "Cache-Control" to "no-store"),
        body,
    )

private class DocumentPathHandler(private val html: String) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        if (path != "index.html") return null
        return noStoreResponse("text/html", "utf-8", Sandbox.CSP, ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
    }
}

/**
 * 从 EPUB 解压目录提供文件。
 * 路径穿越防护交给 androidx 的 InternalStoragePathHandler；我们只做两件事：
 * 给所有响应加上禁止脚本的 CSP，并在章节 XHTML 里注入主题样式。
 */
private class EpubPathHandler(
    context: Context,
    rootDir: File,
    private val theme: () -> ReaderTheme,
    private val readingCss: () -> String,
) : WebViewAssetLoader.PathHandler {
    private val files = WebViewAssetLoader.InternalStoragePathHandler(context, rootDir)

    override fun handle(path: String): WebResourceResponse? {
        val res = files.handle(path)?.takeIf { it.data != null } ?: return null
        val lower = path.lowercase()
        val isChapter = lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
        if (!isChapter) return noStoreResponse(res.mimeType, res.encoding, Sandbox.BOOK_CSP, res.data)
        val text = res.data?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return res
        val injected = EpubInjector.inject(text, theme(), readingCss()).toByteArray(Charsets.UTF_8)
        return noStoreResponse(res.mimeType, "utf-8", Sandbox.BOOK_CSP, ByteArrayInputStream(injected))
    }
}

private class SandboxClient(
    private val loader: WebViewAssetLoader,
    private val onHistoryChanged: (Boolean) -> Unit,
    private val onExternalLink: (Uri) -> Unit,
    private val onPageLoaded: (WebView) -> Unit,
    private val onInternalLink: (Uri) -> Boolean,
) : WebViewClient() {

    // 跑在后台线程，不能直接改 Compose 状态
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (Sandbox.isLocal(url)) {
            loader.shouldInterceptRequest(url)?.let { return it }
        }
        Log.w(Sandbox.TAG, "blocked request: ${request.method} $url")
        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked by sandbox", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        if (Sandbox.isLocal(url)) return onInternalLink(url) // true = 我们接管（如切换 EPUB 章节）
        val userClicked = request.isForMainFrame && request.hasGesture()
        if (userClicked && url.scheme in setOf("http", "https", "mailto")) {
            onExternalLink(url)
        } else {
            Log.w(Sandbox.TAG, "blocked navigation: $url (gesture=${request.hasGesture()})")
        }
        return true
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        onPageLoaded(view)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        onHistoryChanged(view.canGoBack())
    }
}

@SuppressLint("SetJavaScriptEnabled") // 我们的模板需要 JS；风险由拦截和 CSP 兜住。EPUB 章节关闭
private fun WebView.applySandboxSettings(allowJs: Boolean) {
    settings.apply {
        javaScriptEnabled = allowJs
        allowFileAccess = false
        allowContentAccess = false
        domStorageEnabled = allowJs
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        setGeolocationEnabled(false)
        mediaPlaybackRequiresUserGesture = true
        cacheMode = WebSettings.LOAD_NO_CACHE
    }
    CookieManager.getInstance().setAcceptCookie(false)
}

private fun openExternal(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (e: ActivityNotFoundException) {
        Log.w(Sandbox.TAG, "no app to open $uri")
    }
}

// ---------------- Reading position ----------------

@Suppress("DEPRECATION") // contentHeight is in CSS pixels; use the actual WebView scale, including zoom.
private fun WebView.maxScrollPx(): Int = max(0, (contentHeight * scale).roundToInt() - height)

fun WebView.scrollRatio(): Float {
    val range = maxScrollPx()
    return if (range == 0) 0f else (scrollY.toFloat() / range).coerceIn(0f, 1f)
}

private suspend fun WebView.restoreRatio(ratio: Float) {
    var last = -1
    var stable = 0
    repeat(30) {
        val h = contentHeight
        if (h > 0 && h == last) {
            if (++stable >= 3) {
                scrollTo(0, (ratio * maxScrollPx()).roundToInt())
                return
            }
        } else { stable = 0; last = h }
        delay(100)
    }
    scrollTo(0, (ratio * maxScrollPx()).roundToInt())
}

private class ScrollPosition(var ratio: Float, var reporting: Boolean = false)

/** HTML templates update live; EPUB keeps scripts disabled and reloads with injected CSS. */
@Composable
fun HtmlView(
    source: PageSource,
    modifier: Modifier = Modifier,
    theme: ReaderTheme? = null,
    wrap: Boolean? = null,
    initialRatio: Float = 0f,
    onRatioChanged: (Float) -> Unit = {},
    onInternalLink: (String, String?) -> Boolean = { _, _ -> false },
    bookTheme: () -> ReaderTheme = { ReaderTheme.AUTO },
    reading: String? = null,
    bookReadingCss: () -> String = { "" },
    gestures: ReaderGestures? = null,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageLoads by remember { mutableIntStateOf(0) }
    val position = remember { ScrollPosition(initialRatio.coerceIn(0f, 1f)) }
    val latestOnRatio by rememberUpdatedState(onRatioChanged)
    val latestOnLink by rememberUpdatedState(onInternalLink)
    val latestBookTheme by rememberUpdatedState(bookTheme)
    val latestBookReading by rememberUpdatedState(bookReadingCss)
    val latestGestures by rememberUpdatedState(gestures)

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    // One effect handles initial readiness and subsequent changes. In particular, a selection
    // made before page scripts exist is applied once onPageFinished increments pageLoads.
    LaunchedEffect(theme, wrap, reading, webView, pageLoads) {
        val view = webView ?: return@LaunchedEffect
        if (pageLoads == 0) return@LaunchedEffect
        position.reporting = false
        if (source is PageSource.Html) {
            val updates = buildList {
                theme?.let { add("window.setTheme && window.setTheme(${MarkdownRenderer.toJsonString(it.id)})") }
                wrap?.let { add("window.setWrap && window.setWrap($it)") }
                reading?.let { add("window.setReading && window.setReading(${MarkdownRenderer.toJsonString(it)})") }
            }
            if (updates.isNotEmpty()) view.evaluateJavascript(updates.joinToString(";"), null)
        }
        view.restoreRatio(position.ratio)
        position.ratio = view.scrollRatio()
        position.reporting = true
        latestOnRatio(position.ratio)
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebStorage.getInstance().deleteAllData()
            val builder = WebViewAssetLoader.Builder()
                .setDomain(Sandbox.HOST)
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            val startUrl = when (source) {
                is PageSource.Html -> {
                    builder.addPathHandler("/doc/", DocumentPathHandler(source.html))
                    Sandbox.DOC_URL
                }
                is PageSource.BookFile -> {
                    builder.addPathHandler("/book/", EpubPathHandler(context, source.rootDir, { latestBookTheme() }, { latestBookReading() }))
                    Sandbox.bookUrl(source.path) + (source.fragment?.let { "#" + Uri.encode(it) } ?: "")
                }
            }
            val loader = builder.build()
            WebView(context).apply {
                applySandboxSettings(allowJs = source is PageSource.Html)
                webViewClient = SandboxClient(
                    loader = loader,
                    onHistoryChanged = { canGoBack = it },
                    onExternalLink = { uri -> openExternal(context, uri) },
                    onPageLoaded = { pageLoads++ },
                    onInternalLink = { uri ->
                        val path = uri.path.orEmpty()
                        if (path.startsWith("/book/")) latestOnLink(path.removePrefix("/book/"), uri.fragment) else false
                    },
                )
                setOnScrollChangeListener { _, _, _, _, _ ->
                    if (position.reporting) {
                        position.ratio = scrollRatio()
                        latestOnRatio(position.ratio)
                    }
                }
                installReaderGestures(context) { latestGestures }
                loadUrl(startUrl)
                webView = this
            }
        },
        onRelease = {
            position.reporting = false
            it.setOnScrollChangeListener(null)
            it.setOnTouchListener(null)
            it.animate().cancel()
            webView = null
            it.destroy()
        },
    )
}

class ReaderGestures(
    val tapToTurn: Boolean,
    val onToggleBars: () -> Unit,
    val onReachEnd: () -> Unit,     // 已在本章末尾还往后翻 → 下一章
    val onReachStart: () -> Unit,   // 已在本章开头还往前翻 → 上一章末尾
)

/** 一次翻 90% 屏高：留一点重叠，翻页后还能看到上一屏最后一两行，不会读断 */
private fun WebView.turnPage(forward: Boolean, g: ReaderGestures) {
    val step = (height * 0.9f).roundToInt()
    if (forward) {
        if (!canScrollVertically(1)) return g.onReachEnd()
        ObjectAnimator.ofInt(this, "scrollY", scrollY, min(scrollY + step, maxScrollPx())).setDuration(160).start()
    } else {
        if (!canScrollVertically(-1)) return g.onReachStart()
        ObjectAnimator.ofInt(this, "scrollY", scrollY, max(0, scrollY - step)).setDuration(160).start()
    }
}


@SuppressLint("ClickableViewAccessibility") // 翻页不是"点击控件"，读屏用户仍可用底部栏的上一章 / 下一章
private fun WebView.installReaderGestures(context: Context, gestures: () -> ReaderGestures?) {
    val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val g = gestures() ?: return false
            // 点在链接上：交给网页处理跳转，不当成翻页
            val type = hitTestResult.type
            if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) return false
            val x = e.x / width.coerceAtLeast(1)
            when {
                !g.tapToTurn || x in 0.3f..0.7f -> g.onToggleBars()
                x > 0.7f -> turnPage(forward = true, g = g)
                else -> turnPage(forward = false, g = g)
            }
            return true
        }
    })
    setOnTouchListener { _, event ->
        detector.onTouchEvent(event)
        false
    }
}
