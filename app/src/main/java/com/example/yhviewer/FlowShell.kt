package com.example.yhviewer

import java.util.Locale

/**
 * v11：章节连续滚动（见 assets/book/flow.html 顶部的说明）。
 *
 * 这里只有纯函数：生成外层页面、TXT 章节页，解析页面状态。可在 JVM 单元测试里测。
 */
object FlowShell {
    const val TEMPLATE = "book/flow.html"
    const val TXT_CHAPTER = "book/txt-chapter.html"
    const val TXT_PATTERN = "/txt/{i}.html"

    /**
     * 外层页面的 CSP：只运行页面自带的内联脚本（模板是我们自己的，不含书的内容），
     * 只能嵌入同源的章节页。章节页各自带 BOOK_CSP（script-src 'none'），iframe 又不给 allow-scripts。
     */
    val SHELL_CSP = listOf(
        "default-src 'none'",
        "script-src 'unsafe-inline'",
        "style-src 'self' 'unsafe-inline'",
        "frame-src 'self'",
        "img-src 'self' data:",
        "font-src 'self' data:",
        "connect-src 'none'",
        "form-action 'none'",
        "base-uri 'none'",
        "worker-src 'none'",
        "object-src 'none'",
    ).joinToString("; ")

    /**
     * @param urls EPUB：每章的同源路径（"/book/OEBPS/c1.xhtml"）；TXT 传 null，用 [TXT_PATTERN]
     */
    fun render(
        template: String,
        theme: ReaderTheme,
        count: Int,
        start: Int,
        ratio: Float,
        anchor: String?,
        urls: List<String>?,
        /** 一开始就暂停补章（搜索结果跳转：先在目标章里定位第 n 个结果） */
        hold: Boolean = false,
    ): String {
        require(count > 0 && start in 0 until count) { "bad chapter range" }
        require(urls == null || urls.size == count) { "urls must match count" }
        val j = MarkdownRenderer::toJsonString
        val payload = buildString {
            append("{\"count\":").append(count)
            append(",\"start\":").append(start)
            append(",\"ratio\":").append(String.format(Locale.ROOT, "%.4f", if (ratio.isFinite()) ratio.coerceAtLeast(0f) else 0f))
            append(",\"anchor\":").append(anchor?.let(j) ?: "null")
            append(",\"hold\":").append(hold)
            if (urls != null) {
                append(",\"urls\":[")
                urls.forEachIndexed { i, u -> if (i > 0) append(','); append(j(u)) }
                append(']')
            } else {
                append(",\"pattern\":").append(j(TXT_PATTERN))
            }
            append('}')
        }
        return Placeholders.fill(template, mapOf("THEME" to theme.id, "PAYLOAD" to payload))
    }

    /** YH.state() 的返回值（evaluateJavascript 给的是 JSON 字符串）："章节,比例"；未就绪或格式不对 → null */
    fun parseState(json: String?): Pair<Int, Float>? {
        val s = runCatching { JsonStrings.decodeStringOrNull(json) }.getOrNull() ?: return null
        val parts = s.split(',')
        if (parts.size != 2) return null
        val chapter = parts[0].toIntOrNull() ?: return null
        val ratio = parts[1].toFloatOrNull()?.takeIf { it.isFinite() } ?: return null
        if (chapter < 0) return null
        return chapter to ratio.coerceAtLeast(0f)
    }

    /** EPUB 章节路径 → 外层页面里用的同源路径（每段 URL 编码；encode 由调用方传入，JVM 测试可替换） */
    fun bookPath(path: String, encode: (String) -> String): String =
        "/book/" + path.split('/').joinToString("/") { encode(it) }
}

/** v11：TXT 的一章渲染成独立页面，给连续滚动的 iframe 用。没有脚本 */
object TxtChapterPage {
    fun render(template: String, chapter: TxtChapter, index: Int, count: Int, theme: ReaderTheme, readingCss: String): String {
        require('<' !in readingCss) { "Unsafe reading CSS" }
        val body = StringBuilder(chapter.text.length + chapter.text.length / 8)
        TxtSearchText.paragraphs(chapter.text).forEach { body.append("<p>").append(escape(it)).append("</p>\n") }
        return Placeholders.fill(template, mapOf(
            "THEME" to theme.id,
            "READING" to readingCss,
            "TITLE" to escape(chapter.title),
            "BODY" to body.toString(),
            "POSITION" to "${index + 1} / $count",
        ))
    }

    internal fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }
}

/**
 * 模板占位符 /*NAME*/ 一遍替换：只在模板里找占位符，插进去的内容不会再被扫描，
 * 所以书的正文里恰好写着 "/*BODY*/" 也不会被二次替换。未知占位符原样保留。
 */
internal object Placeholders {
    fun fill(template: String, values: Map<String, String>): String {
        val out = StringBuilder(template.length + values.values.sumOf { it.length })
        var i = 0
        while (i < template.length) {
            val open = template.indexOf("/*", i)
            if (open < 0) { out.append(template, i, template.length); break }
            val close = template.indexOf("*/", open + 2)
            if (close < 0) { out.append(template, i, template.length); break }
            val name = template.substring(open + 2, close)
            val value = values[name]
            if (value != null) {
                out.append(template, i, open).append(value)
                i = close + 2
            } else {
                out.append(template, i, open + 2)
                i = open + 2
            }
        }
        return out.toString()
    }
}
