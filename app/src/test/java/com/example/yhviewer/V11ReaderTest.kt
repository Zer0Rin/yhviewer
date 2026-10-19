package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/** v11：TXT 章节索引（大文件快速打开）、连续滚动外层页面、目录快速滚动条 */
class TxtIndexTest {
    @get:Rule val tmp = TemporaryFolder()

    /** v10 的切章实现，原样保留做对照：v11 的逐行扫描必须切出完全一样的章节，已保存的章节号才不会错位 */
    private object Legacy {
        private val HEADING = Regex(
            """^[ \t　]*(第[0-9０-９零〇一二两三四五六七八九十百千万]+[章节回卷集部篇]|chapter\s*\d+|序章|序言|楔子|引子|前言|后记|尾声|番外|终章)[^\n]{0,40}$""",
            setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE),
        )
        fun split(raw: String): List<TxtChapter> {
            val text = raw.replace("\r\n", "\n").replace('\r', '\n')
            if (text.isBlank()) return listOf(TxtChapter("正文", ""))
            val matches = HEADING.findAll(text).toList()
            if (matches.size < 2) return bySize(text)
            val chapters = mutableListOf<TxtChapter>()
            val preface = text.substring(0, matches.first().range.first)
            if (preface.isNotBlank()) chapters += TxtChapter("开头", preface.trim('\n'))
            matches.forEachIndexed { i, m ->
                val bodyStart = m.range.last + 1
                val bodyEnd = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
                val body = text.substring(bodyStart, bodyEnd).trim('\n')
                if (body.isNotBlank()) chapters += TxtChapter(m.value.trim(), body)
            }
            return if (chapters.size >= 2) chapters else bySize(text)
        }
        private fun bySize(text: String): List<TxtChapter> {
            if (text.length <= 8000) return listOf(TxtChapter("正文", text.trim('\n')))
            val parts = mutableListOf<TxtChapter>()
            var start = 0
            while (start < text.length) {
                var end = (start + 8000).coerceAtMost(text.length)
                if (end < text.length) {
                    val nl = text.lastIndexOf('\n', end - 1)
                    if (nl >= start + 4000) end = nl + 1
                    if (text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
                }
                parts += TxtChapter("第 ${parts.size + 1} 部分", text.substring(start, end).trim('\n'))
                start = end
            }
            return parts
        }
    }

    private val lines = listOf(
        "　　他沿着长街慢慢走着，雨水打湿了青石板。", "第一章 风起", "第12章 归来", "  第三百二十回　夜宴", "Chapter 7 The Road",
        "chapter12", "序章", "楔子：旧梦", "番外 一", "前面的路很长，他说。", "后来的事情谁也说不清", "第一章开头的这句话很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长",
        "", "   ", "😀 emoji 行 𝄞", "é 组合字符 é", "مرحبا RTL", "目录", "\t第五章\t缩进", "终章", "尾声",
    )

    private fun randomDoc(r: Random, crlf: Boolean): String {
        val n = r.nextInt(0, 400)
        val sb = StringBuilder()
        if (r.nextInt(5) == 0) sb.append('﻿')
        repeat(n) {
            sb.append(lines[r.nextInt(lines.size)])
            if (r.nextInt(30) == 0) sb.append("长".repeat(r.nextInt(100, 9000))) // 没有换行的超长行
            sb.append(if (crlf && r.nextBoolean()) "\r\n" else if (r.nextInt(50) == 0) "\r" else "\n")
        }
        return sb.toString()
    }

    @Test fun splitMatchesV10Exactly() {
        val r = Random(11)
        repeat(300) { k ->
            val doc = randomDoc(r, crlf = k % 2 == 0)
            assertEquals("case $k", Legacy.split(doc), TxtChapters.split(doc))
        }
        listOf("", "  \n ", "第一章 甲\r\n内容\r\n第二章 乙\r\n内容", "x".repeat(20_000), "😀".repeat(9000)).forEach {
            assertEquals(Legacy.split(it), TxtChapters.split(it))
        }
    }

