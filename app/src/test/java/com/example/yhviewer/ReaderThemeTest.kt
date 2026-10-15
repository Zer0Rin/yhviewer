package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderThemeTest {

    private val template =
        """<html data-theme="${MarkdownRenderer.THEME_PLACEHOLDER}"><x>${MarkdownRenderer.PLACEHOLDER}</x></html>"""

    @Test
    fun fromIdKnownValues() {
        ReaderTheme.entries.forEach { assertEquals(it, ReaderTheme.fromId(it.id)) }
    }

    @Test
    fun fromIdUnknownOrNullFallsBackToAuto() {
        assertEquals(ReaderTheme.AUTO, ReaderTheme.fromId(null))
        assertEquals(ReaderTheme.AUTO, ReaderTheme.fromId("removed-theme"))
        assertEquals(ReaderTheme.AUTO, ReaderTheme.fromId(""))
    }

    @Test
    fun idsMatchTemplateWhitelist() {
        // 改主题时如果忘了同步 template.html 的 setTheme 白名单，这个测试会提醒你
        assertEquals(listOf("auto", "paper", "ink", "terminal"), ReaderTheme.entries.map { it.id })
    }

    @Test
    fun renderInjectsTheme() {
        val out = MarkdownRenderer.render("hi", template, ReaderTheme.TERMINAL)
        assertTrue(out.startsWith("""<html data-theme="terminal">"""))
    }

    @Test
    fun renderDefaultsToAuto() {
        assertTrue(MarkdownRenderer.render("hi", template).contains("""data-theme="auto""""))
    }

    @Test
    fun placeholderTextInsideUserContentIsNotReplaced() {
        // 用户文档里恰好出现占位符字样，必须原样保留
        val out = MarkdownRenderer.render("see __THEME__ here", template, ReaderTheme.INK)
        assertTrue(out.contains("\"see __THEME__ here\""))
        assertFalse(out.contains("see ink here"))
    }
}
