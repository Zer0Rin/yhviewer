package com.example.yhviewer

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Unique, app-private sources imported through the ordinary repository, never direct DB inserts. */
internal class V11GeneratedFixtures(private val context: Context) {
    private val sources = mutableListOf<File>()
    private val repo = ShelfRepository(context, AppDatabase.get(context))
    private val dao = AppDatabase.get(context).bookDao()
    fun file(extension: String): File = File(context.cacheDir, "v11-${UUID.randomUUID()}.$extension").also { sources += it }
    suspend fun import(file: File): Long = repo.import(Uri.fromFile(file))
    suspend fun clean() {
        // Resolve by source so even an import that fails after inserting its row is owned.
        sources.forEach { file -> dao.findBySource(Uri.fromFile(file).toString())?.let { repo.delete(it.id) } }
        sources.forEach { it.delete() }
    }
    fun txt(count: Int = 12, paragraphs: Int = 20, short: Boolean = false): File = file("txt").also { file ->
        file.bufferedWriter().use { out ->
            repeat(count) { chapter ->
                out.append("第${chapter + 1}章 Flow ${chapter + 1}\n")
                repeat(if (short) 1 else paragraphs) { p ->
                    out.append(paragraph(chapter, p)).append("\n\n")
                }
            }
        }
    }
    fun epub(count: Int = 12, malicious: Boolean = false): File = file("epub").also { file ->
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            fun entry(path: String, body: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(body.toByteArray()); zip.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            entry("OPS/book.opf", """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">v11-generated</dc:identifier><dc:title>Generated flow</dc:title><dc:language>en</dc:language></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>${(0 until count).joinToString("") { "<item id=\"c$it\" href=\"c$it.xhtml\" media-type=\"application/xhtml+xml\"/>" }}</manifest><spine>${(0 until count).joinToString("") { "<itemref idref=\"c$it\"/>" }}</spine></package>""")
            entry("OPS/nav.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol>${(0 until count).joinToString("") { "<li><a href=\"c$it.xhtml\">第${it + 1}章 Flow ${it + 1}</a></li>" }}</ol></nav></body></html>""")
            repeat(count) { c ->
                val attack = if (malicious) """<script>parent.document.title='BOOK_ESCAPED';parent.document.body.setAttribute('data-escaped','yes');</script><meta http-equiv="refresh" content="0;url=https://example.invalid/escaped"/>""" else ""
                val body = buildString {
                    append("<h1>第${c + 1}章 Flow ${c + 1}</h1>")
                    if (c == 4) append("<p><a id=\"cross-link\" href=\"c7.xhtml#p12\">Jump native anchor</a></p>")
                    if (malicious) append("""<p><a id="top-link" href="https://example.invalid/escaped" target="_top">Escape top</a></p><img src="missing.png" onerror="parent.document.title='BOOK_ESCAPED';parent.document.body.setAttribute('data-escaped','yes')"/>""")
                    repeat(20) { p -> append("<p id=\"p$p\">${paragraph(c, p)}</p>") }
                }
                entry("OPS/c$c.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Flow ${c + 1}</title>$attack</head><body>$body</body></html>""")
            }
        }
    }
    companion object {
        const val NEEDLE = "flowneedle"
        // Exactly three occurrences per chapter, separated enough to validate nth scrolling.
        fun paragraph(chapter: Int, p: Int): String = "Anchor chapter $chapter paragraph $p. " +
            (if (p in listOf(3, 9, 16)) "$NEEDLE occurrence $p. " else "") +
            "Generated mountain letters stay readable across chapter boundaries. 山海之间，远方的来信。".repeat(2)
    }
}
