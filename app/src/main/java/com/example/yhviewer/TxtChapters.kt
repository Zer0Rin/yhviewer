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
    private val REGEX_SPACES = Regex("""\s*""")
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

    /**
     * 标题行：start = 行首，end = 匹配结束处（行尾）。
     *
     * 语义和旧的"对全文 findAll 多行正则"完全一致，但每个候选行只把**这一行**交给正则：
     * Android 的 ICU Matcher 每次设置输入（包括 region()）都会把整个输入复制一份到 native，
     * 对全文逐个候选 region() 是 O(候选数 × 全文长度)，30 MB 的书要几十秒。
     *
     * 窗口为什么够：除了 chapter\s* 里的 \s，正则里没有能跨过 '\n' 的部分（[^\n]、$ 都停在 '\n' 前）。
     * 所以窗口取到下一个 '\n'；只有"chapter 后面全是空白"时才往下扩展，直到遇到第一个非空白字符所在的行。
     */
    private fun headings(text: String): List<TxtRange> {
        val out = ArrayList<TxtRange>()
        val pattern = HEADING_LINE.toPattern()
        val n = text.length
        var lineStart = 0
        var consumed = 0
        while (lineStart < n) {
            var k = lineStart
            while (k < n && (text[k] == ' ' || text[k] == '\t' || text[k] == '　')) k++
            if (lineStart >= consumed && k < n && text[k] in HEADING_STARTS) {
                var end = text.indexOf('\n', k).let { if (it < 0) n else it }
                var m = pattern.matcher(text.substring(lineStart, end))
                var found = m.lookingAt()
                if (!found && text.regionMatches(k, "chapter", 0, 7, ignoreCase = true) && onlyRegexSpace(text, k + 7, end)) {
                    // chapter 后面这一行只剩空白：\s* 可以跨行，扩到第一个非空白字符所在的行
                    while (end < n) {
                        val next = text.indexOf('\n', end + 1)
                        val nextEnd = if (next < 0) n else next
                        // Check each added slice once; a long run of blank lines stays linear.
                        val allSpace = onlyRegexSpace(text, end, nextEnd)
                        end = nextEnd
                        if (!allSpace) break
                    }
                    m = pattern.matcher(text.substring(lineStart, end))
                    found = m.lookingAt()
                }
                if (found) {
                    val matchEnd = lineStart + m.end()
                    out += TxtRange(m.group().trim(), lineStart, matchEnd)
                    consumed = matchEnd
                }
            }
            // 和 Java MULTILINE 的 ^ 一致：\n、\u0085、\u2028、\u2029 之后都算行首
            while (lineStart < n) {
                val c = text[lineStart]
                if (c == '\n' || c == '\u0085' || c == '\u2028' || c == '\u2029') break
                lineStart++
            }
            lineStart++
        }
        return out
    }

    /** Use the active runtime's regex whitespace class (Android ICU differs from desktop Java). */
    private fun onlyRegexSpace(text: String, from: Int, to: Int): Boolean {
        return REGEX_SPACES.matches(text.substring(from, to))
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
