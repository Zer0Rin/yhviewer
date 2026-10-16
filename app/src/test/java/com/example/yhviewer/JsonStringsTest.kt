package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonStringsTest {

    private val samples = listOf(
        "",
        "hello",
        "line1\nline2\r\n\tindented",
        "quotes \" and backslash \\ and slash /",
        "中文、日本語、한국어",
        "emoji 😀👍🏽 (surrogate pairs)",
        "</script><!-- & \u2028 \u2029",
        "control \u0001\u001f chars",
        "fun main() {\n    println(\"hi\")\n}\n",
    )

    @Test fun roundTripWithOurEncoder() {
        // 我们的编码器会把 "<" 编成 \u003c、控制字符编成 \u00XX：解码必须完全还原
        samples.forEach { s ->
            assertEquals(s, JsonStrings.decodeStringOrNull(MarkdownRenderer.toJsonString(s)))
        }
    }

    @Test fun decodesChromeStyleOutput() {
        assertEquals("a\"b", JsonStrings.decodeStringOrNull("\"a\\\"b\""))
        assertEquals("é", JsonStrings.decodeStringOrNull("\"\\u00e9\""))
        assertEquals("😀", JsonStrings.decodeStringOrNull("\"\\ud83d\\ude00\""))
        assertEquals("x/y", JsonStrings.decodeStringOrNull("\"x\\/y\""))
    }

    @Test fun nullAndEmpty() {
        assertNull(JsonStrings.decodeStringOrNull(null))
        assertNull(JsonStrings.decodeStringOrNull("null"))
        assertNull(JsonStrings.decodeStringOrNull(""))
        assertEquals("", JsonStrings.decodeStringOrNull("\"\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonString() {
        JsonStrings.decodeStringOrNull("42")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedEscape() {
        JsonStrings.decodeStringOrNull("\"\\u12\"")
    }

    @Test fun rejectsMalformedJsonStringContents() {
        listOf("\"unescaped\"quote\"", "\"raw\nnewline\"", "\"\\u+001\"", "\"\\u-001\"", "\"\\q\"", "\"ok\" trailing").forEach { bad ->
            try { JsonStrings.decodeStringOrNull(bad); org.junit.Assert.fail("Accepted malformed JSON") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun booleans() {
        assertTrue(JsonStrings.decodeBoolean("true"))
        assertFalse(JsonStrings.decodeBoolean("false"))
        assertFalse(JsonStrings.decodeBoolean("null"))
        assertFalse(JsonStrings.decodeBoolean(null))
    }
}
