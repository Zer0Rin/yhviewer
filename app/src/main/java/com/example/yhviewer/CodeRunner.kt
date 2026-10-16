package com.example.yhviewer

/**
 * JavaScript / Python 执行沙箱（"运行"按钮）。阅读时代码永远不执行，只有用户主动点运行，
 * 才在一个新建的 WebView 里、由 Web Worker 执行。
 *
 * 隔离来自四处：
 *  1. Web Worker：没有 DOM，和页面不同线程。死循环卡不住界面，超时直接 terminate
 *  2. 运行页专用 CSP：connect-src 'none'（fetch / XHR / WebSocket 全拦），script-src 只放行 blob:
 *     和页面自己的脚本（importScripts 外部地址被拦）。blob: Worker 继承页面的 CSP
 *  3. 现有沙箱的请求拦截和 WebView 设置（不能读文件、没有 JS 桥）
 *  4. 每次运行一个新 WebView + 临时会话，关掉面板即销毁，存储不跨次保留
 *
 * render 是纯函数，可在 JVM 单元测试里测。
 */
object CodeRunner {
    const val TEMPLATE = "run/runner.html"
    const val DEFAULT_TIMEOUT_MS = 5_000
    const val LONG_TIMEOUT_MS = 30_000
    const val MAX_LINES = 1_000
    const val MAX_CHARS = 200_000

    enum class Lang(val id: String) { JAVASCRIPT("javascript"), PYTHON("python") }

    private fun csp(connect: String, wasm: Boolean) = listOf(
        "default-src 'none'",
        "script-src 'self' 'unsafe-inline' 'unsafe-eval' ${if (wasm) "'wasm-unsafe-eval' " else ""}blob:",
        "worker-src blob:",
        "style-src 'self' 'unsafe-inline'",
        "img-src 'self' data:",
        "font-src 'self' data:",
        "connect-src $connect",
        "frame-src 'none'",
        "form-action 'none'",
        "base-uri 'none'",
        "object-src 'none'",
    ).joinToString("; ")

    /** Compatibility policy for existing JavaScript callers. */
    val CSP = cspFor(Lang.JAVASCRIPT)

    fun cspFor(lang: Lang): String = csp(if (lang == Lang.PYTHON) "'self'" else "'none'", lang == Lang.PYTHON)

    fun runnableLang(lang: String?, pythonAvailable: Boolean): Lang? = when (lang) {
        "javascript" -> Lang.JAVASCRIPT
        "python" -> if (pythonAvailable) Lang.PYTHON else null
        else -> null
    }

    /** Compatibility API; flavor-aware callers use runnableLang. */
    fun isRunnable(lang: String?): Boolean = lang == "javascript"

    fun render(code: String, template: String, theme: ReaderTheme, timeoutMs: Int = DEFAULT_TIMEOUT_MS, lang: Lang = Lang.JAVASCRIPT): String {
        require(PAYLOAD_PLACEHOLDER in template) { "template is missing $PAYLOAD_PLACEHOLDER" }
        val j = MarkdownRenderer::toJsonString
        val timeout = timeoutMs.coerceIn(500, LONG_TIMEOUT_MS)
        val payload = """{"lang":${j(lang.id)},"code":${j(code)},"timeoutMs":$timeout,"maxLines":$MAX_LINES,"maxChars":$MAX_CHARS}"""
        return template
            .replace(MarkdownRenderer.THEME_PLACEHOLDER, theme.id)
            .replace(PAYLOAD_PLACEHOLDER, payload)
    }
}
