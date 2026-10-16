package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 在内存里拼一个 zip，用来构造正常和恶意的 EPUB */
private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val bos = ByteArrayOutputStream()
    ZipOutputStream(bos).use { zip ->
        for ((name, data) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(data)
            zip.closeEntry()
        }
    }
    return bos.toByteArray()
}

private fun String.b() = toByteArray(Charsets.UTF_8)

private const val CONTAINER = """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""

private fun opf(withNav: Boolean = true, withNcx: Boolean = false) = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>夜车</dc:title></metadata>
  <manifest>
    ${if (withNav) """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""" else ""}
    ${if (withNcx) """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""" else ""}
    <item id="c1" href="text/ch%201.xhtml" media-type="application/xhtml+xml"/>
    <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
    <item id="css" href="style.css" media-type="text/css"/>
  </manifest>
  <spine ${if (withNcx) "toc=\"ncx\"" else ""}><itemref idref="c1"/><itemref idref="c2"/><itemref idref="css"/></spine>
</package>"""

private const val NAV = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
<nav epub:type="toc"><ol>
  <li><a href="text/ch%201.xhtml">第一章&nbsp;出发</a></li>
  <li><a href="text/ch2.xhtml#start">第二章 抵达</a></li>
</ol></nav></body></html>"""

private const val NCX = """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><navMap>
  <navPoint id="p1"><navLabel><text>Chapter One</text></navLabel><content src="text/ch%201.xhtml"/>
    <navPoint id="p1a"><navLabel><text>Section</text></navLabel><content src="text/ch2.xhtml"/></navPoint>
  </navPoint>
</navMap></ncx>"""

private const val CHAPTER = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title></head><body><p>hi</p></body></html>"""

private fun validEpub(withNav: Boolean = true, withNcx: Boolean = false): ByteArray {
    val entries = mutableListOf(
        "mimetype" to "application/epub+zip".b(),
        "META-INF/container.xml" to CONTAINER.b(),
        "OEBPS/content.opf" to opf(withNav, withNcx).b(),
        "OEBPS/text/ch 1.xhtml" to CHAPTER.b(),
        "OEBPS/text/ch2.xhtml" to CHAPTER.b(),
        "OEBPS/style.css" to "p{}".b(),
    )
    if (withNav) entries += "OEBPS/nav.xhtml" to NAV.b()
    if (withNcx) entries += "OEBPS/toc.ncx" to NCX.b()
    return zipOf(*entries.toTypedArray())
}

class SafeUnzipTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun extract(bytes: ByteArray, limits: SafeUnzip.Limits = SafeUnzip.Limits()): File {
        val dir = File(tmp.root, "book")
        SafeUnzip.extract(ByteArrayInputStream(bytes), dir, limits)
        return dir
    }

    private fun assertRejected(bytes: ByteArray, limits: SafeUnzip.Limits = SafeUnzip.Limits()) {
        try {
            extract(bytes, limits)
            fail("应当拒绝")
        } catch (e: EpubFormatException) {
            // 预期
        } catch (e: java.util.zip.ZipException) {
            // 新版 JDK / Android 14+ 的 ZipInputStream 自己也会拦截，同样算通过
        }
    }

    @Test fun extractsValidEpub() {
        val dir = extract(validEpub())
        assertTrue(File(dir, "OEBPS/text/ch 1.xhtml").isFile)
    }

    @Test fun zipSlipWithDotDotIsRejected() {
        assertRejected(zipOf("../../evil.txt" to "x".b()))
        assertFalse(File(tmp.root, "evil.txt").exists())
        assertFalse(File(tmp.root.parentFile, "evil.txt").exists())
    }

    @Test fun zipSlipNestedDotDotIsRejected() {
        assertRejected(zipOf("OEBPS/../../evil.txt" to "x".b()))
    }

    @Test fun absolutePathIsRejected() {
        assertRejected(zipOf("/tmp/evil.txt" to "x".b()))
    }

    @Test fun backslashPathIsRejected() {
        assertRejected(zipOf("..\\evil.txt" to "x".b()))
    }

    @Test fun zipBombTotalSizeIsRejected() {
        // 5 MB 的 0 压缩后只有几 KB；限制总大小 1 MB
        val zeros = ByteArray(5 * 1024 * 1024)
        val bomb = zipOf("a.bin" to zeros)
        assertTrue("压缩后应当很小", bomb.size < 100 * 1024)
        assertRejected(bomb, SafeUnzip.Limits(maxTotalBytes = 1024 * 1024, maxEntryBytes = 10L * 1024 * 1024))
    }

    @Test fun singleEntryLimit() {
        assertRejected(zipOf("a.bin" to ByteArray(2048)), SafeUnzip.Limits(maxEntryBytes = 1024))
    }

    @Test fun tooManyEntries() {
        val many = (1..20).map { "f$it.txt" to "x".b() }.toTypedArray()
        assertRejected(zipOf(*many), SafeUnzip.Limits(maxEntries = 10))
    }

    @Test fun emptyOrNonZipInputIsRejected() {
        assertRejected("this is not a zip".b())
    }
}

class EpubParserTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun parse(bytes: ByteArray): EpubBook {
        val dir = File(tmp.root, "book")
        SafeUnzip.extract(ByteArrayInputStream(bytes), dir)
        return EpubParser.parse(dir)
    }

    @Test fun readsTitleSpineAndNavTitles() {
        val book = parse(validEpub())
        assertEquals("夜车", book.title)
        assertEquals(listOf("OEBPS/text/ch 1.xhtml", "OEBPS/text/ch2.xhtml"), book.chapters.map { it.path })
        // &nbsp; 被正确处理；带 #锚点 的链接也能对上章节
        assertEquals(listOf("第一章 出发", "第二章 抵达"), book.chapters.map { it.title })
    }

    @Test fun spineSkipsNonHtmlItems() {
        assertEquals(2, parse(validEpub()).chapters.size) // style.css 虽在 spine 里，但不是章节
    }

    @Test fun fallsBackToNcxTitles() {
        val book = parse(validEpub(withNav = false, withNcx = true))
        assertEquals(listOf("Chapter One", "Section"), book.chapters.map { it.title })
    }

    @Test fun fallsBackToNumberedTitles() {
        val book = parse(validEpub(withNav = false, withNcx = false))
        assertEquals(listOf("第 1 节", "第 2 节"), book.chapters.map { it.title })
    }

    @Test fun entityDeclarationIsRejected() {
        val xxe = """<?xml version="1.0"?><!DOCTYPE c [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>
            <rootfile full-path="&x;"/></rootfiles></container>"""
        val bytes = zipOf("META-INF/container.xml" to xxe.b())
        try {
            parse(bytes)
            fail("应当拒绝")
        } catch (e: EpubFormatException) {
            assertTrue(e.message!!.contains("实体"))
        }
    }

    @Test fun missingContainerIsRejected() {
        try {
            parse(zipOf("mimetype" to "application/epub+zip".b()))
            fail("应当拒绝")
        } catch (e: EpubFormatException) {
            // 预期
        }
    }

    @Test fun resolveHref() {
        assertEquals("OEBPS/text/a.xhtml", EpubParser.resolveHref("OEBPS", "text/a.xhtml#p3"))
        assertEquals("OEBPS/b.xhtml", EpubParser.resolveHref("OEBPS/text", "../b.xhtml"))
        assertEquals("OEBPS/c d.xhtml", EpubParser.resolveHref("OEBPS", "c%20d.xhtml"))
        assertEquals("a+b.xhtml", EpubParser.resolveHref("", "a+b.xhtml"))            // + 不是空格
        assertNull(EpubParser.resolveHref("OEBPS", "../../escape.xhtml"))             // 跳出书根
        assertNull(EpubParser.resolveHref("OEBPS", "https://example.com/x.html"))
        assertNull(EpubParser.resolveHref("OEBPS", "/abs.xhtml"))
        assertNull(EpubParser.resolveHref("OEBPS", "#only-anchor"))
    }
}

class EpubInjectorTest {
    @Test fun addsThemeAndStylesheetsBeforeHeadClose() {
        val out = EpubInjector.inject(CHAPTER, ReaderTheme.INK)
        assertTrue(out.contains("<html data-theme=\"ink\" xmlns="))
        assertTrue(out.contains(EpubInjector.LINKS + "</head>"))
    }

    @Test fun replacesExistingDataTheme() {
        val out = EpubInjector.inject("<html data-theme='x'><head></head></html>", ReaderTheme.PAPER)
        assertEquals(1, Regex("data-theme").findAll(out).count())
        assertTrue(out.contains("data-theme=\"paper\""))
    }

