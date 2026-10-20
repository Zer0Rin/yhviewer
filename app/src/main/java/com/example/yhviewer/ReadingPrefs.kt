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
    /** v11：章节之间上下滑动无缝连接；关掉则一章一页（滑到底再翻页换章） */
    val continuous: Boolean = true,
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

/**
 * 偏好存储。书（TXT / EPUB）用默认前缀；v13 起 Markdown / HTML / 代码用 "doc_" 前缀单独存一份，
 * 两边的字号互不影响（网页和小说的合适字号差别很大）。
 */
class ReadingPrefsStore(
    context: Context,
    private val prefix: String = "",
    private val defaults: ReadingPrefs = ReadingPrefs(),
) {
    private val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    private fun k(name: String) = prefix + name

    fun load() = ReadingPrefs(
        fontScale = prefs.getInt(k("read_scale"), defaults.fontScale),
        lineHeight = prefs.getFloat(k("read_line"), defaults.lineHeight),
        margin = prefs.getInt(k("read_margin"), defaults.margin),
        font = ReadingFont.fromId(prefs.getString(k("read_font"), defaults.font.id)),
        tapToTurn = prefs.getBoolean(k("read_tap_turn"), defaults.tapToTurn),
        keepScreenOn = prefs.getBoolean(k("read_keep_on"), defaults.keepScreenOn),
        continuous = prefs.getBoolean(k("read_continuous"), defaults.continuous),
    ).normalized()

    fun save(value: ReadingPrefs) {
        val p = value.normalized()
        prefs.edit()
            .putInt(k("read_scale"), p.fontScale)
            .putFloat(k("read_line"), p.lineHeight)
            .putInt(k("read_margin"), p.margin)
            .putString(k("read_font"), p.font.id)
            .putBoolean(k("read_tap_turn"), p.tapToTurn)
            .putBoolean(k("read_keep_on"), p.keepScreenOn)
            .putBoolean(k("read_continuous"), p.continuous)
            .apply()
    }

    companion object {
        const val DOC_PREFIX = "doc_"
        /** Markdown 模板原本的排版：17px、行距 1.75、左右 18px */
        val DOC_DEFAULTS = ReadingPrefs(lineHeight = 1.75f, margin = 18)
    }
}
