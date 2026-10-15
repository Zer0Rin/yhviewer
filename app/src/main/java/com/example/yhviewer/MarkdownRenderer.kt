package com.example.yhviewer

import android.content.Context

/**
 * md → 可显示的 HTML 页面。
 * Kotlin 只做两次字面量替换：主题 id、Markdown 原文（JSON 编码）。渲染交给页面里的 markdown-it。
 */
object MarkdownRenderer {
    private const val TEMPLATE_PATH = "md/template.html"
    const val PLACEHOLDER = "/*MD_JSON*/"
    const val THEME_PLACEHOLDER = "__THEME__"

    @Volatile
    private var cachedTemplate: String? = null

    fun template(context: Context): String =
        cachedTemplate ?: context.assets.open(TEMPLATE_PATH)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
            .also { cachedTemplate = it }

    fun render(markdown: String, template: String, theme: ReaderTheme = ReaderTheme.AUTO): String {
        require(PLACEHOLDER in template) { "template is missing $PLACEHOLDER" }
        // 顺序很重要：先替换主题，再塞入用户内容。
        // 反过来的话，文档里恰好写了 "__THEME__" 也会被替换掉，用户内容就被篡改了。
        // theme.id 来自枚举（白名单），不可能注入任意字符串。
        return template
            .replace(THEME_PLACEHOLDER, theme.id)
            .replace(PLACEHOLDER, toJsonString(markdown))
    }

    /** Escape every '<' so untrusted Markdown cannot close its script data block. */
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
