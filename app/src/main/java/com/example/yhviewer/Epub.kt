package com.example.yhviewer

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/*
 * EPUB = 一个 zip 包，里面是 XHTML 章节、CSS、图片，加上描述目录结构的 XML。
 * 对阅读器来说它是"不可信的压缩包 + 不可信的 HTML"，所以这里的重点是安全：
 *
 *  1. Zip Slip：条目名形如 "../../shared_prefs/x.xml" 时，天真的解压会把文件写到目录外面
 *  2. Zip 炸弹：几 KB 的压缩包解压出几 GB。不能相信 zip 头里声明的大小，要边写边数实际字节
 *  3. XXE：XML 里的 <!ENTITY> 可以引用本地文件。EPUB 的 OPF/NCX 从来不需要实体声明，直接拒绝
 *  4. 章节里的脚本：渲染时用 script-src 'none' 的 CSP，书里的 JS 一行都不会执行（见 SandboxWebView）
 *
 * 本文件只用 java.* / javax.* / org.w3c.*，在 JVM 单元测试里可以直接跑。
 */

class EpubFormatException(message: String) : IOException(message)

data class EpubChapter(val title: String, val path: String) // path：相对书根目录，用 "/" 分隔
data class EpubBook(val title: String?, val chapters: List<EpubChapter>)

object SafeUnzip {

    data class Limits(
        val maxEntries: Int = 3_000,
        val maxEntryBytes: Long = 50L * 1024 * 1024,
        val maxTotalBytes: Long = 200L * 1024 * 1024,
    )

    fun extract(input: InputStream, destDir: File, limits: Limits = Limits()) {
        val root = destDir.canonicalFile
        root.mkdirs()
        var entries = 0
        var total = 0L
        val buf = ByteArray(16 * 1024)
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > limits.maxEntries) throw EpubFormatException("压缩包内文件数超过 ${limits.maxEntries}")
                val target = resolveSafely(root, entry.name)
                if (entry.isDirectory) {
                    if (zip.read() != -1) throw EpubFormatException("目录条目不能包含文件内容")
                    target.mkdirs()
                    continue
                }
                target.parentFile?.mkdirs()
                var written = 0L
                FileOutputStream(target).use { out ->
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        written += n
                        total += n
                        if (written > limits.maxEntryBytes) throw EpubFormatException("压缩包内单个文件过大：${entry.name}")
                        if (total > limits.maxTotalBytes) throw EpubFormatException("解压后总大小超过上限")
                        out.write(buf, 0, n)
                    }
                }
            }
        }
        if (entries == 0) throw EpubFormatException("不是有效的 EPUB：压缩包为空或已损坏")
    }

    /**
     * 两道检查：先按名字拒绝明显的穿越写法，再用 canonical 路径兜底确认最终落在 root 里面。
     * Android 14+ 的 ZipInputStream 自己也会拦一部分，但旧版本不会，这里统一处理。
     */
    internal fun resolveSafely(root: File, name: String): File {
        if (name.isEmpty() || name.startsWith("/") || name.contains('\\') || name.contains('\u0000') ||
            name.split('/').any { it == ".." }
        ) {
            throw EpubFormatException("压缩包含非法路径：$name")
        }
        val target = File(root, name).canonicalFile
        if (!target.path.startsWith(root.path + File.separator)) {
            throw EpubFormatException("压缩包含非法路径：$name")
        }
        return target
    }
}

object EpubParser {

    private const val MAX_XML_BYTES = 10 * 1024 * 1024
    private const val OPS_NS = "http://www.idpf.org/2007/ops"

    private data class Item(val href: String, val mediaType: String, val properties: String)

