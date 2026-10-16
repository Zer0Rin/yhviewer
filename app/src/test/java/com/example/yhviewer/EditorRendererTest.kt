package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorRendererTest {
    private val template = "<html data-theme=\"${MarkdownRenderer.THEME_PLACEHOLDER}\">$PAYLOAD_PLACEHOLDER"

    @Test fun languageByType() {
        assertEquals("html", EditorRenderer.langFor(LoadedDoc("a.html", DocType.HTML, "")))
        assertEquals("markdown", EditorRenderer.langFor(LoadedDoc("a.md", DocType.MARKDOWN, "")))
        assertEquals("kotlin", EditorRenderer.langFor(LoadedDoc("Main.kt", DocType.CODE, "")))
        assertEquals("plaintext", EditorRenderer.langFor(LoadedDoc("README", DocType.UNKNOWN, "")))
    }

    @Test fun payloadIsSafe() {
        val out = EditorRenderer.render("</script><script>x()</script>", "html", false, template, ReaderTheme.INK)
        assertFalse(out.substringAfter(">").contains('<'))
        assertTrue(out.startsWith("<html data-theme=\"ink\">"))
    }

    @Test fun editedTextSurvivesTheRoundTrip() {
        // 打开编辑器时编码 → 保存时从 evaluateJavascript 解码：内容必须一字不差
        val text = "fun main() {\n    println(\"你好 😀 </script>\")\n}\n"
        val payload = EditorRenderer.render(text, "kotlin", false, PAYLOAD_PLACEHOLDER, ReaderTheme.AUTO)
        val literal = payload.substringAfter("\"text\":").substringBefore(",\"lang\"")
        assertEquals(text, JsonStrings.decodeStringOrNull(literal))
    }

    @Test fun largeFilesAreReadOnly() {
        assertTrue(EditorRenderer.canEdit("x".repeat(EditorRenderer.MAX_EDIT_CHARS)))
        assertFalse(EditorRenderer.canEdit("x".repeat(EditorRenderer.MAX_EDIT_CHARS + 1)))
    }
}
