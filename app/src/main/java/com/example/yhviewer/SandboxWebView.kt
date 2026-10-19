package com.example.yhviewer

import android.animation.ObjectAnimator
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
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
 *  1. 系统权限：Strict 不声明 INTERNET；Standard 默认拒绝外网，按每份 HTML 授权
 *  2. WebView 设置：禁止 file:// 和 content://，不开 JS 桥，不允许自动弹窗；EPUB 章节直接关闭 JS
 *  3. 请求拦截：私有虚拟域名 + 权限允许的 HTTPS 主机，其余 403
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
        "worker-src 'none'",
        "object-src 'none'",
    )

    /** 单页文档（HTML/MD/代码/TXT 模板）：AI 生成的 HTML 依赖内联脚本 */
    val CSP = (COMMON + "script-src 'self' 'unsafe-inline' 'unsafe-eval' data: blob:").joinToString("; ")

    /** EPUB 章节：电子书不需要脚本，一律禁止 */
    val BOOK_CSP = (COMMON + "script-src 'none'").joinToString("; ")

    fun isLocal(url: Uri, host: String = HOST): Boolean = url.scheme == "https" && url.host == host && url.port in setOf(-1, 443)

    /** 章节路径逐段编码（文件名里可能有空格、中文） */
    fun bookUrl(path: String): String =
        "https://$HOST/book/" + path.split('/').joinToString("/") { Uri.encode(it) }
}

const val RENDERER_GONE_MESSAGE = "页面渲染进程已终止（可能是资源不足或渲染异常）"

/** WebView 要显示的内容 */
sealed interface PageSource {
    /** Native-generated HTML at /doc/index.html. CSP overrides are only for trusted app templates. */
    data class Html(val html: String, val csp: String? = null) : PageSource

    /** EPUB 解压目录里的一个章节文件，挂在 /book/<path> */
    data class BookFile(val rootDir: File, val path: String, val fragment: String? = null) : PageSource

    /**
     * v10：文件夹导入的文档，挂在 /bundle/<相对路径>。mainHtml 是要显示的页面（HTML 原文或渲染好的 Markdown），
     * 同一目录树里的 CSS、图片、字体按相对路径从 rootDir 读取。
     */
    data class BundleFile(val rootDir: File, val relPath: String, val mainHtml: String) : PageSource

    /**
     * v11：章节连续滚动。外层页面（FlowShell）挂在 /flow/index.html，每章是一个沙箱 iframe：
     * EPUB 章节从 epubRoot 挂在 /book/，TXT 章节由 txt 按需渲染挂在 /txt/<序号>.html。
     * 外层页面需要脚本（只有我们自己的），书的内容一律不执行脚本。
     */
    data class Flow(
        val shellHtml: String,
        val epubRoot: File? = null,
        val txt: TxtBook? = null,
        val txtTemplate: String? = null,
    ) : PageSource
}

internal fun PageSource.supportsTemplateUpdates(): Boolean =
    this is PageSource.Html || this is PageSource.BundleFile || this is PageSource.Flow

/** v10：页面里的 alert / confirm / prompt，改用我们自己的对话框显示（标题写明来自哪个文档，避免伪装成系统提示） */
sealed interface JsDialogRequest {
    val message: String
    data class Alert(override val message: String, val result: android.webkit.JsResult) : JsDialogRequest
    data class Confirm(override val message: String, val result: android.webkit.JsResult) : JsDialogRequest
    data class Prompt(override val message: String, val default: String, val result: android.webkit.JsPromptResult) : JsDialogRequest
}

private fun noStoreResponse(mime: String?, encoding: String?, csp: String, body: java.io.InputStream?) =
    WebResourceResponse(
        mime, encoding, 200, "OK",
        mapOf("Content-Security-Policy" to csp, "Cache-Control" to "no-store"),
        body,
    )

/** v11：连续滚动的外层页面 */
private class FlowShellPathHandler(private val html: String) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        if (path != "index.html") return null
        return noStoreResponse("text/html", "utf-8", FlowShell.SHELL_CSP, ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
    }
}

private class DocumentPathHandler(private val html: String, private val csp: String) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        if (path != "index.html") return null
        return noStoreResponse("text/html", "utf-8", csp, ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
    }
}

/**
 * v10：从文件夹副本提供文件。路径穿越防护同样交给 InternalStoragePathHandler；
 * 所有响应都带上这份文档权限对应的 CSP。文件夹里的 HTML 在导入时已统一转成 UTF-8。
 */
