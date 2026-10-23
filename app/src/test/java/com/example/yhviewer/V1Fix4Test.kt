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
        assertTrue(f.contains("y = top(s) + el.getBoundingClientRect().top; pad = topPad();"))
        assertTrue(f.contains("barPad = barFrom(rects)"))
    }

    /** 1.0 FIX5：连续模式跳到章首时，章节标题停在顶栏下方，并且算作这一章（大 TXT 目录跳转停在上一章） */
    @Test
    fun flowChapterLineIsTheTopBarEdge() {
        val f = asset("book/flow.html")
        assertTrue(f.contains("var probe = window.scrollY + topPad() + 0.5"))
        assertTrue(f.contains("var pad = r === 0 ? topPad() : 0;"))
        assertTrue(f.contains("function settleJump()"))
        // 行为本身由 scripts/test-flow-shell.cjs 的 top-bar chapter line 断言覆盖
        assertTrue(File("../scripts/test-flow-shell.cjs").readText().contains("top-bar chapter line"))
    }
}
