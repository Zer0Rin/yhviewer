package com.example.yhviewer

import java.util.Locale

/**
 * 阅读位置："第几章 + 章内滚动比例"。单页文档（HTML/MD/代码）chapter 恒为 0。
 * 存进数据库时编码成 "3:0.4521" 这样的短字符串，读不懂的值一律回到开头，而不是崩溃。
 *
 * 用比例而不是像素：换主题、换字号、横竖屏切换后，像素位置会失效，比例基本还能对上。
 */
data class Locator(val chapter: Int = 0, val ratio: Float = 0f, val flowRatio: Float? = null) {

    // Old chapter:ratio retains exactly its meaning. Only continuous tail/gap offsets
    // need this optional extension; single-page readers use the ordinary clamped ratio.
    fun encode(): String = "$chapter:" + String.format(Locale.ROOT, "%.4f", ratio.coerceIn(0f, 1f)) +
        (flowRatio?.takeIf { it.isFinite() && it > 1f }?.let { ":flow=" + String.format(Locale.ROOT, "%.4f", it) } ?: "")

    /** 全书进度 0..1，用于书架显示"读到 35%" */
    fun overallProgress(chapterCount: Int): Float {
        val r = ratio.coerceIn(0f, 1f)
        if (chapterCount <= 1) return r
        val c = chapter.coerceIn(0, chapterCount - 1)
        return (c + r) / chapterCount
    }

    companion object {
        val START = Locator()

        fun decode(s: String?): Locator {
            if (s.isNullOrBlank()) return START
            val parts = s.split(':')
            val chapter = parts.getOrNull(0)?.toIntOrNull() ?: return START
            val ratio = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
            if (chapter < 0 || ratio.isNaN()) return START
            val flow = parts.getOrNull(2)?.takeIf { it.startsWith("flow=") }?.removePrefix("flow=")
                ?.toFloatOrNull()?.takeIf { it.isFinite() && it > 1f }
            return Locator(chapter, ratio.coerceIn(0f, 1f), flow)
        }
    }
}
