package com.example.yhviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Compares the optimized scanner against the original full-input regex on Android's regex runtime. */
@RunWith(AndroidJUnit4::class)
class TxtHeadingNativeParityTest {
    private val legacyHeading = Regex(
        """^[ \t　]*(第[0-9０-９零〇一二两三四五六七八九十百千万]+[章节回卷集部篇]|chapter\s*\d+|序章|序言|楔子|引子|前言|后记|尾声|番外|终章)[^\n]{0,40}$""",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE),
    )

    private fun legacySplit(raw: String): List<TxtChapter> {
        val text = TxtChapters.normalize(raw)
        if (text.isBlank()) return listOf(TxtChapter("正文", ""))
        val matches = legacyHeading.findAll(text).toList()
        // Every case below has two ordinary control headings, so the legacy path does not fall back.
        check(matches.size >= 2)
        val chapters = mutableListOf<TxtChapter>()
        val preface = text.substring(0, matches.first().range.first)
        if (preface.isNotBlank()) chapters += TxtChapter("开头", preface.trim('\n'))
        matches.forEachIndexed { i, match ->
            val start = match.range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val body = text.substring(start, end).trim('\n')
            if (body.isNotBlank()) chapters += TxtChapter(match.value.trim(), body)
        }
        return chapters
    }

    @Test fun lineWindowsMatchLegacyFullTextRegexOnAndroid() {
        val candidates = listOf(
            "Chapter\n12 Cross line", "Chapter \t\u000B\u000C\n12 ASCII space",
            "Chapter\u00A0\n12 Nonbreaking space", "Chapter\u2003\n12 Em space",
            "Chapter\u2028\n12 Line separator", "Chapter\u0085\n12 Next line",
            "Chapter\u2029\n12 Paragraph separator", "Chapter\u000B\n12 Vertical tab",
            "第" + "一".repeat(90) + "章 Long number", "序章\u2028Other text",
            "chapter " + "9".repeat(90), "Chapter\n\n12 Blank lines",
        )
        candidates.forEachIndexed { i, candidate ->
            val raw = "第一章 Start\nbody one\n$candidate\nbody two\n第二章 End\nbody three"
            assertEquals("Android case $i: ${candidate.map { "U+%04X".format(it.code) }}", legacySplit(raw), TxtChapters.split(raw))
        }
    }
}
