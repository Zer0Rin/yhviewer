package com.example.yhviewer

import android.content.Context

/** Legacy assertions exercise one chapter per page; restore an absent preference as absent. */
internal class LegacyChapterMode(context: Context) {
    private val prefs = context.getSharedPreferences("reader_prefs", 0)
    private val existed = prefs.contains("read_continuous")
    private val previous = prefs.getBoolean("read_continuous", true)
    init { check(prefs.edit().putBoolean("read_continuous", false).commit()) }
    fun restore() {
        val edit = prefs.edit()
        if (existed) edit.putBoolean("read_continuous", previous) else edit.remove("read_continuous")
        check(edit.commit())
    }
}
