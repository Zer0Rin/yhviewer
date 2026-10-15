package com.example.yhviewer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

enum class DocType { HTML, MARKDOWN, UNKNOWN }

data class LoadedDoc(val name: String, val type: DocType, val text: String)

/**
 * 把别的 App 传来的 content:// Uri 读成文本。
 * 注意：安卓上拿到的通常不是文件路径，只能通过 ContentResolver 读流。
 */
object DocumentLoader {
    private const val MAX_BYTES = 20L * 1024 * 1024 // 20MB 上限，防止误开巨型文件卡死

    suspend fun load(context: Context, uri: Uri): LoadedDoc = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "untitled"
        val type = detectType(name, resolver.getType(uri))

        val text = resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) throw IOException("文件超过 20MB")
                out.write(buf, 0, n)
            }
            String(out.toByteArray(), Charsets.UTF_8) // 先假设 UTF-8，AI 生成的文件基本都是
        } ?: throw IOException("无法打开文件")

        LoadedDoc(name, type, text)
    }

    /** content:// 的真实文件名要查 OpenableColumns，uri 本身往往是一串 id */
    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    /** 后缀优先，MIME 兜底（很多文件管理器把 .md 报成 text/plain 或 octet-stream） */
    private fun detectType(name: String, mime: String?): DocType {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in setOf("html", "htm", "xhtml") -> DocType.HTML
            ext in setOf("md", "markdown", "mdown") -> DocType.MARKDOWN
            mime == "text/html" -> DocType.HTML
            mime == "text/markdown" || mime == "text/x-markdown" -> DocType.MARKDOWN
            else -> DocType.UNKNOWN
        }
    }
}

/**
 * 转成 WebView 能显示的 HTML。
 * HTML 原样显示；Markdown 使用本地渲染模板；未知类型仍以转义后的纯文本显示。
 */
fun LoadedDoc.toDisplayHtml(context: Context, theme: ReaderTheme = ReaderTheme.AUTO): String = when (type) {
    DocType.HTML -> text
    DocType.MARKDOWN -> MarkdownRenderer.render(text, MarkdownRenderer.template(context), theme)
    DocType.UNKNOWN -> """
        <!doctype html>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          :root { color-scheme: light dark; }
          body { margin: 0; padding: 16px; font: 14px/1.6 monospace; }
          pre { white-space: pre-wrap; word-break: break-word; margin: 0; }
        </style>
        <pre>${escapeHtml(text)}</pre>
    """.trimIndent()
}

private fun escapeHtml(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
