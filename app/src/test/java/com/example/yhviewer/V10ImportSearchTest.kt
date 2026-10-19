package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** v10：文件夹导入规划、全书搜索、书签规则、缺失资源提示、SVG 预览、内容分类补充 */
class V10ImportSearchTest {

    // ---------- 文件夹导入规划：用用户朋友的 30 例测试包的目录结构 ----------

    private val pack = listOf(
        "01_HTML/01_简体_基础排版.html" to 1141L,
        "01_HTML/02_繁體_圖表與本地資源.html" to 3090L,
        "01_HTML/assets/layout.css" to 83L,
        "01_HTML/assets/reading-card.svg" to 326L,
        "02_Markdown/01_简体_基础语法.md" to 371L,
        "03_代码/02_繁體_字串與跳脫.js" to 2869L,
        "03_代码/05_长代码_1500条声明.cpp" to 104486L,
        "04_TXT/05_混合_大文件_12000行.txt" to 4558651L,
        "05_EPUB/05_简体_EPUB3_60章长篇.epub" to 37948L,
        "06_其他格式/01_简体_嵌套数据.json" to 482L,
        "06_其他格式/03_English_vector_card.svg" to 844L,
        "06_其他格式/05_混合_日志_长文件名_空 格_（Android书架测试）.log" to 86678L,
        "测试清单.csv" to 7665L,
        "使用说明.md" to 4721L,
        "Android书架测试文件.zip" to 100L,
    ).map { (p, n) -> FolderEntry(p, n) }

    private fun role(plan: FolderPlan, path: String) = plan.entries.first { it.entry.relPath == path }.role

    @Test fun friendsPackIsPlannedSensibly() {
        val plan = FolderImportPlanner.plan(pack)
        // HTML / Markdown 进文件夹副本，assets 里的 CSS 和 SVG 作为资源一起带上
        assertEquals(EntryRole.BUNDLE_DOC, role(plan, "01_HTML/02_繁體_圖表與本地資源.html"))
        assertEquals(EntryRole.BUNDLE_DOC, role(plan, "使用说明.md"))
        assertEquals(EntryRole.RESOURCE, role(plan, "01_HTML/assets/layout.css"))
        assertEquals(EntryRole.RESOURCE, role(plan, "01_HTML/assets/reading-card.svg"))
        // 资源目录外的 JS / JSON / SVG / LOG / CSV 是书架上的独立文档
        assertEquals(EntryRole.STANDALONE, role(plan, "03_代码/02_繁體_字串與跳脫.js"))
        assertEquals(EntryRole.STANDALONE, role(plan, "06_其他格式/01_简体_嵌套数据.json"))
        assertEquals(EntryRole.STANDALONE, role(plan, "06_其他格式/03_English_vector_card.svg"))
        assertEquals(EntryRole.STANDALONE, role(plan, "06_其他格式/05_混合_日志_长文件名_空 格_（Android书架测试）.log"))
        assertEquals(EntryRole.STANDALONE, role(plan, "测试清单.csv"))
        assertEquals(EntryRole.STANDALONE, role(plan, "04_TXT/05_混合_大文件_12000行.txt"))
        assertEquals(EntryRole.STANDALONE, role(plan, "05_EPUB/05_简体_EPUB3_60章长篇.epub"))
        // zip 不支持
        assertEquals(EntryRole.SKIP, role(plan, "Android书架测试文件.zip"))
        assertEquals(4, plan.bundleDocs.size)    // 2 个 HTML + 2 个 Markdown
        assertEquals(2, plan.resources.size)
        assertEquals(6, plan.bundleFiles.size)
    }

    @Test fun noBundleWithoutHtmlOrMarkdown() {
        val plan = FolderImportPlanner.plan(listOf(FolderEntry("a.txt", 10), FolderEntry("img/x.png", 10)))
        assertTrue(plan.bundleFiles.isEmpty())
        assertEquals(1, plan.standalone.size)
    }

    @Test fun browserSavedPageFolder() {
        // 浏览器"另存为网页"产生的 xxx_files 目录：里面的 HTML / JS 都是资源
        val plan = FolderImportPlanner.plan(listOf(
            FolderEntry("文章.html", 100), FolderEntry("文章_files/app.js", 100), FolderEntry("文章_files/frame.html", 100),
        ))
        assertEquals(1, plan.bundleDocs.size)
        assertEquals(2, plan.resources.size)
    }

    @Test fun limits() {
        val limits = FolderImportPlanner.Limits(maxEntries = 3, maxBundleFiles = 2, maxDepth = 2, maxBundleBytes = 1000)
        val plan = FolderImportPlanner.plan(listOf(
            FolderEntry("a.html", 10), FolderEntry("b.html", 10), FolderEntry("c.html", 10), FolderEntry("d.html", 10),
        ), limits)
        assertEquals(2, plan.bundleDocs.size)      // 副本最多 2 个文件
        assertEquals(2, plan.skipped.size)         // 1 个超出副本数量，1 个超出遍历数量
        assertEquals(EntryRole.SKIP, FolderImportPlanner.classify(FolderEntry("a/b/c/d.html", 1), limits).role) // 太深
        assertEquals(EntryRole.SKIP, FolderImportPlanner.classify(FolderEntry("big.css", 30L * 1024 * 1024), FolderImportPlanner.Limits()).role)
        val heavy = FolderImportPlanner.plan(listOf(FolderEntry("a.html", 600), FolderEntry("img/a.png", 600)), limits)
        assertEquals(EntryRole.SKIP, role(heavy, "img/a.png")) // 副本总大小超限：资源先被跳过
    }

