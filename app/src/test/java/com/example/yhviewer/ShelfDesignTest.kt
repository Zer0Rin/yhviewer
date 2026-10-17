package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfDesignTest {

    // ---------- 排版封面配色 ----------

    @Test fun paletteIsStableAndTrimmed() {
        assertEquals(CoverPalette.forTitle("三体"), CoverPalette.forTitle("三体"))
        assertEquals(CoverPalette.forTitle("三体"), CoverPalette.forTitle("  三体 "))
        // 负哈希也必须落在范围内（floorMod）
        listOf("", "a", "Kotlin in Depth", "长夜将尽", "x".repeat(500)).forEach { CoverPalette.forTitle(it) }
    }

    @Test fun paletteSpreadsAcrossTitles() {
        val used = (1..40).map { CoverPalette.forTitle("第 $it 本书") }.toSet()
        assertEquals(CoverPalette.entries.toSet(), used)
    }

    // ---------- 从 TXT 文件名解析书名和作者 ----------

    @Test fun parsesCommonTxtNames() {
        assertEquals(BookTitles.Parsed("三体", "刘慈欣"), BookTitles.parse("《三体》作者：刘慈欣.txt"))
        assertEquals(BookTitles.Parsed("三体", null), BookTitles.parse("《三体》（校对版）.txt"))
        assertEquals(BookTitles.Parsed("三体", "刘慈欣"), BookTitles.parse("三体 作者:刘慈欣.txt"))
        assertEquals(BookTitles.Parsed("三体", "刘慈欣"), BookTitles.parse("三体 - 刘慈欣.txt"))
        assertEquals(BookTitles.Parsed("notes-2024", null), BookTitles.parse("notes-2024.txt"))
        assertEquals(BookTitles.Parsed("夜车", null), BookTitles.parse("夜车"))
    }

    @Test fun epubUsesMetadataNotFileName() {
        val b = book(1, "《不是这个》作者：谁.epub", DocType.EPUB).copy(author = "周南")
        assertEquals(BookTitles.Parsed(b.name, "周南"), BookTitles.display(b))
    }

    // ---------- 缩略图采样率 ----------

    @Test fun sampleSize() {
        assertEquals(1, sampleSizeFor(300, 300))
        assertEquals(1, sampleSizeFor(599, 300))
        assertEquals(2, sampleSizeFor(600, 300))
        assertEquals(8, sampleSizeFor(3000, 300))
        assertEquals(1, sampleSizeFor(100, 300)) // 比目标还小：不放大
    }

    // ---------- 目录筛选 ----------

    private val titles = listOf("序章", "第一章 出发", "第二章 抵达", "Chapter 12 Tide", "尾声")

    @Test fun tocFilter() {
        assertEquals(5, filterToc(titles, " ").size)
        assertEquals(listOf(1, 2), filterToc(titles, "第").map { it.index }.drop(0).take(2))
        assertEquals(listOf(3), filterToc(titles, "tide").map { it.index })           // 忽略大小写
        assertEquals(listOf(1), filterToc(titles, "2").map { it.index }.take(1))      // 数字 = 第 2 章（下标 1）
        assertTrue(filterToc(titles, "12").any { it.index == 3 })                     // 也匹配标题里的数字
        assertTrue(filterToc(titles, "不存在").isEmpty())
    }

    // ---------- 书架筛选：范围 × 内容分类 ----------

    private fun book(
        id: Long, name: String, type: DocType, progress: Float = 0f, fav: Boolean = false,
        group: Long? = null, kind: ContentKind? = null,
    ) = Book(
        id = id, sourceUri = "content://$id", name = name, type = type, localFile = "f$id",
        sizeBytes = 1, addedAt = 0, lastOpenedAt = 100 - id, favorite = fav, progress = progress,
        groupId = group, kind = kind?.id,
    )

    private val list = listOf(
        book(1, "a.html", DocType.HTML, group = 10, kind = ContentKind.WEB),
        book(2, "夜车.epub", DocType.EPUB, progress = 0.4f, kind = ContentKind.EBOOK),
        book(3, "三体.txt", DocType.TXT, progress = 0.7f, fav = true, group = 10, kind = ContentKind.NOVEL),
        book(4, "Main.kt", DocType.CODE, fav = true, kind = ContentKind.CODE),
        book(5, "读完了.txt", DocType.TXT, progress = 1f, kind = ContentKind.NOVEL),
        book(6, "data.txt", DocType.TXT, kind = ContentKind.DATA),       // 内容是 JSON 的 .txt
        book(7, "old.md", DocType.MARKDOWN, group = 11),                  // 升级前导入，还没分类
    )

    @Test fun continueReadingIsMostRecentUnfinishedBook() {
        val s = sectionsFor(list, ShelfScope.All, kind = null, searching = false)
        assertEquals(2L, s.continueReading?.id)
        assertEquals(listOf(3L, 5L, 6L), s.books.map { it.id })  // 按 DocType 分区，不重复出现
        assertEquals(listOf(1L, 4L, 7L), s.docs.map { it.id })
        assertEquals(7, s.all.size)
    }

    @Test fun noHeroWhenSearchingFilteringOrInsideAGroup() {
        assertNull(sectionsFor(list, ShelfScope.All, null, searching = true).continueReading)
        assertNull(sectionsFor(list, ShelfScope.All, ContentKind.EBOOK, false).continueReading)
        assertNull(sectionsFor(list, ShelfScope.Group(10), null, false).continueReading)
    }

    @Test fun scopes() {
        assertEquals(listOf(3L, 4L), sectionsFor(list, ShelfScope.Favorites, null, false).all.map { it.id })
        assertEquals(listOf(3L, 1L), sectionsFor(list, ShelfScope.Group(10), null, false).all.map { it.id })
        assertEquals(setOf(2L, 4L, 5L, 6L), sectionsFor(list, ShelfScope.Ungrouped, null, false).all.map { it.id }.toSet())
        assertTrue(sectionsFor(list, ShelfScope.Group(99), null, false).isEmpty)
    }

    @Test fun kindFilterAndFallback() {
        assertEquals(listOf(6L), sectionsFor(list, ShelfScope.All, ContentKind.DATA, false).all.map { it.id })
        // kind 为空的老记录按 DocType 估：.md → 笔记
        assertEquals(listOf(7L), sectionsFor(list, ShelfScope.All, ContentKind.NOTE, false).all.map { it.id })
        // 范围和分类叠加
        assertEquals(listOf(3L), sectionsFor(list, ShelfScope.Group(10), ContentKind.NOVEL, false).all.map { it.id })
    }

    @Test fun kindCountsFollowScopeAndEnumOrder() {
        assertEquals(
            listOf(ContentKind.WEB to 1, ContentKind.NOTE to 1, ContentKind.CODE to 1, ContentKind.DATA to 1, ContentKind.NOVEL to 2, ContentKind.EBOOK to 1),
            kindCounts(list, ShelfScope.All),
        )
        assertEquals(listOf(ContentKind.WEB to 1, ContentKind.NOVEL to 1), kindCounts(list, ShelfScope.Group(10)))
    }

    @Test fun scopeKeysRoundTrip() {
        listOf(ShelfScope.All, ShelfScope.Favorites, ShelfScope.Ungrouped, ShelfScope.Group(42)).forEach {
            assertEquals(it, ShelfScope.fromKey(it.key))
        }
        assertEquals(ShelfScope.All, ShelfScope.fromKey("g:abc")) // 坏数据回到"全部"
        assertEquals(ShelfScope.Ungrouped, ShelfScope.fromGroupId(null))
    }

    @Test fun selectionDropsItemsHiddenByScopeOrKindButWaitsForInitialLoad() {
        val selected = listOf(1L, 3L, 4L)
        assertEquals(selected, pruneShelfSelection(selected, null))
        assertEquals(listOf(1L, 3L), pruneShelfSelection(selected, sectionsFor(list, ShelfScope.Group(10), null, false)))
        assertEquals(listOf(3L), pruneShelfSelection(selected, sectionsFor(list, ShelfScope.All, ContentKind.NOVEL, false)))
        assertTrue(pruneShelfSelection(selected, sectionsFor(list, ShelfScope.Group(99), null, false)).isEmpty())
    }

    // ---------- 阅读页配色 ----------

    @Test fun readerPaletteMatchesThemesCss() {
        assertEquals(0xFFFAF9F5, ReaderPalette.of(ReaderTheme.PAPER, systemDark = true).bg)
        assertEquals(0xFF262624, ReaderPalette.of(ReaderTheme.INK, systemDark = false).bg)
        assertEquals(0xFF1D2A32, ReaderPalette.of(ReaderTheme.TERMINAL, systemDark = false).bg) // 截图里露白条的那个主题
        assertEquals(ReaderPalette.INK, ReaderPalette.of(ReaderTheme.AUTO, systemDark = true))
        assertEquals(ReaderPalette.PAPER, ReaderPalette.of(ReaderTheme.AUTO, systemDark = false))
    }
}
