package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v13：液态玻璃的坐标换算、Markdown 的排版 CSS */
class V13GlassTest {
    @Test fun glassRectsAreConvertedToCssPixels() {
        val js = GlassScript.build(
            listOf(GlassRect(left = 48f, top = 2200f, width = 984f, height = 168f, radius = 84f)),
            viewLeft = 0f, viewTop = 100f, cssScale = 2.625f,
        )
        assertEquals("window.yhGlass && window.yhGlass([{\"x\":18.3,\"y\":800.0,\"w\":374.9,\"h\":64.0,\"r\":32.0}])", js)
    }

    @Test fun emptyListClearsAndBadScaleFallsBack() {
        assertEquals("window.yhGlass && window.yhGlass([])", GlassScript.build(emptyList(), 0f, 0f, 3f))
        val js = GlassScript.build(listOf(GlassRect(10f, 20f, 30f, 40f, 5f)), 0f, 0f, Float.NaN)
        assertTrue(js.contains("\"x\":10.0,\"y\":20.0,\"w\":30.0,\"h\":40.0,\"r\":5.0"))
    }

    @Test fun atMostFourRects() {
        val many = List(9) { GlassRect(it.toFloat(), 0f, 1f, 1f, 0f) }
        assertEquals(4, Regex("\\{").findAll(GlassScript.build(many, 0f, 0f, 1f)).count())
    }

    @Test fun markdownReadingCssGoesBeforeUserContent() {
        val template = "<style id=\"reading\">${MarkdownRenderer.READING_PLACEHOLDER}</style><x>${MarkdownRenderer.PLACEHOLDER}</x>"
        val css = ReadingPrefs(fontScale = 130, lineHeight = 2.0f).toCss()
        val out = MarkdownRenderer.render("text with ${MarkdownRenderer.READING_PLACEHOLDER} inside", template, ReaderTheme.PAPER, css)
        assertTrue(out.startsWith("<style id=\"reading\">$css</style>"))
        assertTrue(out.contains("text with /*MD_READING*/ inside"))   // 用户内容里的占位符不被替换
    }

    @Test(expected = IllegalArgumentException::class)
    fun markdownRejectsUnsafeReadingCss() {
        MarkdownRenderer.render("x", MarkdownRenderer.PLACEHOLDER, ReaderTheme.PAPER, "</style><script>")
    }

    @Test fun docDefaultsMatchMarkdownTemplate() {
        assertEquals(100, ReadingPrefsStore.DOC_DEFAULTS.fontScale)
        assertEquals(1.8f, ReadingPrefsStore.DOC_DEFAULTS.lineHeight, 0f)
        assertEquals(20, ReadingPrefsStore.DOC_DEFAULTS.margin)
        // 默认值要能用滑杆拖回来：落在刻度上
        val line = ReadingPrefsStore.DOC_DEFAULTS.lineHeight
        assertEquals(0.0, ((line - ReadingPrefs.MIN_LINE) * 10 - Math.round((line - ReadingPrefs.MIN_LINE) * 10)).toDouble(), 1e-4)
        assertEquals(0, (ReadingPrefsStore.DOC_DEFAULTS.margin - ReadingPrefs.MIN_MARGIN) % 4)
        assertEquals(0, (ReadingPrefsStore.DOC_DEFAULTS.fontScale - ReadingPrefs.MIN_SCALE) % 10)
    }
}