private class BundlePathHandler(
    context: Context,
    rootDir: File,
    private val mainPath: String,
    private val mainHtml: String,
    private val csp: String,
) : WebViewAssetLoader.PathHandler {
    private val files = WebViewAssetLoader.InternalStoragePathHandler(context, rootDir)

    override fun handle(path: String): WebResourceResponse? {
        if (path == mainPath) {
            return noStoreResponse("text/html", "utf-8", csp, ByteArrayInputStream(mainHtml.toByteArray(Charsets.UTF_8)))
        }
        val res = files.handle(path)?.takeIf { it.data != null } ?: return null
        val lower = path.lowercase()
        val isHtml = lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".xhtml")
        return noStoreResponse(res.mimeType, if (isHtml) "utf-8" else res.encoding, csp, res.data)
    }
}

/** 文件夹副本里的相对路径逐段编码成 URL */
internal fun bundleUrl(host: String, relPath: String): String =
    "https://$host/bundle/" + relPath.split('/').joinToString("/") { Uri.encode(it) }

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
        val text = res.data?.use { CharsetDetector.decode(it.readBytes()).text } ?: return res
        val injected = EpubInjector.inject(text, theme(), readingCss()).toByteArray(Charsets.UTF_8)
        return noStoreResponse(res.mimeType, "utf-8", Sandbox.BOOK_CSP, ByteArrayInputStream(injected))
    }
}

/** v11：连续滚动里 TXT 的一章。在 WebView 的后台线程里按需读取、渲染 */
private class TxtChapterPathHandler(
    private val book: TxtBook,
    private val template: String,
    private val theme: () -> ReaderTheme,
    private val readingCss: () -> String,
) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        val index = path.removeSuffix(".html").toIntOrNull()?.takeIf { path.endsWith(".html") && it in 0 until book.size } ?: return null
        val html = try {
            TxtChapterPage.render(template, book.chapter(index), index, book.size, theme(), readingCss())
        } catch (e: Exception) {
            Log.w(Sandbox.TAG, "txt chapter $index failed", e)
            return null
        }
        return noStoreResponse("text/html", "utf-8", Sandbox.BOOK_CSP, ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
    }
}

private class SandboxClient(
    private val loader: WebViewAssetLoader,
    private val permissions: DocPermissions,
    private val localHost: String,
    private val onHistoryChanged: (Boolean) -> Unit,
    private val onExternalLink: (Uri) -> Unit,
    private val onPageLoaded: (WebView) -> Unit,
    private val onInternalLink: (Uri) -> Boolean,
    private val onRendererGone: (WebView) -> Unit,
    /** v10：页面引用了本地路径下不存在的文件（单文件导入的 HTML 引用同文件夹资源时就是这样） */
    private val onMissingLocal: (String) -> Unit = {},
    private val onPageLoading: () -> Unit = {},
    /** v11：连续滚动。章节 iframe 可以加载 /book/、/txt/ 下的文件；用户点了章节里的链接交给 onInternalLink */
    private val bookFrames: Boolean = false,
) : WebViewClient() {

    // 跑在后台线程，不能直接改 Compose 状态
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (Sandbox.isLocal(url, localHost)) {
            loader.shouldInterceptRequest(url)?.let { return it }
            val path = url.path.orEmpty()
            if ((path.startsWith("/doc/") && path != "/doc/index.html") || path.startsWith("/bundle/")) onMissingLocal(path)
        } else if (url.host != Sandbox.HOST && url.host?.endsWith(".${Sandbox.HOST}") != true &&
            RequestPolicy.allowsNetwork(permissions, url.scheme, url.host)) {
            return null
        }
        Log.w(Sandbox.TAG, "blocked request: ${request.method} $url")
        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked by sandbox", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        if (Sandbox.isLocal(url, localHost)) {
            // The trusted shell never navigates. Even a top-targeted book link cannot replace it.
            if (request.isForMainFrame) return bookFrames || onInternalLink(url)
            val path = url.path.orEmpty()
            if (bookFrames && (path.startsWith("/book/") || path.startsWith("/txt/"))) {
                // 用户点的链接：交给阅读器跳转（换章），iframe 本身不导航；外层页面创建章节 iframe：放行
                if (request.hasGesture()) { onInternalLink(url); return true }
                return false
            }
            // A child frame cannot navigate into private document endpoints.
            return true
        }
        if (url.host == Sandbox.HOST || url.host?.endsWith(".${Sandbox.HOST}") == true) return true
        return when (NavigationPolicy.decide(permissions, request.isForMainFrame, request.hasGesture(), url.scheme)) {
            NavigationPolicy.Decision.OPEN_EXTERNAL -> { onExternalLink(url); true }
            NavigationPolicy.Decision.ALLOW_IN_PLACE -> false
            NavigationPolicy.Decision.BLOCK -> {
                Log.w(Sandbox.TAG, "blocked navigation: $url (gesture=${request.hasGesture()})")
                true
            }
        }
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Log.w(Sandbox.TAG, "renderer gone, didCrash=${detail.didCrash()}")
        // Every affected WebView handles its own callback; renderers may be shared.
        onRendererGone(view)
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageLoading()
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