    @Test fun splitRetainsLegacyUnboundedHeadingsAndWhitespace() {
        listOf(
            "第" + "一".repeat(90) + "章\n正文\n第二章\n正文",
            "Chapter\n12 标题\n正文\nChapter 13\n正文",
            "序章\u2028正文\n第二章\n正文",
            "\t第一章\t标题\n正文\n第二章\n正文",
        ).forEach { assertEquals(Legacy.split(it), TxtChapters.split(it)) }
    }

    @Test fun corruptIndexOrderAndOversizedRangesAreRejected() {
        listOf("5\t10\tYQ==\n0\t5\tYg==\n", "0\t10\tYQ==\n5\t12\tYg==\n", "0\t2147483648\tYQ==\n").forEach {
            assertNull(TxtIndex.decode("YHTXT\t${TxtIndex.VERSION}\t2147483648\t1\n$it", 2147483648, 1))
        }
    }

    @Test fun persistedIndexPreservesExactLegacyTitles() {
        val raw = "第一章\t标题\n正文\nChapter\n12 标题\n正文"
        val file = tmp.newFile("exact-titles").apply { writeText(raw) }
        val idx = File(tmp.root, "exact.idx")
        TxtBook.build(file, file.readBytes(), idx)
        assertEquals(Legacy.split(raw).map { it.title }, TxtBook.open(file, idx).titles)
    }

    @Test fun byteRangesDecodeToTheSameChapters() {
        val r = Random(7)
        repeat(200) { k ->
            val raw = randomDoc(r, crlf = k % 3 == 0)
            val bytes = raw.toByteArray(Charsets.UTF_8)
            val entries = TxtIndex.build(raw)
            val expected = TxtChapters.split(raw)
            assertEquals(expected.map { it.title }, entries.map { it.title })
            entries.forEachIndexed { i, e ->
                val text = TxtChapters.normalize(String(bytes, e.start.toInt(), (e.end - e.start).toInt(), Charsets.UTF_8)).trim('\n')
                assertEquals("case $k chapter $i", expected[i].text, text)
            }
        }
    }

    @Test fun indexFileRoundTripAndStaleness() {
        val entries = listOf(TxtIndex.Entry("第一章\t带制表符", 0, 10), TxtIndex.Entry("第二章", 10, 25))
        val encoded = TxtIndex.encode(entries, 25, 1234)
        val decoded = TxtIndex.decode(encoded, 25, 1234)!!
        assertEquals(listOf("第一章\t带制表符", "第二章"), decoded.map { it.title })
        assertEquals(listOf(0L to 10L, 10L to 25L), decoded.map { it.start to it.end })
        assertNull(TxtIndex.decode(encoded, 26, 1234))   // 副本大小变了
        assertNull(TxtIndex.decode(encoded, 25, 9999))   // 修改时间变了
        assertNull(TxtIndex.decode(encoded.replace("YHTXT\t${TxtIndex.VERSION}", "YHTXT\t0"), 25, 1234))
        assertNull(TxtIndex.decode("YHTXT\t1\t25\t1234\n0\tx\t第一章\n", 25, 1234))
        assertNull(TxtIndex.decode("YHTXT\t1\t25\t1234\n0\t99\t越界\n", 25, 1234))
        assertNull(TxtIndex.decode("", 25, 1234))
    }

    @Test fun bookOpensFromIndexAndReadsChaptersLazily() {
        val raw = (1..300).joinToString("") { "第${it}章 标题$it\r\n" + "　　正文$it 😀 é。\r\n".repeat(20) }
        val file = tmp.newFile("book")
        file.writeText(raw, Charsets.UTF_8)
        val idx = File(tmp.root, "index/book.idx")

        val built = TxtBook.build(file, file.readBytes(), idx)
        assertTrue(idx.exists())
        assertEquals(300, built.size)

        val reopened = TxtBook.open(file, idx)
        val expected = TxtChapters.split(raw)
        assertEquals(expected.map { it.title }, reopened.titles)
        assertEquals(expected[0].text, reopened.text(0))
        assertEquals(expected[299].text, reopened.text(299))
        assertEquals(expected.map { it.text }, reopened.allTexts())

        // 副本被换掉（大小变了）→ 索引作废，重新切
        file.writeText("第一章 甲\n内容\n第二章 乙\n内容", Charsets.UTF_8)
        assertEquals(listOf("第一章 甲", "第二章 乙"), TxtBook.open(file, idx).titles)
    }

