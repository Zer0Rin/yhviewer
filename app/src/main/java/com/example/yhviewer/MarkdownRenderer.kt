package com.example.yhviewer

import android.content.Context

/**
 * md → 可显示的 HTML 页面。
 *
 * 思路：Kotlin 不解析 Markdown，只把原文安全地塞进 assets/md/template.html，
 * 真正的渲染交给页面里的 markdown-it（在沙箱 WebView 里执行）。
 *
 * 渲染函数是纯 Kotlin（不碰 Android API），所以能在 JVM 单元测试里直接测。
 */
object MarkdownRenderer {
    private const val TEMPLATE_PATH = "md/template.html"
    const val PLACEHOLDER = "/*MD_JSON*/"

    @Volatile
    private var cachedTemplate: String? = null

    fun template(context: Context): String =
        cachedTemplate ?: context.assets.open(TEMPLATE_PATH)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
            .also { cachedTemplate = it }

    fun render(markdown: String, template: String): String {
        require(PLACEHOLDER in template) { "template is missing $PLACEHOLDER" }
        return template.replace(PLACEHOLDER, toJsonString(markdown)) // 字面量替换，不是正则
    }

    /**
     * 把任意文本编码成 JSON 字符串字面量，并且能安全放进 <script> 标签：
     * 所有 "<" 都编码为 \u003c，所以 "</script>" "<!--" 都没法提前闭合标签。
     * 不用 org.json 是因为它在 JVM 单元测试里是空桩。
     */
    internal fun toJsonString(s: String): String = buildString(s.length + 16) {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '<' -> append("\\u003c")
                ' ' -> append("\\u2028")
                ' ' -> append("\\u2029")
                else -> if (c < ' ') {
                    append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    append(c)
                }
            }
        }
        append('"')
    }
}
