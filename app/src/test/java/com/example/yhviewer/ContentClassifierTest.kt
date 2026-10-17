package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentClassifierTest {

    private fun kind(name: String, type: DocType, text: String, size: Long = text.length.toLong()) =
        ContentClassifier.classify(name, type, size, text)

    @Test fun byTypeAndExtension() {
        assertEquals(ContentKind.EBOOK, kind("a.epub", DocType.EPUB, ""))
        assertEquals(ContentKind.WEB, kind("a.html", DocType.HTML, "<p>hi</p>"))
        assertEquals(ContentKind.NOTE, kind("a.md", DocType.MARKDOWN, "hello"))
        assertEquals(ContentKind.CODE, kind("Main.kt", DocType.CODE, "fun main() {}"))
        assertEquals(ContentKind.DATA, kind("a.json", DocType.CODE, "{\"a\":1}"))
        assertEquals(ContentKind.DATA, kind("config.yaml", DocType.CODE, "a: 1"))
    }

    @Test fun contentBeatsWrongExtension() {
        // .txt 里存的是整页 HTML
        assertEquals(ContentKind.WEB, kind("page.txt", DocType.TXT, "<!-- saved -->\n<!DOCTYPE html><html><body>x</body></html>"))
        // 没有扩展名的 JSON / CSV
        assertEquals(ContentKind.DATA, kind("response", DocType.UNKNOWN, "  [{\"id\": 1}, {\"id\": 2}]\n"))
        assertEquals(ContentKind.DATA, kind("export.txt", DocType.TXT, "name,age,city\n张三,20,上海\n李四,30,北京\n"))
        // 没有扩展名的 Markdown 笔记
        val md = "# 周报\n\n## 完成\n- 修复登录\n- 加了测试\n- 发布 0.4\n\n## 下周\n见 [计划](plan.md) 和 [看板](board.md)\n"
        assertEquals(ContentKind.NOTE, kind("notes", DocType.UNKNOWN, md))
    }

    @Test fun codeIsNeverSniffedAsCsv() {
        val py = "foo(a, b)\nbar(c, d)\nbaz(e, f)\n"
        assertEquals(ContentKind.CODE, kind("x.py", DocType.CODE, py))
    }

    @Test fun novelsAndShortText() {
        val chapters = "第一章 出发\n车窗外的灯光渐渐远去。\n\n第二章 抵达\n雾还没有散。\n"
        assertEquals(ContentKind.NOVEL, kind("夜车.txt", DocType.TXT, chapters))
        assertEquals(ContentKind.NOVEL, kind("Chapter.txt", DocType.TXT, "Chapter 1\nfoo\nChapter 2\nbar"))
        // 没识别出章节，但足够长
        assertEquals(ContentKind.NOVEL, kind("long.txt", DocType.TXT, "很长的正文。", size = 200_000))
        assertEquals(ContentKind.TEXT, kind("todo.txt", DocType.TXT, "买牛奶\n交电费\n"))
    }

    @Test fun jsonHeuristic() {
        assertTrue(ContentClassifier.looksLikeJson("{}"))
        assertTrue(ContentClassifier.looksLikeJson("\n [1, 2, 3] \n"))
        assertFalse(ContentClassifier.looksLikeJson("[链接](http://x) 开头的 Markdown"))
        assertFalse(ContentClassifier.looksLikeJson("{ 未闭合"))
        // 样本被截断（达到 SAMPLE_CHARS）时不要求闭合
        val truncated = "[" + "1,".repeat(ContentClassifier.SAMPLE_CHARS)
        assertTrue(ContentClassifier.looksLikeJson(truncated.take(ContentClassifier.SAMPLE_CHARS)))
    }

    @Test fun csvHeuristic() {
        assertTrue(ContentClassifier.looksLikeCsv("a\tb\n1\t2\n3\t4"))
        assertFalse(ContentClassifier.looksLikeCsv("a,b\n1,2"))                  // 少于 3 行
        assertFalse(ContentClassifier.looksLikeCsv("Hello, world.\nNo commas here\nOne, two, three"))
    }

    @Test(timeout = 2_000) fun manyLeadingCommentsDoNotOverflowOrHideHtml() {
        val comments = "<!-- a -->".repeat(5_000)
        assertEquals(ContentKind.TEXT, kind("comments.txt", DocType.TXT, comments + "plain text"))
        assertEquals(ContentKind.WEB, kind("saved.txt", DocType.TXT, comments + "<!DOCTYPE html><html>"))
        assertEquals(ContentKind.WEB, kind("saved.txt", DocType.TXT, comments + "\n <HTML lang=\"zh\">"))
    }

    @Test(timeout = 2_000) fun commentsOutsideSampleAndUnclosedCommentsAreNotHtmlPrefixes() {
        assertEquals(ContentKind.TEXT, kind("plain.txt", DocType.TXT, "<!-- unclosed <html>"))
        val outsideSample = "<!--" + "a".repeat(ContentClassifier.SAMPLE_CHARS) + "--><html>"
        assertEquals(ContentKind.TEXT, kind("plain.txt", DocType.TXT, outsideSample, size = 1))
        assertEquals(ContentKind.TEXT, kind("plain.txt", DocType.TXT, "ordinary text <!-- comment --><html>"))
    }

    @Test fun kindIds() {
        ContentKind.entries.forEach { assertEquals(it, ContentKind.fromId(it.id)) }
        assertNull(ContentKind.fromId(null))
        assertNull(ContentKind.fromId("removed-kind"))
    }
}