    @Test fun invalidUtf8FallsBackToMemory() {
        val file = tmp.newFile("bad")
        val bytes = "第一章 甲\n内容\n第二章 乙\n".toByteArray(Charsets.UTF_8) + byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "尾".toByteArray()
        file.writeBytes(bytes)
        val book = TxtBook.build(file, bytes, File(tmp.root, "bad.idx"))
        assertEquals(listOf("第一章 甲", "第二章 乙"), book.titles)
        assertTrue(book.text(1).endsWith("尾"))
        assertFalse(File(tmp.root, "bad.idx").exists())
    }

    @Test fun largeBookBuildsQuickly() {
        val sb = StringBuilder()
        for (i in 1..4000) {
            sb.append("第").append(i).append("章 风起长街之").append(i).append("\r\n")
            repeat(30) { sb.append("　　他沿着长街慢慢走着，雨水打湿了青石板，远处的灯火一盏一盏亮起来。\r\n") }
        }
        val raw = sb.toString()
        val entries = TxtIndex.build(raw)
        assertEquals(4000, entries.size)
        assertEquals("第4000章 风起长街之4000", entries.last().title)
        assertEquals(raw.toByteArray(Charsets.UTF_8).size.toLong(), entries.last().end)
    }
}

class FlowShellTest {
    private val template = """<html data-theme="/*THEME*/"><script type="application/json">/*PAYLOAD*/</script></html>"""

