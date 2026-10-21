package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** v17：翻页累加、动效预算、EPUB 解析缓存 */
class V17MotionTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun rapidPageTurnsAccumulateFromThePreviousTarget() {
        val step = PageTurn.step(2000)
        assertEquals(1800, step)
        // 连点三下：每次都从上一次的目标算，正好三屏
        var target = 0
        repeat(3) { target = PageTurn.target(target, step, maxScroll = 100_000, forward = true) }
        assertEquals(5400, target)
        // 不越过本章结尾 / 开头
        assertEquals(6000, PageTurn.target(5400, step, maxScroll = 6000, forward = true))
        assertEquals(0, PageTurn.target(1000, step, maxScroll = 6000, forward = false))
    }

    @Test fun uiMotionStaysInsideBudget() {
        listOf(Motion.PRESS_MS, Motion.RELEASE_MS, Motion.ENTER_MS, Motion.EXIT_MS, Motion.SCREEN_MS, Motion.BAR_ENTER_MS, Motion.PAGE_TURN_MS)
            .forEach { assertTrue("$it ms", it in 1..300) }
        assertTrue("消失要比出现快", Motion.EXIT_MS < Motion.ENTER_MS)
        assertTrue("按下要比松手快", Motion.PRESS_MS < Motion.RELEASE_MS)
    }

    @Test fun epubParseIsCachedPerExtractedDirectory() {
        val root = book("春")
        val first = EpubCache.parse(root)
        assertSame(first, EpubCache.parse(root))
        EpubCache.invalidate(root)
        assertNotSame(first, EpubCache.parse(root))
        // 另一本书（另一个目录）不会拿到这本的结果
        assertEquals("夏", EpubCache.parse(book("夏")).title)
    }

    private fun book(title: String): File {
        val root = tmp.newFolder()
        fun put(path: String, text: String) = File(root, path).apply { parentFile.mkdirs(); writeText(text) }
        put("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""")
        put("OEBPS/content.opf", """<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0">
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>$title</dc:title></metadata>
            <manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest>
            <spine><itemref idref="c1"/></spine></package>""")
        put("OEBPS/c1.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><head></head><body><p>$title</p></body></html>""")
        return root
    }
}
