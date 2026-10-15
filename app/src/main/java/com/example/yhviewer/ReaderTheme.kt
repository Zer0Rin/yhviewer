package com.example.yhviewer

import android.content.Context

/**
 * Markdown 阅读主题。id 必须和 template.html 里 data-theme / setTheme 白名单一致。
 * 名字刻意用中性词，不用任何产品或品牌名。
 */
enum class ReaderTheme(val id: String, val label: String) {
    AUTO("auto", "跟随系统"),
    PAPER("paper", "纸张（浅）"),
    INK("ink", "墨色（深）"),
    TERMINAL("terminal", "终端"),
    ;

    companion object {
        /** 存储里读到未知值（比如以后删了某个主题）时回退到 AUTO，而不是崩溃 */
        fun fromId(id: String?): ReaderTheme = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/** 只存一个字符串，用 SharedPreferences 足够，不必为此引入 DataStore */
class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)

    fun load(): ReaderTheme = ReaderTheme.fromId(prefs.getString(KEY, null))

    fun save(theme: ReaderTheme) {
        prefs.edit().putString(KEY, theme.id).apply()
    }

    private companion object {
        const val KEY = "md_theme"
    }
}