    @Test fun payloadIsEscapedAndComplete() {
        val html = FlowShell.render(template, ReaderTheme.INK, 3, 1, 0.256f, "fn</script>", listOf("/book/a.xhtml", "/book/</script>.xhtml", "/book/c.xhtml"))
        assertTrue(html.startsWith("<html data-theme=\"ink\">"))
        assertTrue(html.contains("\"count\":3,\"start\":1,\"ratio\":0.2560"))
        assertTrue(html.contains("\"hold\":false"))
        assertEquals(1, Regex("</script>").findAll(html).count())   // 只有模板自己的闭合标签
        assertTrue(html.contains("\"urls\":["))
        val txt = FlowShell.render(template, ReaderTheme.PAPER, 5, 4, Float.NaN, null, null, hold = true)
        assertTrue(txt.contains("\"ratio\":0.0000"))
        assertTrue(txt.contains("\"pattern\":\"/txt/{i}.html\""))
        assertTrue(txt.contains("\"hold\":true"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun startMustBeInRange() { FlowShell.render(template, ReaderTheme.PAPER, 3, 3, 0f, null, null) }

    @Test fun parsesState() {
        assertEquals(12 to 0.5f, FlowShell.parseState("\"12,0.5000\""))
        assertEquals(0 to 1f, FlowShell.parseState("\"0,1.7\""))
        assertNull(FlowShell.parseState("\"\""))
        assertNull(FlowShell.parseState("null"))
        assertNull(FlowShell.parseState(null))
        assertNull(FlowShell.parseState("\"x,0.1\""))
        assertNull(FlowShell.parseState("\"-1,0.1\""))
        assertNull(FlowShell.parseState("\"3,NaN\""))
        assertNull(FlowShell.parseState("not json"))
    }

    @Test fun bookPathEncodesEachSegment() {
        assertEquals("/book/OEBPS/Text/c%201.xhtml", FlowShell.bookPath("OEBPS/Text/c 1.xhtml") { it.replace(" ", "%20") })
    }

    @Test fun txtChapterPageEscapesAndKeepsSearchParagraphs() {
        val tpl = "<title>/*TITLE*/</title><style id=\"yh-reading\">/*READING*/</style><h1>/*TITLE*/</h1><main>/*BODY*/</main><p>/*POSITION*/</p>"
        val ch = TxtChapter("第一章 <b>", "　　甲 & 乙 <script>alert(1)</script>\n\n   \n/*POSITION*/ 原文\n")
        val html = TxtChapterPage.render(tpl, ch, 2, 9, ReaderTheme.PAPER, ":root{--read-scale:1.20}")
        assertTrue(html.contains("<h1>第一章 &lt;b&gt;</h1>"))
        assertTrue(html.contains("<p>甲 &amp; 乙 &lt;script&gt;alert(1)&lt;/script&gt;</p>"))
        assertTrue(html.contains("<p>/*POSITION*/ 原文</p>"))   // 正文里的占位符不会被二次替换
        assertTrue(html.endsWith("<p>3 / 9</p>"))
        assertTrue(html.contains("--read-scale:1.20"))
        assertEquals(2, Regex("<p>").findAll(html.substringAfter("<main>").substringBefore("</main>")).count())
    }

    @Test fun flowChapterVisibleTextMatchesBookSearchRepresentation() {
        val chapter = TxtChapter("第3章 needle", "　　needle 3 / 9\n\n全书完也可以是正文\nneedle")
        val template = File("src/main/assets/" + FlowShell.TXT_CHAPTER).readText()
        val html = TxtChapterPage.render(template, chapter, 2, 9, ReaderTheme.PAPER, "")
        val visible = mutableListOf<String>()
        var inBody = false
        javax.swing.text.html.parser.ParserDelegator().parse(java.io.StringReader(html),
            object : javax.swing.text.html.HTMLEditorKit.ParserCallback() {
                override fun handleStartTag(tag: javax.swing.text.html.HTML.Tag, attrs: javax.swing.text.MutableAttributeSet, pos: Int) {
                    if (tag == javax.swing.text.html.HTML.Tag.BODY) inBody = true
                }
                override fun handleEndTag(tag: javax.swing.text.html.HTML.Tag, pos: Int) {
                    if (tag == javax.swing.text.html.HTML.Tag.BODY) inBody = false
                }
                override fun handleText(data: CharArray, pos: Int) {
                    if (inBody && data.isNotEmpty()) visible += String(data)
                }
            }, true)
        assertEquals(TxtSearchText.rendered(chapter.title, chapter.text), visible.joinToString("\n"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun txtChapterPageRejectsUnsafeCss() {
        TxtChapterPage.render("/*READING*/", TxtChapter("t", "x"), 0, 1, ReaderTheme.PAPER, "</style><script>")
    }

    @Test fun placeholdersAreFilledOnce() {
        assertEquals("a[X]b/*Y*/c/*", Placeholders.fill("a/*X*/b/*Y*/c/*", mapOf("X" to "[X]")))
        assertEquals("/*X*/", Placeholders.fill("/*X*/", mapOf("X" to "/*X*/")))
    }

    @Test fun epubReadingStyleHasStableId() {
        val out = EpubInjector.inject("<html><head></head><body/></html>", ReaderTheme.PAPER)
        assertTrue(out.contains("<style id=\"yh-reading\" type=\"text/css\"></style>"))
    }

    @Test fun continuousIsDefault() {
        assertTrue(ReadingPrefs().continuous)
    }
}

class FastScrollTest {
    @Test fun indexAt() {
        assertEquals(0, FastScroll.indexAt(0f, 100))
        assertEquals(99, FastScroll.indexAt(1f, 100))
        assertEquals(50, FastScroll.indexAt(0.5f, 101))
        assertEquals(0, FastScroll.indexAt(-3f, 100))
        assertEquals(99, FastScroll.indexAt(7f, 100))
        assertEquals(0, FastScroll.indexAt(Float.NaN, 100))
        assertEquals(0, FastScroll.indexAt(0.5f, 0))
    }

    @Test fun thumbFraction() {
        assertEquals(0f, FastScroll.thumbFraction(0, 12, 100), 0f)
        assertEquals(1f, FastScroll.thumbFraction(88, 12, 100), 0f)
        assertEquals(0.5f, FastScroll.thumbFraction(44, 12, 100), 1e-6f)
        assertEquals(0f, FastScroll.thumbFraction(0, 30, 20), 0f)
        assertNotNull(FastScroll.MIN_ITEMS)
    }
}