    fun parse(root: File): EpubBook {
        val container = readXml(File(root, "META-INF/container.xml"))
        val opfPath = container.byName("rootfile").firstOrNull()?.getAttribute("full-path")
            ?.let { resolveHref("", it) }
            ?: throw EpubFormatException("找不到 OPF（META-INF/container.xml 无 rootfile）")
        val opf = readXml(File(root, opfPath))
        val opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")

        val manifest = mutableMapOf<String, Item>()
        for (el in opf.byName("item")) {
            val href = resolveHref(opfDir, el.getAttribute("href")) ?: continue
            manifest[el.getAttribute("id")] = Item(href, el.getAttribute("media-type"), el.getAttribute("properties"))
        }

        val spine = opf.byName("itemref")
            .map { manifest[it.getAttribute("idref")] ?: throw EpubFormatException("章节引用不存在") }
            .filter { it.mediaType.contains("html") }
        spine.forEach {
            if (!File(root, it.href).isFile || File(root, it.href).length() == 0L) {
                throw EpubFormatException("EPUB 缺少章节：${it.href}")
            }
            // Chapters reach WebView's XML/HTML renderer too; apply entity/size checks here,
            // not just to the container, OPF and optional navigation documents.
            readXmlText(File(root, it.href))
        }
        if (spine.isEmpty()) throw EpubFormatException("EPUB 没有可阅读的章节")

        val title = opf.byName("title").firstOrNull()?.textContent?.trim()?.ifBlank { null }

        // 目录标题：EPUB3 的 nav 文档优先，其次 EPUB2 的 NCX；都解析不了就用"第 N 节"
        // Optional TOC syntax may fall back, but unsafe entity declarations must never be swallowed.
        manifest.values.filter { "nav" in it.properties.split(' ') || it.mediaType == "application/x-dtbncx+xml" }
            .forEach { item -> File(root, item.href).takeIf { it.isFile }?.let(::readXmlText) }
        val tocTitles = runCatching { navTitles(root, manifest.values) }.getOrNull()
            ?: runCatching { ncxTitles(root, opf, manifest) }.getOrNull()
            ?: emptyMap()

        val chapters = spine.mapIndexed { i, item ->
            EpubChapter(tocTitles[item.href] ?: "第 ${i + 1} 节", item.href)
        }
        return EpubBook(title, chapters)
    }

    private fun navTitles(root: File, items: Collection<Item>): Map<String, String>? {
        val nav = items.firstOrNull { it.properties.split(' ').contains("nav") } ?: return null
        val doc = readXml(File(root, nav.href), relaxEntities = true)
        val navEls = doc.byName("nav")
        val toc = navEls.firstOrNull { it.getAttributeNS(OPS_NS, "type").split(' ').contains("toc") } ?: navEls.firstOrNull()
            ?: return null
        val baseDir = nav.href.substringBeforeLast('/', "")
        val titles = linkedMapOf<String, String>()
        for (a in toc.byName("a")) {
            val path = resolveHref(baseDir, a.getAttribute("href")) ?: continue
            val text = a.textContent.replace(Regex("\\s+"), " ").trim()
            if (text.isNotEmpty()) titles.putIfAbsent(path, text)
        }
        return titles.ifEmpty { null }
    }

    private fun ncxTitles(root: File, opf: Document, manifest: Map<String, Item>): Map<String, String>? {
        val tocId = opf.byName("spine").firstOrNull()?.getAttribute("toc")?.ifBlank { null }
        val ncx = (tocId?.let { manifest[it] } ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" })
            ?: return null
        val doc = readXml(File(root, ncx.href), relaxEntities = true)
        val baseDir = ncx.href.substringBeforeLast('/', "")
        val titles = linkedMapOf<String, String>()
        for (point in doc.byName("navPoint")) {
            // 嵌套 navPoint 时，按文档顺序第一个 text / content 就是它自己的
            val label = point.byName("text").firstOrNull()?.textContent?.trim().orEmpty()
            val src = point.byName("content").firstOrNull()?.getAttribute("src") ?: continue
            val path = resolveHref(baseDir, src) ?: continue
            if (label.isNotEmpty()) titles.putIfAbsent(path, label)
        }
        return titles.ifEmpty { null }
    }

    /**
     * 把 OPF/目录里的相对 href 解析成相对书根目录的路径。
     * 去掉 #锚点 和 ?查询，做百分号解码，处理 . 和 ..；想跳出书根目录、或是绝对地址时返回 null。
     */
    internal fun resolveHref(baseDir: String, href: String): String? {
        val clean = href.trim().substringBefore('#').substringBefore('?')
        if (clean.isEmpty() || clean.contains("://") || clean.startsWith("/") || clean.startsWith("data:")) return null
        val decoded = try {
            URLDecoder.decode(clean.replace("+", "%2B"), "UTF-8")
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (decoded.startsWith('/') || decoded.contains('\\') || decoded.contains('\u0000') ||
            Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(decoded)) return null
        val parts = ArrayDeque<String>()
        baseDir.split('/').filter { it.isNotEmpty() }.forEach { parts.addLast(it) }
        for (seg in decoded.split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) return null else parts.removeLast()
                else -> parts.addLast(seg)
            }
        }
        return parts.joinToString("/").ifEmpty { null }
    }

