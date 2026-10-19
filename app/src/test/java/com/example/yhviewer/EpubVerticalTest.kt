package com.example.yhviewer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** v10：竖排 EPUB 识别，以及转横排 CSS 能安全注入 */
class EpubVerticalTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun book(spineAttr: String, css: String): File {
        val root = tmp.newFolder()
        fun put(path: String, text: String) = File(root, path).apply { parentFile.mkdirs(); writeText(text) }
        put("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""")
        put("OEBPS/content.opf", """<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0">
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>春日書簡</dc:title></metadata>
            <manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/><item id="css" href="s/book.css" media-type="text/css"/></manifest>
            <spine $spineAttr><itemref idref="c1"/></spine></package>""")
        put("OEBPS/c1.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><head><link rel="stylesheet" href="s/book.css"/></head><body><p>春</p></body></html>""")
        put("OEBPS/s/book.css", css)
        return root
    }

    @Test fun detectsVerticalBySpineOrCss() {
        assertTrue(EpubParser.parse(book("""page-progression-direction="rtl"""", "p{}")).vertical)
        assertTrue(EpubParser.parse(book("", "body{writing-mode:vertical-rl;}")).vertical)
        assertTrue(EpubParser.parse(book("", "html { -epub-writing-mode: vertical-rl; writing-mode : vertical-lr }")).vertical)
        assertFalse(EpubParser.parse(book("", "body{writing-mode:horizontal-tb}")).vertical)
    }

    @Test fun horizontalCssIsSafeToInject() {
        val out = EpubInjector.inject(
            """<html xmlns="http://www.w3.org/1999/xhtml"><head></head><body/></html>""",
            ReaderTheme.PAPER,
            ":root{--read-scale:1.00}" + EpubInjector.HORIZONTAL_CSS,
        )
        assertTrue(out.contains("writing-mode:horizontal-tb !important"))
    }
}
