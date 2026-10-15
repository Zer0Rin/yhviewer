package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MarkdownRendererTest {

    @Test
    fun scriptCloseTagCannotEscape() {
        val out = MarkdownRenderer.toJsonString("</script><script>alert(1)</script>")
        assertFalse("输出里不应出现任何原始 '<'", out.contains('<'))
    }

    @Test
    fun htmlCommentCannotEscape() {
        assertFalse(MarkdownRenderer.toJsonString("<!-- <script>").contains('<'))
    }

    @Test
    fun quotesAndBackslash() {
        assertEquals("\"a\\\"b\\\\c\"", MarkdownRenderer.toJsonString("a\"b\\c"))
    }

    @Test
    fun newlinesTabsAndControlChars() {
        assertEquals("\"a\\nb\\r\\tc\\u0001\"", MarkdownRenderer.toJsonString("a\nb\r\tc\u0001"))
    }

    @Test
    fun lineSeparatorsAreEscaped() {
        assertEquals("\"\\u2028\\u2029\"", MarkdownRenderer.toJsonString("  "))
    }

    @Test
    fun unicodeIsKept() {
        assertEquals("\"中文😀\"", MarkdownRenderer.toJsonString("中文😀"))
    }

    @Test
    fun emptyString() {
        assertEquals("\"\"", MarkdownRenderer.toJsonString(""))
    }

    @Test
    fun renderReplacesPlaceholder() {
        val template = "<x>${MarkdownRenderer.PLACEHOLDER}</x>"
        assertEquals("<x>\"hi\"</x>", MarkdownRenderer.render("hi", template))
    }

    @Test
    fun dollarSignsAreLiteral() {
        // 防回归：如果有人把 replace 改成正则版本，"$1" 会被当成分组引用
        val template = MarkdownRenderer.PLACEHOLDER
        assertEquals("\"price \$1 \$\$\"", MarkdownRenderer.render("price \$1 \$\$", template))
    }

    @Test(expected = IllegalArgumentException::class)
    fun renderRequiresPlaceholder() {
        MarkdownRenderer.render("hi", "<x></x>")
    }
}
