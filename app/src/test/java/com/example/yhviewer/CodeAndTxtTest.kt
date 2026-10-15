package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class CodeLanguagesTest {
    @Test fun commonExtensions() {
        assertEquals("kotlin", CodeLanguages.forFileName("Main.kt"))
        assertEquals("python", CodeLanguages.forFileName("train.PY"))
        assertEquals("typescript", CodeLanguages.forFileName("App.tsx"))
        assertEquals("json", CodeLanguages.forFileName("package.json"))
        assertEquals("yaml", CodeLanguages.forFileName("ci.yml"))
        assertEquals("plaintext", CodeLanguages.forFileName("server.log"))
    }

    @Test fun specialFileNames() {
        assertEquals("makefile", CodeLanguages.forFileName("Makefile"))
        assertEquals("bash", CodeLanguages.forFileName("Dockerfile"))
        assertEquals("plaintext", CodeLanguages.forFileName(".gitignore"))
        assertEquals("plaintext", CodeLanguages.forFileName("CMakeLists.txt")) // 文件名优先于 .txt
    }

    @Test fun nonCodeFilesReturnNull() {
        assertNull(CodeLanguages.forFileName("index.html"))
        assertNull(CodeLanguages.forFileName("notes.md"))
        assertNull(CodeLanguages.forFileName("novel.txt"))
        assertNull(CodeLanguages.forFileName("README"))
        assertNull(CodeLanguages.forFileName("book.epub"))
    }

    @Test fun badge() {
        assertEquals("KT", CodeLanguages.badge("Main.kt"))
        assertEquals("JSON", CodeLanguages.badge("a.json"))
        assertEquals("GITI", CodeLanguages.badge(".gitignore"))
    }
}

class CharsetDetectorTest {
    private val gbk = Charset.forName("GBK")

    @Test fun utf8() {
        val r = CharsetDetector.decode("第一章 夜车".toByteArray(Charsets.UTF_8))
        assertEquals(Charsets.UTF_8, r.charset)
        assertEquals("第一章 夜车", r.text)
    }

    @Test fun utf8BomIsStripped() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "abc".toByteArray()
        assertEquals("abc", CharsetDetector.decode(bytes).text)
    }

    @Test fun gbkNovelIsDetected() {
        val text = "第一章 夜车\n　　夜色渐深，城市的灯光一盏盏亮起。"
        val r = CharsetDetector.decode(text.toByteArray(gbk))
        assertEquals("GB18030", r.charset.name())
        assertEquals(text, r.text)
    }

    @Test fun utf16LeWithBom() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "中文".toByteArray(Charsets.UTF_16LE)
        assertEquals("中文", CharsetDetector.decode(bytes).text)
    }

    @Test fun pureAsciiIsUtf8() {
        assertEquals(Charsets.UTF_8, CharsetDetector.decode("hello".toByteArray()).charset)
    }

    @Test fun garbageStillDecodes() {
        // 任何编码都不合法的字节：不能抛异常，至少返回一个字符串
        val r = CharsetDetector.decode(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x80.toByte()))
        assertTrue(r.text.isNotEmpty())
    }
}

class TxtChaptersTest {
    @Test fun splitsByChineseHeadings() {
        val text = "第一章 开始\n正文一\n第二章 继续\n正文二\n"
        val chapters = TxtChapters.split(text)
        assertEquals(listOf("第一章 开始", "第二章 继续"), chapters.map { it.title })
        assertEquals("正文一", chapters[0].text)
    }

    @Test fun prefaceBecomesOwnChapter() {
        val chapters = TxtChapters.split("作者的话\n\n第1章 甲\n内容\n第2章 乙\n内容")
        assertEquals("开头", chapters.first().title)
        assertEquals(3, chapters.size)
    }

    @Test fun tableOfContentsDoesNotCreateEmptyChapters() {
        val text = "第一章 甲\n第二章 乙\n第三章 丙\n\n第一章 甲\n内容甲\n第二章 乙\n内容乙\n第三章 丙\n内容丙"
        val chapters = TxtChapters.split(text)
        assertEquals(3, chapters.size)
        assertTrue(chapters.all { it.text.isNotBlank() })
    }

