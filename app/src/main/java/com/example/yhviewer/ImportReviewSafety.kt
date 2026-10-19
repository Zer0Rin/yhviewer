package com.example.yhviewer

/** Bounds provider input before retaining paths or encoding document URIs. */
internal class ProviderMetadataBudget(private val maxChars: Int = 512 * 1024) {
    var retainedChars = 0
        private set
    fun accept(id: String?, path: String): Boolean {
        if (!validId(id) || path.length > 2048) return false
        // Conservatively include URI escaping and copies in traversal collections.
        val cost = id!!.length * 12 + path.length * 4
        if (cost > maxChars - retainedChars) return false
        retainedChars += cost
        return true
    }
    companion object {
        fun validId(id: String?) = id != null && id.isNotEmpty() && id.length <= 4096
    }
}

/** Only compact commit metadata survives the next document decode. */
internal data class StagedBundleDocument(
    val relPath: String, val name: String, val type: DocType, val size: Long, val kind: ContentKind,
) {
    companion object {
        fun from(relPath: String, size: Long, text: String): StagedBundleDocument {
            val name = relPath.substringAfterLast('/')
            val type = DocTypes.detect(name, null)
            return StagedBundleDocument(relPath, name, type, size, ContentClassifier.classify(name, type, size, text))
        }
    }
}

/** Visible heading and trimmed nonempty paragraphs in assets/book/txt.html. */
internal object TxtSearchText {
    private val edges = Regex("^[\\s\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]+|[\\s\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]+$")
    private val lineBreak = Regex("\\r?\\n")
    /** v11：连续滚动的章节页（TxtChapterPage）用同一套分段规则，保证搜索序号和页面一致 */
    fun paragraphs(text: String): List<String> = text.split(lineBreak).map { it.replace(edges, "") }.filter { it.isNotEmpty() }
    fun rendered(title: String, text: String): String = (listOf(title) + paragraphs(text)).joinToString("\n")
}

/** At most one queued UI callback, bounded representatives and one overflow marker. */
internal class MissingResourceAccumulator(private val capacity: Int = 8, private val maxPathChars: Int = 160) {
    companion object { const val OVERFLOW = "更多资源未找到…" }
    private val paths = linkedSetOf<String>()
    private var overflow = false
    private var pending = false
    @Synchronized fun add(path: String): Boolean {
        val sample = path.take(maxPathChars)
        val changed = if (sample in paths) false else if (paths.size < capacity) paths.add(sample)
            else if (!overflow) { overflow = true; true } else false
        if (!changed || pending) return false
        pending = true
        return true
    }
    @Synchronized fun drainNotification(): List<String> {
        pending = false
        return paths.toList() + if (overflow) listOf(OVERFLOW) else emptyList()
    }
}
