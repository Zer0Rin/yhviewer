package com.example.yhviewer

data class TxtChapter(val title: String, val text: String)

/** 一章在"规范化文本"（换行统一成 \n）里的范围；正文 = substring(start, end).trim('\n') */
internal data class TxtRange(val title: String, val start: Int, val end: Int)

/**
 * 把整本 TXT 切成章节。纯 Kotlin，可单元测试。
 *
 * 规则：
 *  - 识别"第X章/节/回/卷/集/部/篇"、"Chapter 12"、序章、楔子、番外等标题行，标题行最多 40 个字符
 *    （正文里以"第一章"开头的长句不会被误判）
 *  - 第一个标题之前有内容 → 单独成"开头"一章
 *  - 正文为空的章节丢弃：很多 TXT 开头带一份目录，目录里的标题行会被识别成一串空章节
 *  - 识别到的章节少于 2 个 → 按大约 8000 字分段（在换行处切，不切断句子）
 *
 * v11：逐行扫描代替对全文跑多行正则。先看行首第一个非空白字符，只有可能是标题的行才跑正则。
 * 切分结果和 v10 一致（TxtIndexTest 对照旧实现），已保存的章节号不会错位。
 */
object TxtChapters {

    private val HEADING_LINE = Regex(
        """^[ \t　]*(第[0-9０-９零〇一二两三四五六七八九十百千万]+[章节回卷集部篇]|chapter\s*\d+|序章|序言|楔子|引子|前言|后记|尾声|番外|终章)[^\n]{0,40}$""",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE),
    )
    /** 标题行去掉行首空白后，第一个字符只可能是这些 */
    private const val HEADING_STARTS = "第cC序楔引前后尾番终"
    const val FALLBACK_SIZE = 8_000

    fun normalize(raw: String): String =
        if ('\r' in raw) raw.replace("\r\n", "\n").replace('\r', '\n') else raw

    fun split(raw: String): List<TxtChapter> {
        val text = normalize(raw)
        return ranges(text).map { TxtChapter(it.title, text.substring(it.start, it.end).trim('\n')) }
    }

    /** text 必须已经 normalize */
    internal fun ranges(text: String): List<TxtRange> {
        if (text.isBlank()) return listOf(TxtRange("正文", 0, 0))

        val heads = headings(text)
        if (heads.size < 2) return bySize(text)

        val out = ArrayList<TxtRange>(heads.size + 1)
        if (!isBlank(text, 0, heads[0].start)) out += TxtRange("开头", 0, heads[0].start)
        heads.forEachIndexed { i, h ->
            val bodyStart = h.end
            val bodyEnd = if (i + 1 < heads.size) heads[i + 1].start else text.length
            if (!isBlank(text, bodyStart, bodyEnd)) out += TxtRange(h.title, bodyStart, bodyEnd)
        }
        return if (out.size >= 2) out else bySize(text)
    }

    /** 标题行：start = 行首，end = 行尾（换行符的位置） */
    private fun headings(text: String): List<TxtRange> {
        val out = ArrayList<TxtRange>()
        var lineStart = 0
        var consumed = 0
        while (lineStart < text.length) {
            var k = lineStart
            while (k < text.length && (text[k] == ' ' || text[k] == '\t' || text[k] == '　')) k++
            if (lineStart >= consumed && k < text.length && text[k] in HEADING_STARTS) {
                // Match the original multiline expression at candidate starts. In particular,
                // chapter\s* may span lines and neither the numeric part nor its whitespace is bounded.
                HEADING_LINE.matchAt(text, lineStart)?.let { m ->
                    out += TxtRange(m.value.trim(), m.range.first, m.range.last + 1)
                    consumed = m.range.last + 1
                }
            }
            while (lineStart < text.length && text[lineStart] !in "\n\u0085\u2028\u2029") lineStart++
            lineStart++
        }
        return out
    }

    private fun isBlank(text: String, from: Int, to: Int): Boolean {
        for (i in from until to) if (!text[i].isWhitespace()) return false
        return true
    }

    private fun bySize(text: String): List<TxtRange> {
        if (text.length <= FALLBACK_SIZE) return listOf(TxtRange("正文", 0, text.length))
        val parts = mutableListOf<TxtRange>()
        var start = 0
        while (start < text.length) {
            var end = (start + FALLBACK_SIZE).coerceAtMost(text.length)
            if (end < text.length) {
                val nl = text.lastIndexOf('\n', end - 1)
                if (nl >= start + FALLBACK_SIZE / 2) end = nl + 1
                // Bound even a novel with no line breaks, without splitting a surrogate pair.
                if (text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
            }
            parts += TxtRange("第 ${parts.size + 1} 部分", start, end)
            start = end
        }
        return parts
    }
}
