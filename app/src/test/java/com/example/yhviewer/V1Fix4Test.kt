package com.example.yhviewer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 1.0 FIX4：真机验收 4.6.2（连续模式脚注被顶栏挡住）、5.9（代码复制丢空行） */
class V1Fix4Test {
    private fun asset(path: String) = File("src/main/assets/$path").readText()

    @Test
    fun codeCopyListenerIsOnDocument() {
        val t = asset("code/template.html")
        // Android 选字工具栏点"复制"时 WebView 失焦，copy 事件发给 <html>，挂在 #code 上收不到
        assertTrue(t.contains("document.addEventListener('copy'"))
        assertFalse(t.contains("codeEl.addEventListener('copy'"))
        assertTrue(t.contains("intersectsNode(codeEl)"))
    }

    @Test
    fun flowAnchorJumpLeavesRoomForTopBar() {
        val f = asset("book/flow.html")
        assertTrue(f.contains("el.getBoundingClientRect().top - topPad()"))
        assertTrue(f.contains("barPad = barFrom(rects)"))
    }
}
