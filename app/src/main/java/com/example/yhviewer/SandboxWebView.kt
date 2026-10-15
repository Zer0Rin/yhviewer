package com.example.yhviewer

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream

/**
 * 沙箱的四道门（纵深防御，任何一道失效，其他几道仍然挡得住）：
 *
 *  1. 系统权限：Manifest 不声明 INTERNET → 整个 App 没有网络
 *  2. WebView 设置：禁止访问 file:// 和 content://，不开 JS 桥、不允许自动弹窗
 *  3. 请求拦截：shouldInterceptRequest 只放行"我们自己的虚拟域名"，其余一律 403
 *  4. CSP 响应头：浏览器内核层面禁止联网（connect-src 'none'）、禁止外部脚本/图片/iframe
 *
 * 另外：外部链接只有"用户亲手点击"才交给系统浏览器，脚本自动跳转一律拦截。
 */
object Sandbox {
    const val TAG = "Sandbox"

    // androidx.webkit 预留的虚拟域名，永远不会解析到真实服务器
    const val HOST = "appassets.androidplatform.net"
    const val DOC_URL = "https://$HOST/doc/index.html"

    // 'self' = 上面的虚拟域名。inline 脚本和样式要放行，否则 AI 生成的单文件 HTML 基本跑不起来
    val CSP = listOf(
        "default-src 'none'",
        "script-src 'self' 'unsafe-inline' 'unsafe-eval' data: blob:",
        "style-src 'self' 'unsafe-inline' data:",
        "img-src 'self' data: blob:",
        "font-src 'self' data:",
        "media-src 'self' data: blob:",
        "connect-src 'none'",
        "frame-src 'none'",
        "form-action 'none'",
        "base-uri 'none'",
    ).joinToString("; ")
}

/** 把读出来的文档当作虚拟域名下的 /doc/index.html 提供，并附带 CSP 响应头 */
private class DocumentPathHandler(private val html: String) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        if (path != "index.html") return null // 相对路径资源暂不支持（见回复说明）
        return WebResourceResponse(
            "text/html", "utf-8", 200, "OK",
            mapOf(
                "Content-Security-Policy" to Sandbox.CSP,
                "Cache-Control" to "no-store",
            ),
            ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)),
        )
    }
}

private class SandboxClient(
    private val loader: WebViewAssetLoader,
    private val onHistoryChanged: (Boolean) -> Unit,
    private val onExternalLink: (Uri) -> Unit,
) : WebViewClient() {

    // 注意：这个方法跑在后台线程，不能直接改 Compose 状态
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.host == Sandbox.HOST) {
            loader.shouldInterceptRequest(url)?.let { return it }
        }
        Log.w(Sandbox.TAG, "blocked request: ${request.method} $url")
        return WebResourceResponse(
            "text/plain", "utf-8", 403, "Blocked by sandbox",
            emptyMap(), ByteArrayInputStream(ByteArray(0)),
        )
    }

    // 页面导航（点链接、location = ...）都会先经过这里；返回 true = 我们接管，WebView 不加载
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        if (url.host == Sandbox.HOST) return false

        val userClicked = request.isForMainFrame && request.hasGesture()
        if (userClicked && url.scheme in setOf("http", "https", "mailto")) {
            onExternalLink(url)
        } else {
            Log.w(Sandbox.TAG, "blocked navigation: $url (gesture=${request.hasGesture()})")
        }
        return true
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        onHistoryChanged(view.canGoBack())
    }
}

// 这里有意开启 JS：AI 生成的页面依赖它；风险由上面的拦截和 CSP 兜住
@SuppressLint("SetJavaScriptEnabled")
private fun WebView.applySandboxSettings() {
    settings.apply {
        javaScriptEnabled = true
        allowFileAccess = false          // 禁止 file://
        allowContentAccess = false       // 禁止 content://
        domStorageEnabled = true         // 很多页面用 localStorage，关掉会直接报错；靠每次清空做隔离
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        setGeolocationEnabled(false)
        mediaPlaybackRequiresUserGesture = true
        cacheMode = WebSettings.LOAD_NO_CACHE
    }
    CookieManager.getInstance().setAcceptCookie(false)
    // 不调用 addJavascriptInterface：页面拿不到任何 Kotlin 对象
}

private fun openExternal(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (e: ActivityNotFoundException) {
        Log.w(Sandbox.TAG, "no app to open $uri")
    }
}

/** 替换 MainActivity 里原来的 HtmlView，签名不变，ReaderApp 不用改 */
@Composable
fun HtmlView(html: String, modifier: Modifier = Modifier) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            // 所有文档共用一个虚拟域名 = 同一个 origin，所以每次打开前清空存储，
            // 防止 A 文档读到 B 文档写进 localStorage 的数据
            WebStorage.getInstance().deleteAllData()

            val loader = WebViewAssetLoader.Builder()
                .setDomain(Sandbox.HOST)
                .addPathHandler("/doc/", DocumentPathHandler(html))
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()

            WebView(context).apply {
                applySandboxSettings()
                webViewClient = SandboxClient(
                    loader = loader,
                    onHistoryChanged = { canGoBack = it },
                    onExternalLink = { uri -> openExternal(context, uri) },
                )
                loadUrl(Sandbox.DOC_URL)
                webView = this
            }
        },
        onRelease = { it.destroy() },
    )
}
