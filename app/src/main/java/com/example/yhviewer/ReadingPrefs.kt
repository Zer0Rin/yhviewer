package com.example.yhviewer

import android.content.Context
import java.util.Locale

/** 正文字体：跟随主题（终端主题是等宽），或强制衬线 / 无衬线 */
enum class ReadingFont(val id: String, val label: String, val css: String) {
    THEME("theme", "跟随主题", "var(--font-body)"),
    SERIF("serif", "衬线", "var(--font-serif)"),
    SANS("sans", "无衬线", "var(--font-sans)"),
    ;

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: THEME
    }
}

/**
 * 长文本（TXT / EPUB）的排版偏好。全局生效，不分书。
 * 只用于"书"：HTML / Markdown / 代码保持各自的排版。
 */
data class ReadingPrefs(
    val fontScale: Int = 100,        // 百分比
    val lineHeight: Float = 1.9f,
    val margin: Int = 20,            // 左右页边距，CSS 像素
    val font: ReadingFont = ReadingFont.THEME,
    val tapToTurn: Boolean = true,   // 点屏幕左右两侧翻页
    val keepScreenOn: Boolean = false,
) {
    /** 夹到合法范围：偏好文件被改坏、或以后调整范围时，不会出现 0 号字之类的值 */
    fun normalized() = copy(
        fontScale = fontScale.coerceIn(MIN_SCALE, MAX_SCALE),
        lineHeight = if (lineHeight.isFinite()) lineHeight.coerceIn(MIN_LINE, MAX_LINE) else 1.9f,
        margin = margin.coerceIn(MIN_MARGIN, MAX_MARGIN),
    )

    /**
     * 生成注入页面的 CSS 变量。只包含数字和枚举里的固定字符串，
     * 不含 "<" "&"，可以安全地放进 XHTML 的 <style> 里。
     */
    fun toCss(): String {
        val p = normalized()
        val scale = String.format(Locale.ROOT, "%.2f", p.fontScale / 100f)
        val line = String.format(Locale.ROOT, "%.2f", p.lineHeight)
        return ":root{--read-scale:$scale;--read-line:$line;--read-margin:${p.margin}px;--read-font:${p.font.css}}"
    }

    companion object {
        const val MIN_SCALE = 70
        const val MAX_SCALE = 200
        const val MIN_LINE = 1.2f
        const val MAX_LINE = 2.6f
        const val MIN_MARGIN = 0
        const val MAX_MARGIN = 64
    }
}

class ReadingPrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)

    fun load() = ReadingPrefs(
        fontScale = prefs.getInt("read_scale", 100),
        lineHeight = prefs.getFloat("read_line", 1.9f),
        margin = prefs.getInt("read_margin", 20),
        font = ReadingFont.fromId(prefs.getString("read_font", null)),
        tapToTurn = prefs.getBoolean("read_tap_turn", true),
        keepScreenOn = prefs.getBoolean("read_keep_on", false),
    ).normalized()

    fun save(value: ReadingPrefs) {
        val p = value.normalized()
        prefs.edit()
            .putInt("read_scale", p.fontScale)
            .putFloat("read_line", p.lineHeight)
            .putInt("read_margin", p.margin)
            .putString("read_font", p.font.id)
            .putBoolean("read_tap_turn", p.tapToTurn)
            .putBoolean("read_keep_on", p.keepScreenOn)
            .apply()
    }
}
