package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPrefsTest {

    @Test fun nonFiniteLineHeightFallsBackToDefault() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertEquals(1.9f, ReadingPrefs(lineHeight = it).normalized().lineHeight)
            assertFalse(ReadingPrefs(lineHeight = it).toCss().contains("NaN"))
            assertFalse(ReadingPrefs(lineHeight = it).toCss().contains("Infinity"))
        }
    }

    @Test fun cssUsesDotDecimalsRegardlessOfLocale() {
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertTrue(ReadingPrefs(fontScale = 120, lineHeight = 2.1f).toCss().contains("--read-scale:1.20;--read-line:2.10"))
        } finally { java.util.Locale.setDefault(original) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun epubRejectsAmpersandCss() {
        EpubInjector.inject("<html><head></head></html>", ReaderTheme.INK, "a&b")
    }

    @Test fun defaultCss() {
        assertEquals(
            ":root{--read-scale:1.00;--read-line:1.90;--read-margin:20px;--read-font:var(--font-body)}",
            ReadingPrefs().toCss(),
        )
    }

    @Test fun valuesAreClamped() {
        val p = ReadingPrefs(fontScale = 5, lineHeight = 9f, margin = -10).normalized()
        assertEquals(ReadingPrefs.MIN_SCALE, p.fontScale)
        assertEquals(ReadingPrefs.MAX_LINE, p.lineHeight)
        assertEquals(ReadingPrefs.MIN_MARGIN, p.margin)
    }

    @Test fun cssUsesClampedValues() {
        assertTrue(ReadingPrefs(fontScale = 999).toCss().contains("--read-scale:2.00"))
    }

    @Test fun cssIsSafeForXhtmlStyleTag() {
        ReadingFont.entries.forEach { f ->
            val css = ReadingPrefs(font = f).toCss()
            assertFalse(css.contains('<'))
            assertFalse(css.contains('&'))
        }
    }

    @Test fun fontFallsBackToTheme() {
        assertEquals(ReadingFont.THEME, ReadingFont.fromId(null))
        assertEquals(ReadingFont.THEME, ReadingFont.fromId("comic-sans"))
        assertEquals(ReadingFont.SERIF, ReadingFont.fromId("serif"))
    }

    @Test fun txtPayloadCarriesReadingCss() {
        val out = TxtRenderer.render(
            TxtChapter("第一章", "内容"), 0, 3, PAYLOAD_PLACEHOLDER, ReaderTheme.AUTO, ReadingPrefs(fontScale = 120),
        )
        assertTrue(out.contains("--read-scale:1.20"))
    }

    @Test fun epubInjectsReadingStyleBeforeThemeLinks() {
        val xhtml = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>t</title></head><body/></html>"
        val css = ReadingPrefs(margin = 32).toCss()
        val out = EpubInjector.inject(xhtml, ReaderTheme.INK, css)
        assertTrue(out.contains("<style type=\"text/css\">$css</style>${EpubInjector.LINKS}</head>"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun epubRejectsUnsafeCss() {
        EpubInjector.inject("<html><head></head></html>", ReaderTheme.INK, "</style><script>x</script>")
    }
}
