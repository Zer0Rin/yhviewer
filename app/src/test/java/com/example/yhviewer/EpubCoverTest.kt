package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** EpubParser 的封面和作者识别。直接在临时目录里摆出"解压后的书" */
class EpubCoverTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun book(metadata: String, manifest: String, files: Map<String, String>): File {
        val root = tmp.newFolder()
        fun put(path: String, text: String) = File(root, path).apply { parentFile?.mkdirs(); writeText(text) }
        put(
            "META-INF/container.xml",
            """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
               <rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
        )
        put(
            "OEBPS/content.opf",
            """<?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>夜车</dc:title>$metadata</metadata>
              <manifest>
                <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                $manifest
              </manifest>
              <spine><itemref idref="c1"/></spine>
            </package>""",
        )
        put("OEBPS/c1.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>x</p></body></html>""")
        files.forEach { (p, t) -> put("OEBPS/$p", t) }
        return root
    }

    @Test fun epub3CoverImageProperty() {
        val root = book(
            "",
            """<item id="a" href="img/a.jpg" media-type="image/jpeg"/>
               <item id="front" href="img/front%20page.jpg" media-type="image/jpeg" properties="cover-image"/>""",
            mapOf("img/a.jpg" to "x", "img/front page.jpg" to "x"),
        )
        assertEquals("OEBPS/img/front page.jpg", EpubParser.parse(root).coverPath)
    }

    @Test fun epub2MetaCover() {
        val root = book(
            """<meta name="cover" content="pic"/>""",
            """<item id="pic" href="title.png" media-type="image/png"/>""",
            mapOf("title.png" to "x"),
        )
        assertEquals("OEBPS/title.png", EpubParser.parse(root).coverPath)
    }

    @Test fun fallbackByName() {
        val root = book("", """<item id="i1" href="images/Cover.JPG" media-type="image/jpeg"/>""", mapOf("images/Cover.JPG" to "x"))
        assertEquals("OEBPS/images/Cover.JPG", EpubParser.parse(root).coverPath)
    }

    @Test fun ignoresSvgMissingFilesAndOutsidePaths() {
        val svg = book("", """<item id="c" href="cover.svg" media-type="image/svg+xml" properties="cover-image"/>""", mapOf("cover.svg" to "<svg/>"))
        assertNull(EpubParser.parse(svg).coverPath)

        val missing = book("""<meta name="cover" content="pic"/>""", """<item id="pic" href="gone.jpg" media-type="image/jpeg"/>""", emptyMap())
        assertNull(EpubParser.parse(missing).coverPath)

        // 想跳出书根目录的 href 在 manifest 解析阶段就被丢弃
        val escape = book("", """<item id="cover" href="../../../etc/cover.jpg" media-type="image/jpeg" properties="cover-image"/>""", emptyMap())
        assertNull(EpubParser.parse(escape).coverPath)
    }

    @Test fun rejectsEncodedAbsoluteAndSchemeCoverPaths() {
        for (href in listOf("%2Fetc/cover.jpg", "%66ile%3Acover.jpg", "img%5Ccover.jpg", "cover%00.jpg", "bad%GG.jpg")) {
            val root = book("", """<item id="cover" href="$href" media-type="image/jpeg" properties="cover-image"/>""", emptyMap())
            assertNull("Unsafe cover: $href", EpubParser.parse(root).coverPath)
        }
    }

    @Test fun rejectsCoverSymlinkOutsideBookRoot() {
        val root = book("", """<item id="cover" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>""", emptyMap())
        val outside = tmp.newFile("outside-cover.jpg").apply { writeText("x") }
        java.nio.file.Files.createSymbolicLink(File(root, "OEBPS/cover.jpg").toPath(), outside.toPath())
        assertNull(EpubParser.parse(root).coverPath)
    }

    @Test fun invalidDeclaredCoverFallsBackToExistingBitmap() {
        val root = book("", """<item id="missing" href="missing.jpg" media-type="image/jpeg" properties="cover-image"/>
            <item id="cover" href="cover.png" media-type="image/png"/>""", mapOf("cover.png" to "x"))
        assertEquals("OEBPS/cover.png", EpubParser.parse(root).coverPath)
    }

    @Test fun authorWhitespaceIsNormalizedAndBounded() {
        val name = "长".repeat(100)
        val root = book("<dc:creator>  $name  </dc:creator>", "", emptyMap())
        assertEquals("长".repeat(80), EpubParser.parse(root).author)
    }

    @Test fun noCoverIsFine() {
        val root = book("", "", emptyMap())
        val parsed = EpubParser.parse(root)
        assertNull(parsed.coverPath)
        assertEquals(1, parsed.chapters.size)
    }

    @Test fun author() {
        val root = book("""<dc:creator opf:role="aut" xmlns:opf="http://www.idpf.org/2007/opf">  周南
            </dc:creator><dc:creator>译者</dc:creator>""", "", emptyMap())
        assertEquals("周南", EpubParser.parse(root).author)
        assertNull(EpubParser.parse(book("", "", emptyMap())).author)
    }
}