    @Test fun longSentenceStartingWithHeadingWordIsNotAHeading() {
        val longLine = "第一章节奏太慢了，" + "很多读者在评论区留言说希望作者加快剧情推进的速度。".repeat(2)
        val chapters = TxtChapters.split("第一章 开端\n$longLine\n第二章 发展\n内容")
        assertEquals(2, chapters.size)
        assertTrue(chapters[0].text.contains("节奏太慢"))
    }

    @Test fun englishChapters() {
        val chapters = TxtChapters.split("Chapter 1\nfoo\nCHAPTER 2 The End\nbar")
        assertEquals(listOf("Chapter 1", "CHAPTER 2 The End"), chapters.map { it.title })
    }

    @Test fun noHeadingsFallsBackToSizeAtLineBoundary() {
        val line = "这是一行没有章节标题的正文内容。\n"
        val text = line.repeat(2000)
        val chapters = TxtChapters.split(text)
        assertTrue(chapters.size > 1)
        assertEquals("第 1 部分", chapters[0].title)
        assertTrue(chapters.all { it.text.endsWith("。") })            // 没有把一行切成两半
        assertEquals(text.replace("\n", ""), chapters.joinToString("") { it.text.replace("\n", "") })
    }

    @Test fun windowsLineEndings() {
        val chapters = TxtChapters.split("第一章 甲\r\n内容\r\n第二章 乙\r\n内容")
        assertFalse(chapters[0].title.contains('\r'))
    }

    @Test fun emptyText() {
        assertEquals(1, TxtChapters.split("  \n ").size)
    }
}

class LocatorTest {
    @Test fun roundTrip() {
        val l = Locator(3, 0.4521f)
        assertEquals("3:0.4521", l.encode())
        assertEquals(l, Locator.decode(l.encode()))
    }

    @Test fun badInputFallsBackToStart() {
        listOf(null, "", "abc", "-1:0.5", "2:NaN").forEach {
            assertEquals("input=$it", Locator.START, Locator.decode(it))
        }
    }

    @Test fun ratioIsClamped() {
        assertEquals(1f, Locator.decode("0:7").ratio)
    }

    @Test fun overallProgress() {
        assertEquals(0.5f, Locator(0, 0.5f).overallProgress(1), 1e-4f)
        assertEquals(0.25f, Locator(1, 0f).overallProgress(4), 1e-4f)
        assertEquals(1f, Locator(9, 1f).overallProgress(4), 1e-4f)      // 章节数变少时不越界
    }
}

class PageRenderersTest {
    @Test fun codePayloadIsSafeAndThemed() {
        val t = "<html data-theme=\"${MarkdownRenderer.THEME_PLACEHOLDER}\">$PAYLOAD_PLACEHOLDER"
        val out = CodeRenderer.render("</script>__THEME__", "kotlin", true, t, ReaderTheme.TERMINAL)
        assertTrue(out.startsWith("<html data-theme=\"terminal\">"))
        assertFalse(out.substringAfter(">").contains('<'))
        assertTrue(out.contains("__THEME__"))       // 用户内容里的占位符原样保留
        assertTrue(out.contains("\"wrap\":true"))
    }

    @Test fun txtPayloadContainsPosition() {
        val out = TxtRenderer.render(TxtChapter("第一章", "内容"), 0, 12, PAYLOAD_PLACEHOLDER, ReaderTheme.AUTO)
        assertTrue(out.contains("\"position\":\"1 / 12\""))
    }
}

class TxtLongLineTest {
    @Test fun fallbackStillSplitsWhenAFileHasNoNewlines() {
        val text = "长段落😀".repeat(6_000)
        val chapters = TxtChapters.split(text)
        assertTrue(chapters.size > 1)
        assertTrue(chapters.all { it.text.length <= TxtChapters.FALLBACK_SIZE + 1 })
        assertEquals(text, chapters.joinToString("") { it.text })
        assertTrue(chapters.none { it.text.firstOrNull()?.isLowSurrogate() == true || it.text.lastOrNull()?.isHighSurrogate() == true })
    }
}
