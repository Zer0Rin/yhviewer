package com.example.yhviewer

data class TxtChapter(val title: String, val text: String)

/**
 * 把整本 TXT 切成章节。纯 Kotlin，可单元测试。
 *
 * 规则：
 *  - 识别"第X章/节/回/卷/集/部/篇"、"Chapter 12"、序章、楔子、番外等标题行，标题行最多 40 个字符
 *    （正文里以"第一章"开头的长句不会被误判）
 *  - 第一个标题之前有内容 → 单独成"开头"一章
 *  - 正文为空的章节丢弃：很多 TXT 开头带一份目录，目录里的标题行会被识别成一串空章节
 *  - 识别到的章节少于 2 个 → 按大约 8000 字分段（在换行处切，不切断句子）
 */
object TxtChapters {

    private val HEADING = Regex(
        """^[ \t　]*(第[0-9０-９零〇一二两三四五六七八九十百千万]+[章节回卷集部篇]|chapter\s*\d+|序章|序言|楔子|引子|前言|后记|尾声|番外|终章)[^\n]{0,40}$""",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE),
    )

    const val FALLBACK_SIZE = 8_000

    fun split(raw: String): List<TxtChapter> {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')
        if (text.isBlank()) return listOf(TxtChapter("正文", ""))

        val matches = HEADING.findAll(text).toList()
        if (matches.size < 2) return splitBySize(text)

        val chapters = mutableListOf<TxtChapter>()
        val preface = text.substring(0, matches.first().range.first)
        if (preface.isNotBlank()) chapters += TxtChapter("开头", preface.trim('\n'))

        matches.forEachIndexed { i, m ->
            val bodyStart = m.range.last + 1
            val bodyEnd = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val body = text.substring(bodyStart, bodyEnd).trim('\n')
            if (body.isNotBlank()) chapters += TxtChapter(m.value.trim(), body)
        }
        return if (chapters.size >= 2) chapters else splitBySize(text)
    }

    private fun splitBySize(text: String): List<TxtChapter> {
        if (text.length <= FALLBACK_SIZE) return listOf(TxtChapter("正文", text.trim('\n')))
        val parts = mutableListOf<TxtChapter>()
        var start = 0
        while (start < text.length) {
            var end = (start + FALLBACK_SIZE).coerceAtMost(text.length)
            if (end < text.length) {
                val nl = text.lastIndexOf('\n', end - 1)
                if (nl >= start + FALLBACK_SIZE / 2) end = nl + 1
                // Bound even a novel with no line breaks, without splitting a surrogate pair.
                if (text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
            }
            parts += TxtChapter("第 ${parts.size + 1} 部分", text.substring(start, end).trim('\n'))
            start = end
        }
        return parts
    }
}
