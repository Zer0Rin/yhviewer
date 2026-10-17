package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v8 视觉调整里的纯逻辑：文件名美化、点阵进度、相对时间、色板对比度 */
class V8VisualTest {

    @Test fun prettifyFileNameTitles() {
        assertEquals("night train", BookTitles.parse("night-train.txt").title)
        assertEquals("my notes v2", BookTitles.parse("my_notes.v2.txt").title)
        assertEquals("夜-车", BookTitles.parse("夜-车.txt").title)        // 含中文：原样
        assertEquals("plain", BookTitles.parse("plain.txt").title)
        assertEquals("---", BookTitles.prettify("---"))                  // 全是分隔符：不变成空串
    }

    @Test fun dotProgress() {
        assertEquals(0, dotProgressLit(0f, 40))
        assertEquals(1, dotProgressLit(0.001f, 40))   // 读了一点就至少亮一个
        assertEquals(20, dotProgressLit(0.5f, 40))
        assertEquals(39, dotProgressLit(0.99f, 40))   // 没读完不全亮
        assertEquals(40, dotProgressLit(1f, 40))
        assertEquals(1, dotProgressLit(0.5f, 1))
    }

    @Test fun justNow() {
        assertEquals("刚刚", relativeTime(time = 1_000_000L, now = 1_000_000L + 59_000L).toString())
    }

    /** WCAG 相对亮度对比度 */
    private fun contrast(a: Long, b: Long): Double {
        fun lum(c: Long): Double {
            fun ch(v: Long): Double {
                val s = v / 255.0
                return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
        }
        val (x, y) = lum(a) to lum(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    @Test fun palettesAreReadable() {
        listOf(ReaderPalette.PAPER, ReaderPalette.INK, ReaderPalette.TERMINAL).forEach { p ->
            assertTrue("fg ${p.fg}", contrast(p.fg, p.bg) >= 7.0)
            assertTrue("muted ${p.muted}", contrast(p.muted, p.bg) >= 4.5)
            assertTrue("muted on surface ${p.muted}", contrast(p.muted, p.surface) >= 4.5)
            assertTrue("subtle on surface ${p.subtle}", contrast(p.subtle, p.surface) >= 4.5)
            assertTrue("accent ${p.accent}", contrast(p.accent, p.bg) >= 4.5)  // 强调色会用在小字按钮上
            assertTrue("accent on surface ${p.accent}", contrast(p.accent, p.surface) >= 4.5)
            assertTrue("subtle ${p.subtle}", contrast(p.subtle, p.bg) >= 4.5)   // 小标签、作者和进度也必须达到普通文字对比度
        }
    }

    @Test fun shellUsesReaderBackground() {
        // 外壳和阅读页同一个底色：这就是 v8 要修的"外壳偏黄、正文偏白"
        ReaderTheme.entries.forEach { t ->
            val p = ReaderPalette.of(t, systemDark = false)
            assertEquals(androidx.compose.ui.graphics.Color(p.bg), p.toColors().bg)
        }
    }
}