    private fun readXmlText(file: File): String {
        if (!file.isFile) throw EpubFormatException("EPUB 缺少文件：${file.name}")
        if (file.length() > MAX_XML_BYTES) throw EpubFormatException("XML 文件过大：${file.name}")
        val text = file.readBytes().toString(Charsets.UTF_8)
        if (text.contains("<!ENTITY")) throw EpubFormatException("XML 含实体声明，已拒绝：${file.name}")
        return text
    }

    private fun readXml(file: File, relaxEntities: Boolean = false): Document {
        var text = readXmlText(file)
        // 目录文件是 XHTML，常用 &nbsp; 这类 HTML 实体；不加载 DTD 时 XML 解析器不认识它们
        if (relaxEntities) text = text.replace("&nbsp;", "&#160;")

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // 不同平台的解析器支持的特性不一样（Android 会对不认识的特性抛异常），逐个尝试
            trySetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            trySetFeature("http://xml.org/sax/features/external-general-entities", false)
            trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return factory.newDocumentBuilder().apply {
            // Fail closed even on Android parsers that do not support all feature flags.
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
    }

    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        try {
            setFeature(name, value)
        } catch (e: Exception) {
            // 该平台不支持这个特性：忽略，靠上面的 <!ENTITY 检查兜底
        }
    }

    private fun Document.byName(local: String): List<Element> = getElementsByTagNameNS("*", local).toElements()
    private fun Element.byName(local: String): List<Element> = getElementsByTagNameNS("*", local).toElements()
    private fun NodeList.toElements(): List<Element> = (0 until length).mapNotNull { item(it) as? Element }
}

/** 渲染章节时，把主题样式注入出版方的 XHTML（纯字符串处理，可单元测试） */
object EpubInjector {

    private val HTML_TAG = Regex("<html\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val HEAD_CLOSE = Regex("</head\\s*>", RegexOption.IGNORE_CASE)
    private val DATA_THEME = Regex("""\sdata-theme\s*=\s*("[^"]*"|'[^']*')""", RegexOption.IGNORE_CASE)

    // 自闭合写法：XHTML 是严格 XML，<link> 不闭合会导致整章解析失败
    const val LINKS =
        """<link rel="stylesheet" type="text/css" href="/assets/shared/themes.css"/>""" +
            """<link rel="stylesheet" type="text/css" href="/assets/book/epub.css"/>"""

    fun inject(xhtml: String, theme: ReaderTheme): String {
        val htmlTag = HTML_TAG.find(xhtml) ?: return xhtml
        val attr = " data-theme=\"${theme.id}\""
        val newTag = if (DATA_THEME.containsMatchIn(htmlTag.value)) {
            DATA_THEME.replace(htmlTag.value, attr)
        } else {
            // 保留原标签名的大小写：XHTML 是 XML，开闭标签大小写必须一致
            htmlTag.value.substring(0, 5) + attr + htmlTag.value.substring(5)
        }
        var out = xhtml.replaceRange(htmlTag.range, newTag)
        val headClose = HEAD_CLOSE.find(out)
        out = if (headClose != null) {
            out.replaceRange(headClose.range.first, headClose.range.first, LINKS) // 插在出版方 CSS 之后，优先级更高
        } else {
            val afterHtml = htmlTag.range.first + newTag.length
            out.replaceRange(afterHtml, afterHtml, "<head>$LINKS</head>")
        }
        return out
    }
}