    @Test fun unsafeNames() {
        listOf(null, "", " ", ".", "..", "a/b", "a\\b", "a\u0000b").forEach { assertNull(FolderImportPlanner.safeName(it)) }
        assertEquals("空 格（测试）.log", FolderImportPlanner.safeName(" 空 格（测试）.log "))
        assertTrue(FolderImportPlanner.ignored("__MACOSX"))
        assertTrue(FolderImportPlanner.ignored("._a.html"))
        assertTrue(FolderImportPlanner.ignored(".DS_Store"))
        assertFalse(FolderImportPlanner.ignored("a.html"))
    }

    @Test fun bundlePaths() {
        assertTrue(BundlePaths.isBundle("uuid/01_HTML/a.html"))
        assertFalse(BundlePaths.isBundle("uuid"))
        assertEquals("uuid", BundlePaths.topLevel("uuid/01_HTML/a.html"))
        assertEquals("uuid", BundlePaths.topLevel("uuid"))
        assertEquals("01_HTML/a.html", BundlePaths.relPath("uuid/01_HTML/a.html"))
        assertEquals("u/a/b.html", BundlePaths.localFile("u", "a/b.html"))
        listOf("", "/a", "a//b", "a/../b", "./a").forEach { bad ->
            try { BundlePaths.localFile("u", bad); throw AssertionError("accepted $bad") } catch (_: IOException) {}
        }
        assertEquals("content://tree/x#a/b.html", BundlePaths.sourceKey("content://tree/x", "a/b.html"))
    }

    // ---------- 全书搜索 ----------

    @Test fun searchFindsAllChaptersIgnoringCase() {
        val hits = BookSearch.search(listOf("Alpha beta\nALPHA", "no match", "xx alpha"), "alpha")
        assertEquals(listOf(0, 0, 2), hits.map { it.chapter })
        assertEquals(listOf(0, 11, 3), hits.map { it.offset })
        val h = hits[1]
        assertEquals("ALPHA", h.snippet.substring(h.matchStart, h.matchStart + h.matchLength))
        assertFalse(h.snippet.contains('\n'))   // 换行压成空格
    }

    @Test fun searchSnippetEllipsisAndLimit() {
        val text = "x".repeat(100) + "目标" + "y".repeat(100)
        val h = BookSearch.search(listOf(text), "目标").single()
        assertTrue(h.snippet.startsWith("…") && h.snippet.endsWith("…"))
        assertEquals("目标", h.snippet.substring(h.matchStart, h.matchStart + h.matchLength))
        assertEquals(5, BookSearch.search(listOf("aaaaaaaaaa"), "a", maxHits = 5).size)
        assertTrue(BookSearch.search(listOf("abc"), "  ").isEmpty())
    }

    @Test fun searchHandlesWhitespaceBeforeMatch() {
        val h = BookSearch.search(listOf("第一行\n\n\n   第二行 目标"), "目标").single()
        assertEquals("目标", h.snippet.substring(h.matchStart, h.matchStart + h.matchLength))
    }

    @Test fun xhtmlToText() {
        val x = """<?xml version="1.0"?><html><head><title>T</title><style>p{}</style></head><body>
            <h1>第一章</h1><p>甲&amp;乙 &lt;b&gt; &#x4E2D;&#25991; &amp;lt;</p><!-- 注释 --><script>alert(1)</script><p>丙</p></body></html>"""
        val t = XhtmlText.extract(x)
        assertEquals("第一章\n甲&乙 <b> 中文 &lt;\n丙", t)
        assertFalse(t.contains("alert"))
        assertFalse(t.contains("注释"))
    }

    // ---------- 书签 ----------

    @Test fun bookmarkRules() {
        assertEquals("第一章 · 37%", BookmarkRules.label("第一章", "书.epub", 0.375f))
        assertEquals("书.epub · 100%", BookmarkRules.label(null, "书.epub", 1.5f))
        val existing = listOf(Bookmark(1, 9, 2, 0.50f, "x", 0))
        assertTrue(BookmarkRules.isDuplicate(existing, 2, 0.505f))
        assertFalse(BookmarkRules.isDuplicate(existing, 2, 0.52f))
        assertFalse(BookmarkRules.isDuplicate(existing, 3, 0.50f))
    }

    // ---------- 缺失资源提示 ----------

    @Test fun missingResourcesMessage() {
        val paths = listOf("/doc/assets/layout.css", "/doc/assets/reading-card.svg", "/doc/assets/layout.css", "/doc/x.png")
        assertEquals(listOf("assets/layout.css", "assets/reading-card.svg", "x.png"), MissingResources.display(paths))
        val m = MissingResources.message(paths, bundled = false)
        assertTrue(m.contains("assets/layout.css") && m.contains("等 3 个文件") && m.contains("导入文件夹"))
        assertFalse(MissingResources.message(listOf("/bundle/a.png"), bundled = true).contains("导入文件夹"))
    }

    // ---------- SVG 和内容分类 ----------

    @Test fun svgRendersAsImageNotInline() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>"""
        val html = SvgRenderer.render(svg, ReaderTheme.INK)
        assertTrue(html.contains("data-theme=\"ink\""))
        assertTrue(html.contains("src=\"data:image/svg+xml;base64,"))
        assertFalse(html.contains("alert(1)"))   // 原文只以 base64 出现在 <img> 里
        assertTrue(LoadedDoc("a.SVG", DocType.UNKNOWN, "").isSvg)
    }

    @Test fun classifierSvgAndLog() {
        assertEquals(ContentKind.IMAGE, ContentClassifier.classify("card.svg", DocType.UNKNOWN, 10, "<svg/>"))
        assertEquals(ContentKind.TEXT, ContentClassifier.classify("app.log", DocType.CODE, 10, "2026-10-18 INFO start"))
        assertEquals(ContentKind.IMAGE, ContentKind.fromId("image"))
    }
}
