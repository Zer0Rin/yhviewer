package com.example.yhviewer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

enum class DocType { HTML, MARKDOWN, CODE, TXT, EPUB, UNKNOWN }

data class LoadedDoc(val name: String, val type: DocType, val text: String)

/** 文件名 / MIME → 类型。纯 Kotlin，可单元测试 */
object DocTypes {
    fun detect(name: String, mime: String?): DocType {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in setOf("html", "htm", "xhtml") -> DocType.HTML
            ext in setOf("md", "markdown", "mdown") -> DocType.MARKDOWN
            ext == "epub" -> DocType.EPUB
            CodeLanguages.forFileName(name) != null -> DocType.CODE   // 要在 txt 之前：CMakeLists.txt 是代码
            ext == "txt" -> DocType.TXT
            mime == "text/html" -> DocType.HTML
            mime == "text/markdown" || mime == "text/x-markdown" -> DocType.MARKDOWN
            mime == "application/epub+zip" -> DocType.EPUB
            else -> DocType.UNKNOWN                                    // 无扩展名的文本：按纯文本代码视图显示
        }
    }
}

/**
 * 读取别的 App 传来的 content:// Uri。
 * 三个入口：probe 只看文件名和类型；readText 读文本（自动识别编码）；copyTo 原样复制二进制（EPUB）。
 * 都带大小上限，边读边数字节，不信任对方声明的大小。
 */
object DocumentLoader {
    const val MAX_TEXT_BYTES = 20L * 1024 * 1024      // HTML / MD / 代码
    const val MAX_TXT_BYTES = 32L * 1024 * 1024       // 小说 TXT 普遍更大
    const val MAX_EPUB_BYTES = 100L * 1024 * 1024

    data class Source(val name: String, val type: DocType)

    suspend fun probe(context: Context, uri: Uri): Source = withContext(Dispatchers.IO) {
        val name = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "untitled"
        Source(name, DocTypes.detect(name, context.contentResolver.getType(uri)))
    }

    suspend fun readText(context: Context, uri: Uri, maxBytes: Long): String = withContext(Dispatchers.IO) {
        CharsetDetector.decode(readBytes(context, uri, maxBytes)).text
    }

    // End the growable buffer's ownership before allocating decoded text.
    private fun readBytes(context: Context, uri: Uri, maxBytes: Long): ByteArray {
        val bytes = ByteArrayOutputStream()
        open(context, uri).use { copyLimited(it, bytes, maxBytes) }
        return bytes.toByteArray()
    }

    suspend fun copyTo(context: Context, uri: Uri, dest: File, maxBytes: Long): Long = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        open(context, uri).use { input -> dest.outputStream().use { copyLimited(input, it, maxBytes) } }
    }

    /** 兼容旧调用：读成单个文本文档 */
    suspend fun load(context: Context, uri: Uri): LoadedDoc {
        val source = probe(context, uri)
        val max = if (source.type == DocType.TXT) MAX_TXT_BYTES else MAX_TEXT_BYTES
        return LoadedDoc(source.name, source.type, readText(context, uri, max))
    }

    private fun open(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw IOException("无法打开文件")

    private fun copyLimited(input: InputStream, out: OutputStream, maxBytes: Long): Long {
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw IOException("文件超过 ${maxBytes / 1024 / 1024}MB")
            out.write(buf, 0, n)
        }
        return total
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
}

/** 单页文档 → 可显示的 HTML。TXT / EPUB 是分章节的，不走这里 */
fun LoadedDoc.toDisplayHtml(
    context: Context,
    theme: ReaderTheme = ReaderTheme.AUTO,
    wrap: Boolean = false,
    svgSource: Boolean = false,
): String = when {
    // v10：SVG 默认以图片显示（<img> 里的 SVG 不执行脚本、不加载外部资源），菜单可切回源码
    isSvg && !svgSource -> SvgRenderer.render(text, theme)
    type == DocType.HTML -> text
    type == DocType.MARKDOWN -> MarkdownRenderer.render(text, MarkdownRenderer.template(context), theme)
    else -> CodeRenderer.render(
        text = text,
        lang = CodeLanguages.forFileName(name) ?: "plaintext",
        wrap = wrap,
        template = Templates.get(context, Templates.CODE),
        theme = theme,
    )
}

val LoadedDoc.isSvg get() = name.substringAfterLast('.', "").equals("svg", ignoreCase = true)

/** v10：SVG 预览页。纯函数，JVM 可测 */
object SvgRenderer {
    fun render(svg: String, theme: ReaderTheme): String {
        val data = java.util.Base64.getEncoder().encodeToString(svg.toByteArray(Charsets.UTF_8))
        return """<!doctype html><html lang="zh" data-theme="${theme.id}"><head><meta charset="utf-8">""" +
            """<meta name="viewport" content="width=device-width, initial-scale=1">""" +
            """<link rel="stylesheet" href="/assets/shared/themes.css"><style>""" +
            """html,body{margin:0;min-height:100%;background:var(--bg)}body{display:grid;place-items:center;min-height:100vh}""" +
            """figure{margin:0;padding:24px;display:grid;gap:12px;justify-items:center}""" +
            """img{max-width:100%;height:auto;background:repeating-conic-gradient(rgba(127,127,127,.18) 0 25%,transparent 0 50%) 0 0/16px 16px}""" +
            """figcaption{color:var(--muted);font:13px/1.5 var(--font-sans);text-align:center;max-width:32em}""" +
            """</style></head><body><figure><img alt="SVG 图片" src="data:image/svg+xml;base64,$data">""" +
            """<figcaption>SVG 以图片方式显示，其中的脚本不会运行。右上角菜单可以切换到源码。</figcaption></figure>""" +
            """<script>window.setTheme=function(t){if(['auto','paper','ink','terminal'].indexOf(t)>=0)document.documentElement.setAttribute('data-theme',t)}</script>""" +
            """</body></html>"""
    }
}