    @Test fun createsHeadWhenMissing() {
        val out = EpubInjector.inject("<html><body>x</body></html>", ReaderTheme.AUTO)
        assertTrue(out.startsWith("<html data-theme=\"auto\"><head>"))
    }

    @Test fun nonHtmlIsUntouched() {
        assertEquals("plain", EpubInjector.inject("plain", ReaderTheme.INK))
    }

    @Test fun uppercaseTagsAreHandled() {
        val out = EpubInjector.inject("<HTML><HEAD></HEAD></HTML>", ReaderTheme.TERMINAL)
        assertTrue(out.contains("data-theme=\"terminal\""))
        assertTrue(out.contains(EpubInjector.LINKS + "</HEAD>"))
    }
}

class EpubValidationTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun parse(vararg entries: Pair<String, ByteArray>): EpubBook {
        val dir = File(tmp.root, "edge-book")
        SafeUnzip.extract(ByteArrayInputStream(zipOf(*entries)), dir)
        return EpubParser.parse(dir)
    }

    @Test fun utf16MetadataAndChaptersAreDecodedBeforeParsing() {
        val book=parse("META-INF/container.xml" to CONTAINER.toByteArray(Charsets.UTF_16),
            "OEBPS/content.opf" to opf(false).replace("UTF-8","UTF-16").toByteArray(Charsets.UTF_16),
            "OEBPS/text/ch 1.xhtml" to CHAPTER.replace("UTF-8","UTF-16").toByteArray(Charsets.UTF_16),
            "OEBPS/text/ch2.xhtml" to CHAPTER.b())
        assertEquals("夜车",book.title)
        assertEquals(2,book.chapters.size)
    }

    @Test(expected = EpubFormatException::class)
    fun utf16EntitiesAreRejectedBeforeReachingTheRenderer() {
        val unsafe="""<!DOCTYPE html [<!ENTITY secret SYSTEM "file:///secret">]><html><body>&secret;</body></html>"""
        parse("META-INF/container.xml" to CONTAINER.b(),"OEBPS/content.opf" to opf(false).b(),
            "OEBPS/text/ch 1.xhtml" to unsafe.toByteArray(Charsets.UTF_16),"OEBPS/text/ch2.xhtml" to CHAPTER.b())
    }

    @Test(expected = EpubFormatException::class)
    fun missingSpineChapterDoesNotSilentlyDisappear() {
        parse("META-INF/container.xml" to CONTAINER.b(), "OEBPS/content.opf" to opf(false).b(),
            "OEBPS/text/ch 1.xhtml" to CHAPTER.b())
    }

    @Test(expected = EpubFormatException::class)
    fun unsafeOptionalNavigationCannotBeSwallowedByFallback() {
        val nav = """<!DOCTYPE html [<!ENTITY secret SYSTEM "file:///secret">]><html xmlns="http://www.w3.org/1999/xhtml"><body>&secret;</body></html>"""
        parse("META-INF/container.xml" to CONTAINER.b(), "OEBPS/content.opf" to opf().b(),
            "OEBPS/nav.xhtml" to nav.b(), "OEBPS/text/ch 1.xhtml" to CHAPTER.b(),
            "OEBPS/text/ch2.xhtml" to CHAPTER.b())
    }

    @Test(expected = EpubFormatException::class)
    fun entityDeclarationInSpineChapterIsRejected() {
        val chapter = """<!DOCTYPE html [<!ENTITY secret SYSTEM "file:///secret">]><html xmlns="http://www.w3.org/1999/xhtml"><body>&secret;</body></html>"""
        parse("META-INF/container.xml" to CONTAINER.b(), "OEBPS/content.opf" to opf(false).b(),
            "OEBPS/text/ch 1.xhtml" to chapter.b(), "OEBPS/text/ch2.xhtml" to CHAPTER.b())
    }

    @Test(expected = EpubFormatException::class)
    fun directoryEntriesCannotHideUncountedPayloads() {
        SafeUnzip.extract(ByteArrayInputStream(zipOf("folder/" to ByteArray(4096))), File(tmp.root, "dir-payload"))
    }

    @Test fun decodedPathsAreValidatedAgain() {
        listOf("%2Fsecret.xhtml", "file%3Asecret.xhtml", "..%5Csecret.xhtml", "x%00.xhtml").forEach {
            assertNull(it, EpubParser.resolveHref("OEBPS", it))
        }
    }
}
