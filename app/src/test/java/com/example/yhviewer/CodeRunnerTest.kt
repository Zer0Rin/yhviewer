package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeRunnerTest {
    private val template = "<html data-theme=\"${MarkdownRenderer.THEME_PLACEHOLDER}\">$PAYLOAD_PLACEHOLDER"

    private fun directive(name: String) =
        CodeRunner.CSP.split("; ").first { it.startsWith("$name ") }.removePrefix("$name ")

    @Test fun onlyJavaScriptIsRunnable() {
        assertTrue(CodeRunner.isRunnable("javascript"))
        listOf("typescript", "python", "kotlin", "plaintext", null).forEach { assertFalse(CodeRunner.isRunnable(it)) }
    }

    @Test fun payloadCannotBreakOutOfScriptTag() {
        val out = CodeRunner.render("console.log('</script><script>alert(1)</script>')", template, ReaderTheme.INK)
        assertFalse(out.substringAfter(">").contains('<'))
    }

    @Test fun themeIsInjected() {
        assertTrue(CodeRunner.render("1", template, ReaderTheme.TERMINAL).startsWith("<html data-theme=\"terminal\">"))
    }

    @Test fun timeoutIsClamped() {
        assertTrue(CodeRunner.render("1", template, ReaderTheme.AUTO, timeoutMs = 10).contains("\"timeoutMs\":500"))
        assertTrue(CodeRunner.render("1", template, ReaderTheme.AUTO, timeoutMs = 999_999).contains("\"timeoutMs\":30000"))
    }

    @Test fun limitsAreIncluded() {
        val out = CodeRunner.render("1", template, ReaderTheme.AUTO)
        assertTrue(out.contains("\"maxLines\":${CodeRunner.MAX_LINES}"))
        assertTrue(out.contains("\"maxChars\":${CodeRunner.MAX_CHARS}"))
    }

    @Test fun cspBlocksAllNetworking() {
        assertEquals("'none'", directive("connect-src"))
        assertEquals("'none'", directive("frame-src"))
        assertEquals("blob:", directive("worker-src"))
        assertFalse("脚本不能来自任何外部主机", directive("script-src").contains("https"))
        assertTrue(CodeRunner.CSP.startsWith("default-src 'none'"))
    }
    @Test fun userPlaceholderTextIsNotReplaced() {
        val out=CodeRunner.render("console.log('__THEME__ /*PAYLOAD*/')",template,ReaderTheme.PAPER)
        assertTrue(out.contains("__THEME__ /*PAYLOAD*/"))
    }

    @Test(expected=IllegalArgumentException::class)
    fun missingPayloadTemplateFailsExplicitly() {
        CodeRunner.render("1","<html></html>",ReaderTheme.AUTO)
    }
}
