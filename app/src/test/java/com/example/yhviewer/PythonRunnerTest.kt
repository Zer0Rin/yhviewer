package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonRunnerTest {
    private val template = "<html data-theme=\"${MarkdownRenderer.THEME_PLACEHOLDER}\">$PAYLOAD_PLACEHOLDER"
    private val js = CodeRunner.Lang.JAVASCRIPT
    private val py = CodeRunner.Lang.PYTHON

    private fun directive(csp: String, name: String) =
        csp.split("; ").first { it.startsWith("$name ") }.removePrefix("$name ")

    @Test fun runnableLanguages() {
        assertEquals(js, CodeRunner.runnableLang("javascript", pythonAvailable = false))
        assertEquals(py, CodeRunner.runnableLang("python", pythonAvailable = true))
        assertNull("strict 版没有 Pyodide", CodeRunner.runnableLang("python", pythonAvailable = false))
        listOf("typescript", "kotlin", "plaintext", null).forEach { assertNull(CodeRunner.runnableLang(it, true)) }
    }

    @Test fun payloadCannotBreakOutOfScriptTag() {
        val out = CodeRunner.render("print('</script><script>alert(1)</script>')", template, ReaderTheme.INK, lang = py)
        assertFalse(out.substringAfter(">").contains('<'))
    }

    @Test fun payloadCarriesLanguage() {
        assertTrue(CodeRunner.render("1", template, ReaderTheme.AUTO, lang = py).contains("\"lang\":\"python\""))
        assertTrue(CodeRunner.render("1", template, ReaderTheme.AUTO, lang = js).contains("\"lang\":\"javascript\""))
    }

    @Test fun timeoutIsClamped() {
        assertTrue(CodeRunner.render("1", template, ReaderTheme.AUTO, lang = js, timeoutMs = 10).contains("\"timeoutMs\":500"))
        assertTrue(CodeRunner.render("1", template, ReaderTheme.AUTO, lang = js, timeoutMs = 999_999).contains("\"timeoutMs\":30000"))
    }

    @Test fun javascriptCspBlocksAllConnections() {
        val csp = CodeRunner.cspFor(js)
        assertEquals("'none'", directive(csp, "connect-src"))
        assertEquals("blob:", directive(csp, "worker-src"))
        assertEquals("'none'", directive(csp, "frame-src"))
    }

    @Test fun pythonCspAllowsOnlyOwnAssets() {
        val csp = CodeRunner.cspFor(py)
        assertEquals("'self'", directive(csp, "connect-src"))   // 只能读 App 自带的 Pyodide 文件
        assertTrue(directive(csp, "script-src").contains("'wasm-unsafe-eval'"))
    }

    @Test fun noCspEverAllowsExternalHosts() {
        CodeRunner.Lang.entries.forEach { lang ->
            val csp = CodeRunner.cspFor(lang)
            assertFalse(lang.name, csp.contains("https:"))
            assertFalse(lang.name, csp.contains("http:"))
            assertFalse(lang.name, csp.contains("wss:"))
            assertTrue(csp.startsWith("default-src 'none'"))
        }
    }
}
