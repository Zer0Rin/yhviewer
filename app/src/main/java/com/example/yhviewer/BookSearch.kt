package com.example.yhviewer

/*
 * v10：文内搜索。
 *
 *  - 当前页 / 当前章：交给 WebView.findAllAsync（原生实现，EPUB 禁用 JS 也能用），高亮并上下跳转
 *  - 全书（TXT / EPUB）：WebView 一次只加载一章，所以在 Kotlin 里逐章搜索纯文本，列出结果，
 *    点结果跳到那一章后再用 findAllAsync 高亮
 *
 * 本文件是纯逻辑，JVM 可测。
 */

data class SearchHit(val chapter: Int, val offset: Int, val snippet: String, val matchStart: Int, val matchLength: Int)

object BookSearch {
    const val MAX_HITS = 500
    private const val RADIUS = 24

    /**
     * 在每章文本里找 query（忽略大小写），按章节和位置排序，最多 maxHits 条。
     * snippet 是命中处前后各约 24 个字符，换行压成空格；matchStart / matchLength 指向 snippet 里的命中部分。
     */
    fun search(chapterTexts: List<String>, query: String, maxHits: Int = MAX_HITS): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val hits = ArrayList<SearchHit>()
        for ((index, text) in chapterTexts.withIndex()) {
            var from = 0
            while (hits.size < maxHits) {
                val at = text.indexOf(q, from, ignoreCase = true)
                if (at < 0) break
                hits += hit(index, text, at, q.length)
                from = at + q.length
            }
            if (hits.size >= maxHits) break
        }
        return hits
    }

    private fun hit(chapter: Int, text: String, at: Int, len: Int): SearchHit {
        val start = (at - RADIUS).coerceAtLeast(0)
        val end = (at + len + RADIUS).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        val raw = text.substring(start, end).replace(WS, " ")
        // 压缩空白后命中位置可能前移：在压缩后的片段里重新定位
        val before = text.substring(start, at).replace(WS, " ")
        return SearchHit(chapter, at, prefix + raw + suffix, prefix.length + before.length, len)
    }

    private val WS = Regex("\\s+")
}

/** XHTML 章节 → 纯文本（只用于全书搜索，不用于显示） */
object XhtmlText {
    private val DROP_BLOCKS = Regex("<(head|script|style|svg|math)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val COMMENTS = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val CDATA = Regex("<!\\[CDATA\\[.*?]]>", RegexOption.DOT_MATCHES_ALL)
    private val BLOCK_TAGS = Regex("</?(p|div|br|h[1-6]|li|tr|section|article|blockquote|pre|dt|dd)\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val TAGS = Regex("<[^>]*>")
    private val NUMERIC = Regex("&#(x[0-9a-fA-F]{1,6}|[0-9]{1,7});")
    private val NAMED = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")
    private val NAMED_RE = Regex("&(amp|lt|gt|quot|apos|nbsp);")
    private val BLANK_LINES = Regex("[ \\t\\u00A0]*\\n[\\s]*")
    private val SPACES = Regex("[ \\t\\u00A0]+")

    fun extract(xhtml: String): String {
        var s = COMMENTS.replace(xhtml, " ")
        s = CDATA.replace(s, " ")
        s = DROP_BLOCKS.replace(s, " ")
        s = BLOCK_TAGS.replace(s, "\n")
        s = TAGS.replace(s, "")
        s = NUMERIC.replace(s) { m ->
            val v = m.groupValues[1]
            val code = if (v.startsWith("x") || v.startsWith("X")) v.drop(1).toIntOrNull(16) else v.toIntOrNull()
            if (code != null && Character.isValidCodePoint(code) && code != 0) String(Character.toChars(code)) else " "
        }
        // 先解其他实体再解 &amp;：避免 "&amp;lt;" 被解成 "<"
        s = NAMED_RE.replace(s) { m -> if (m.groupValues[1] == "amp") "\u0000AMP\u0000" else NAMED.getValue(m.groupValues[1]) }
        s = s.replace("\u0000AMP\u0000", "&")
        s = SPACES.replace(s, " ")
        s = BLANK_LINES.replace(s, "\n")
        return s.trim()
    }
}