private suspend fun WebView.restoreRatio(ratio: Float, stillCurrent: () -> Boolean) {
    var last = -1
    var stable = 0
    repeat(30) {
        if (!stillCurrent()) return
        val h = contentHeight
        if (h > 0 && h == last) {
            if (++stable >= 3) {
                scrollTo(0, (ratio * maxScrollPx()).roundToInt())
                return
            }
        } else { stable = 0; last = h }
        delay(100)
    }
    if (stillCurrent()) scrollTo(0, (ratio * maxScrollPx()).roundToInt())
}

private class ReaderAssetsHandler(context: Context) : WebViewAssetLoader.PathHandler {
    private val delegate = WebViewAssetLoader.AssetsPathHandler(context)
    override fun handle(path: String): WebResourceResponse? = delegate.handle(path)?.apply {
        if (path.endsWith(".wasm")) mimeType = "application/wasm"
        if (path.endsWith(".zip")) mimeType = "application/zip"
    }
}

private class ScrollPosition(var ratio: Float, var reporting: Boolean = false, var initialized: Boolean = false, var epoch: Int = 0)

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
    permissions: DocPermissions = DocPermissions.STANDARD,
    storageProfileId: Long? = null,
    onRendererGone: () -> Unit = {},
    handle: WebViewHandle? = null,
    /** v10：每次页面加载完成（用于搜索结果跳转后的高亮） */
    onPageReady: () -> Unit = {},
    onPageLoading: () -> Unit = {},
    /** v10：缺失的本地资源路径（去重后的列表，后台线程收集、主线程回调） */
    onMissingLocal: (List<String>) -> Unit = {},
    /** v10：页面弹窗对话框的标题，写明来源文档 */
    dialogSource: String = "页面",
) {
    val effective = if (source is PageSource.BookFile || source is PageSource.Flow) DocPermissions.STRICT else AppCapabilities.effective(permissions)
    val sessions = remember { mutableMapOf<WebView, DocProfiles.Session>() }
    val touchCleanup = remember { mutableMapOf<WebView, () -> Unit>() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var rendererGone by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageLoads by remember { mutableIntStateOf(0) }
    val position = remember { ScrollPosition(initialRatio.coerceIn(0f, 1f)) }
    val latestOnRatio by rememberUpdatedState(onRatioChanged)
    val latestOnLink by rememberUpdatedState(onInternalLink)
    val latestBookTheme by rememberUpdatedState(bookTheme)
    val latestBookReading by rememberUpdatedState(bookReadingCss)
    val latestGestures by rememberUpdatedState(gestures)
    val latestOnRendererGone by rememberUpdatedState(onRendererGone)
    val latestOnPageReady by rememberUpdatedState(onPageReady)
    val latestOnPageLoading by rememberUpdatedState(onPageLoading)
    val latestOnMissing by rememberUpdatedState(onMissingLocal)
    var jsDialog by remember { mutableStateOf<JsDialogRequest?>(null) }
    var dialogsShown by remember { mutableIntStateOf(0) }
    var dialogsBlocked by remember { mutableStateOf(false) }
    val missing = remember { MissingResourceAccumulator() }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    // One effect handles initial readiness and subsequent changes. In particular, a selection
    // made before page scripts exist is applied once onPageFinished increments pageLoads.
    LaunchedEffect(theme, wrap, reading, webView, pageLoads) {
        val view = webView ?: return@LaunchedEffect
        if (pageLoads == 0) return@LaunchedEffect
        // Capture native position before reflow; onScroll callbacks can lag behind the visual scroll.
        val target = if (position.initialized) view.scrollRatio() else position.ratio
        val epoch = ++position.epoch
        position.reporting = false
        if (source.supportsTemplateUpdates()) {
            val updates = buildList {
                theme?.let { add("window.setTheme && window.setTheme(${MarkdownRenderer.toJsonString(it.id)})") }
                wrap?.let { add("window.setWrap && window.setWrap($it)") }
                reading?.let { add("window.setReading && window.setReading(${MarkdownRenderer.toJsonString(it)})") }
            }
            if (updates.isNotEmpty()) view.evaluateJavascript(updates.joinToString(";"), null)
        }
        // Fresh documents already start at the top. A delayed scrollTo(0, 0) would overwrite
        // early page/script/user scrolling even when there was no saved position to restore.
        // v11：连续滚动的位置由外层页面自己维护（按章节 + 章内比例），这里不按整页比例恢复
        if (target > 0f && source !is PageSource.Flow) view.restoreRatio(target) { position.epoch == epoch }
        if (position.epoch == epoch) {
            position.ratio = view.scrollRatio()
            position.initialized = true
            position.reporting = true
            latestOnRatio(position.ratio)
        }
    }

    // Session removal is the ownership guard: crash cleanup and Compose onRelease can both run.
    fun releaseView(view: WebView) {
        handle?.detach(view)
        val session = sessions.remove(view) ?: return
        val context = view.context
        position.reporting = false
        position.epoch++
        touchCleanup.remove(view)?.invoke()
        view.setOnScrollChangeListener(null)
        view.animate().cancel()
        if (webView === view) webView = null
        (view.parent as? ViewGroup)?.removeView(view)
        try { view.destroy() }
        finally { DocProfiles.release(context, session) }
    }

    if (rendererGone) {
        CenterText(RENDERER_GONE_MESSAGE, modifier)
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val view = WebView(context)
            val session = DocProfiles.attach(view, storageProfileId.takeIf { effective.persistentStorage })
            sessions[view] = session
            try {
                val localHost = session.host
                val builder = WebViewAssetLoader.Builder()
                    .setDomain(localHost)
                    .addPathHandler("/assets/", ReaderAssetsHandler(context))
                val startUrl = when (source) {
                    is PageSource.Html -> {
                        builder.addPathHandler("/doc/", DocumentPathHandler(source.html, source.csp ?: CspPolicy.build(effective)))
                        "https://$localHost/doc/index.html"
                    }
                    is PageSource.BookFile -> {
                        builder.addPathHandler("/book/", EpubPathHandler(context, source.rootDir, { latestBookTheme() }, { latestBookReading() }))
                        Sandbox.bookUrl(source.path).replace(Sandbox.HOST, localHost) + (source.fragment?.let { "#" + Uri.encode(it) } ?: "")
                    }
                    is PageSource.BundleFile -> {
                        builder.addPathHandler(
                            "/bundle/",
                            BundlePathHandler(context, source.rootDir, source.relPath, source.mainHtml, CspPolicy.build(effective)),
                        )
                        bundleUrl(localHost, source.relPath)
                    }
                    is PageSource.Flow -> {
                        builder.addPathHandler("/flow/", FlowShellPathHandler(source.shellHtml))
                        source.epubRoot?.let {
                            builder.addPathHandler("/book/", EpubPathHandler(context, it, { latestBookTheme() }, { latestBookReading() }))
                        }
                        if (source.txt != null && source.txtTemplate != null) {
                            builder.addPathHandler("/txt/", TxtChapterPathHandler(source.txt, source.txtTemplate, { latestBookTheme() }, { latestBookReading() }))
                        }
                        "https://$localHost/flow/index.html"
                    }
                }
                val loader = builder.build()
                view.apply {
                    // v11：连续滚动的外层页面是我们自己的脚本；章节在沙箱 iframe 里，脚本被 sandbox 和 CSP 双重禁止
                    applySandboxSettings(allowJs = source is PageSource.Flow || (source !is PageSource.BookFile && effective.scripts))
                    // v10：页面弹窗。不设置 WebChromeClient 时 alert() 会被静默丢弃，用户以为按钮坏了
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        private fun show(req: JsDialogRequest): Boolean {
                            if (dialogsBlocked || jsDialog != null) {
                                when (req) {
                                    is JsDialogRequest.Alert -> req.result.confirm()
                                    is JsDialogRequest.Confirm -> req.result.cancel()
                                    is JsDialogRequest.Prompt -> req.result.cancel()
                                }
                            } else jsDialog = req
                            return true
                        }
                        // 参数按可空声明：不同 API 级别的 SDK 对这些参数的可空注解不一致
                        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean =
                            result?.let { show(JsDialogRequest.Alert(message.orEmpty().take(2_000), it)) } ?: false
                        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean =
                            result?.let { show(JsDialogRequest.Confirm(message.orEmpty().take(2_000), it)) } ?: false
                        override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: android.webkit.JsPromptResult?): Boolean =
                            result?.let { show(JsDialogRequest.Prompt(message.orEmpty().take(2_000), defaultValue.orEmpty().take(2_000), it)) } ?: false
                        // 离开页面时不让页面拦截
                        override fun onJsBeforeUnload(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                            result?.confirm(); return true
                        }
                    }
                    webViewClient = SandboxClient(
                        loader = loader,
                        permissions = effective,
                        localHost = localHost,
                        onHistoryChanged = { canGoBack = it },
                        onExternalLink = { uri -> openExternal(context, uri) },
                        onPageLoaded = { pageLoads++; latestOnPageReady() },
                        onPageLoading = { latestOnPageLoading() },
                        onMissingLocal = { path ->
                            if (missing.add(path)) post { latestOnMissing(missing.drainNotification()) }
                        },
                        onRendererGone = { dead ->
                            if (sessions.containsKey(dead)) {
                                canGoBack = false
                                rendererGone = true
                                try { releaseView(dead) }
                                finally { latestOnRendererGone() }
                            }
                        },
                        onInternalLink = { uri ->
                            val path = uri.path.orEmpty()
                            if (path.startsWith("/book/")) latestOnLink(path.removePrefix("/book/"), uri.fragment) else false
                        },
                        bookFrames = source is PageSource.Flow,
                    )
                    setOnScrollChangeListener { _, _, _, _, _ ->
                        if (position.reporting) {
                            position.ratio = scrollRatio()
                            latestOnRatio(position.ratio)
                        }
                    }
                    touchCleanup[this] = installReaderGestures(context, onTouch = {
                        // A real touch takes priority over a pending initial/reflow restoration.
                        position.epoch++
                        position.initialized = true
                        position.reporting = true
                        position.ratio = scrollRatio()
                        latestOnRatio(position.ratio)
                    }) { latestGestures }
                    loadUrl(startUrl)
                    webView = this
                    handle?.attach(this)
                }
            } catch (error: Throwable) {
                releaseView(view)
                throw error
            }
        },
        onRelease = ::releaseView,
    )

    jsDialog?.let { req ->
        JsDialog(
            source = dialogSource,
            request = req,
            offerBlock = dialogsShown >= 2,
            onDone = { block ->
                dialogsShown++
                if (block) dialogsBlocked = true
                jsDialog = null
            },
        )
    }
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
private fun WebView.installReaderGestures(context: Context, onTouch: () -> Unit, gestures: () -> ReaderGestures?): () -> Unit {
    var active = true
    var tappedLink = false
    val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!active || !isAttachedToWindow || tappedLink) return false
            val g = gestures() ?: return false
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
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onTouch()
        // Remember the link before WebView navigates; the confirmed callback arrives later.
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            tappedLink = hitTestResult.type in setOf(WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)
        }
        detector.onTouchEvent(event)
        false
    }
    return { active = false; setOnTouchListener(null) }
}

/** Only the bundled trusted editor uses this retained view. Clients capture its session and
 * application context, never an Activity or the departing Compose tree. */
internal fun createRetainedEditorView(context: Context, editor: EditorSession): Pair<WebView, DocProfiles.Session> {
    val app = context.applicationContext
    val view = WebView(app)
    val profile = DocProfiles.attach(view, null)
    try {
        val loader = WebViewAssetLoader.Builder()
            .setDomain(profile.host)
            .addPathHandler("/assets/", ReaderAssetsHandler(app))
            .addPathHandler("/doc/", DocumentPathHandler(editor.html, EditorRenderer.CSP))
            .build()
        view.applySandboxSettings(allowJs = true)
        view.webViewClient = SandboxClient(
            loader = loader,
            permissions = DocPermissions.STANDARD,
            localHost = profile.host,
            onHistoryChanged = {},
            onExternalLink = {},
            onPageLoaded = { editor.pageLoads++ },
            onInternalLink = { true },
            onRendererGone = { editor.rendererGone() },
        )
        view.loadUrl("https://${profile.host}/doc/index.html")
        return view to profile
    } catch (failure: Throwable) {
        try { view.destroy() }
        finally { DocProfiles.release(app, profile) }
        throw failure
    }
}
