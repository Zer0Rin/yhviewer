package com.example.yhviewer

/*
 * 按文件内容自动分类（书架上的"类型"筛选）。
 *
 * 和 DocType 的区别：DocType 决定**用哪个阅读器打开**，主要看扩展名，导入后不变；
 * ContentKind 只用于书架归类，会**看内容**：
 *   - 没有扩展名、或扩展名不对的文件（"notes" 其实是 Markdown，"data.txt" 其实是 JSON）
 *   - 代码里再分出"数据"（JSON / CSV / YAML…），TXT 里分出"小说"和"短文本"
 *
 * 只读文件开头 SAMPLE_CHARS 个字符，纯字符串处理，JVM 可测。
 */

enum class ContentKind(val id: String, val label: String) {
    WEB("web", "网页"),
    NOTE("note", "笔记"),
    CODE("code", "代码"),
    DATA("data", "数据"),
    NOVEL("novel", "小说"),
    EBOOK("ebook", "电子书"),
    TEXT("text", "纯文本"),
    IMAGE("image", "图片"),   // v10：SVG 矢量图，以图片方式显示
    ;

    companion object {
        /** 数据库里存 id；读到 null（还没分类）或未知值时返回 null，由启动时的补分类处理 */
        fun fromId(id: String?): ContentKind? = entries.firstOrNull { it.id == id }
    }
}

object ContentClassifier {

    const val SAMPLE_CHARS = 64 * 1024

    private val DATA_EXT = setOf("json", "jsonl", "ndjson", "geojson", "csv", "tsv", "yaml", "yml", "toml", "ini", "xml", "plist")
    private val NOTE_EXT = setOf("md", "markdown", "mdx", "rst", "org", "adoc")

    private val HTML_START = Regex("""^(<!doctype\s+html|<html[\s>])""", RegexOption.IGNORE_CASE)
    private val CHAPTER = Regex("""^\s*(第\s*[0-9０-９零〇一二两三四五六七八九十百千]+\s*[章节回卷集部篇]|chapter\s+\d+)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val MD_HEADING = Regex("""^#{1,6}\s+\S""", RegexOption.MULTILINE)
    private val MD_FENCE = Regex("""^\s*```""", RegexOption.MULTILINE)
    private val MD_LIST = Regex("""^\s*([-*+]|\d+\.)\s+(\[[ xX]]\s+)?\S""", RegexOption.MULTILINE)
    private val MD_LINK = Regex("""\[[^\]\n]+]\([^)\s]+\)""")

    /** TXT 超过这个大小就算长篇（不管有没有识别出章节） */
    const val NOVEL_MIN_BYTES = 100_000L

    fun classify(name: String, type: DocType, sizeBytes: Long, sample: String): ContentKind {
        if (type == DocType.EPUB) return ContentKind.EBOOK
        val ext = name.substringAfterLast('.', "").lowercase()
        val head = sample.take(SAMPLE_CHARS)

        // v10：SVG 是图片；日志是纯文本（v7 里 .log 被 CodeLanguages 识别成代码类型，归到了"代码"）
        if (ext == "svg") return ContentKind.IMAGE
        if (ext == "log" || ext == "out") return ContentKind.TEXT

        // 1. 内容本身是网页：不管扩展名（.txt 里存的整页 HTML 也算）
        if (type == DocType.HTML || hasHtmlPrefix(head)) return ContentKind.WEB

        // 2. 按扩展名能确定的：数据格式、笔记、代码。代码文件不做内容嗅探（几行 foo(a, b) 会被误认成 CSV）
        if (ext in DATA_EXT) return ContentKind.DATA
        if (type == DocType.MARKDOWN || ext in NOTE_EXT) return ContentKind.NOTE
        if (type == DocType.CODE) return ContentKind.CODE

        // 3. TXT / 未知类型：看内容
        if (looksLikeJson(head) || looksLikeCsv(head)) return ContentKind.DATA
        if (markdownScore(head) >= 3) return ContentKind.NOTE
        val chapters = CHAPTER.findAll(head).take(3).count()
        if (sizeBytes >= NOVEL_MIN_BYTES || chapters >= 2) return ContentKind.NOVEL
        return ContentKind.TEXT
    }

    /** Skip leading comments without recursive regex repetition on untrusted file content. */
    private fun hasHtmlPrefix(head: String): Boolean {
        var offset = 0
        while (offset < head.length) {
            while (offset < head.length && head[offset] in " \t\n\r\u000B\u000C") offset++
            if (!head.startsWith("<!--", offset)) break
            val end = head.indexOf("-->", offset + 4)
            if (end == -1) return false
            offset = end + 3
        }
        return HTML_START.containsMatchIn(head.substring(offset))
    }

    /** 首尾字符成对（{…} 或 […]），且开头像 JSON 的键或值。只看样本，不做完整解析 */
    internal fun looksLikeJson(s: String): Boolean {
        val t = s.trim()
        if (t.length < 2) return false
        val first = t.first()
        if (first != '{' && first != '[') return false
        val afterOpen = t.drop(1).trimStart()
        val plausibleStart = afterOpen.isEmpty() || afterOpen.first() in "\"{[]}-0123456789tfn"
        if (!plausibleStart) return false
        // 样本可能被截断：只有完整文件（样本没截满）才要求结尾闭合
        return s.length >= SAMPLE_CHARS || t.last() == (if (first == '{') '}' else ']')
    }

    /** 至少 3 行，每行的分隔符（逗号或制表符）数量一致且 ≥ 1 */
    internal fun looksLikeCsv(s: String): Boolean {
        val lines = s.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.take(20).toList()
            .let { if (s.length >= SAMPLE_CHARS) it.dropLast(1) else it } // 截断的最后一行不算
        if (lines.size < 3) return false
        return listOf(',', '\t').any { sep ->
            val counts = lines.map { line -> line.count { it == sep } }
            counts.first() >= 1 && counts.all { it == counts.first() }
        }
    }

    /** Markdown 标记种类数：标题、代码围栏、列表、链接各算 1 分，数量多的再加分 */
    internal fun markdownScore(s: String): Int {
        val headings = MD_HEADING.findAll(s).take(5).count()
        val fences = MD_FENCE.findAll(s).take(2).count()
        val lists = MD_LIST.findAll(s).take(5).count()
        val links = MD_LINK.findAll(s).take(3).count()
        var score = 0
        if (headings >= 1) score++
        if (headings >= 3) score++
        if (fences >= 2) score++
        if (lists >= 3) score++
        if (links >= 2) score++
        return score
    }
}
