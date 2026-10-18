package com.example.yhviewer

import java.io.IOException

/*
 * v10：多选导入和文件夹导入的规划（纯逻辑，JVM 可测）。
 *
 * 单个文件导入时，系统文件选择器只给我们这一个文件的读取权限，拿不到同文件夹的 assets/、图片、CSS，
 * 所以 HTML 里的相对路径资源一定加载不出来（用户测试包里的繁体 HTML 就是这样）。
 * 文件夹导入拿到的是整棵目录树的读取权限，可以把资源一起带进来。
 *
 * 导入一个文件夹时，每个文件归入四类之一：
 *   BUNDLE_DOC  HTML / Markdown：和资源一起放进同一个"文件夹副本"（bundle），相对路径照常可用
 *   RESOURCE    CSS、脚本、图片、字体、音视频，以及资源目录（assets/、images/、xxx_files/ …）里的所有文件：
 *               只进 bundle，不在书架上单独出现
 *   STANDALONE  TXT、EPUB、代码、数据、SVG、日志：和单文件导入一样，各自成为书架上的一项
 *   SKIP        不支持的格式（zip、pdf、docx …）或超过限制：记进导入报告
 */

data class FolderEntry(val relPath: String, val size: Long, val skipReason: String? = null)

enum class EntryRole { BUNDLE_DOC, RESOURCE, STANDALONE, SKIP }

data class PlannedEntry(val entry: FolderEntry, val role: EntryRole, val reason: String? = null)

data class FolderPlan(val entries: List<PlannedEntry>) {
    val bundleDocs get() = entries.filter { it.role == EntryRole.BUNDLE_DOC }
    val resources get() = entries.filter { it.role == EntryRole.RESOURCE }
    val standalone get() = entries.filter { it.role == EntryRole.STANDALONE }
    val skipped get() = entries.filter { it.role == EntryRole.SKIP }

    /** 需要复制进 bundle 的文件：有 HTML / Markdown 时才需要 bundle */
    val bundleFiles get() = if (bundleDocs.isEmpty()) emptyList() else bundleDocs + resources
    val bundleBytes get() = bundleFiles.sumOf { it.entry.size }
}

object FolderImportPlanner {

    data class Limits(
        val maxEntries: Int = 3_000,          // 遍历到的文件总数
        val maxDepth: Int = 8,
        val maxBundleFiles: Int = 2_000,
        val maxBundleBytes: Long = 200L * 1024 * 1024,
        val maxFileBytes: Long = DocumentLoader.MAX_TEXT_BYTES, // 单个资源 / 文档
    )

    private val BUNDLE_DOC_EXT = setOf("html", "htm", "xhtml", "md", "markdown", "mdown")

    /** 只作为资源存在的格式：书架不显示图片、字体、音视频 */
    private val RESOURCE_ONLY_EXT = setOf(
        "css", "map",
        "png", "jpg", "jpeg", "gif", "webp", "avif", "bmp", "ico",
        "woff", "woff2", "ttf", "otf", "eot",
        "mp3", "m4a", "aac", "ogg", "oga", "wav", "flac", "mp4", "m4v", "webm", "ogv",
    )

    /** 网页常见的资源目录名：里面的文件一律当资源，包括 .js / .json / .svg */
    private val RESOURCE_DIRS = setOf(
        "assets", "asset", "static", "public", "css", "styles", "style", "js", "scripts", "script",
        "img", "imgs", "image", "images", "pic", "pics", "fonts", "font", "media", "res", "resources", "vendor", "lib",
    )

    /** 文件夹导入时可以单独上书架的扩展名（除了 DocTypes 能识别的类型） */
    private val EXTRA_STANDALONE_EXT = setOf("svg", "log", "csv", "tsv")

    fun plan(entries: List<FolderEntry>, limits: Limits = Limits()): FolderPlan {
        val planned = entries.take(limits.maxEntries).map { classify(it, limits) }.toMutableList()
        entries.drop(limits.maxEntries).forEach { planned += PlannedEntry(it, EntryRole.SKIP, "文件夹内文件超过 ${limits.maxEntries} 个") }

        // bundle 超出数量或总大小：多出来的资源跳过（文档优先保留），仍超出就跳过多余的文档
        val plan = FolderPlan(planned)
        if (plan.bundleDocs.isEmpty()) return plan
        var files = 0
        var bytes = 0L
        val result = planned.map { p ->
            if (p.role != EntryRole.BUNDLE_DOC) p
            else if (files >= limits.maxBundleFiles || p.entry.size > limits.maxBundleBytes - bytes) {
                p.copy(role = EntryRole.SKIP, reason = "文件夹副本超过限制")
            } else { files++; bytes += p.entry.size; p }
        }.map { p ->
            if (p.role != EntryRole.RESOURCE) p
            else if (files >= limits.maxBundleFiles || p.entry.size > limits.maxBundleBytes - bytes) {
                p.copy(role = EntryRole.SKIP, reason = "文件夹副本超过限制")
            } else { files++; bytes += p.entry.size; p }
        }
        return FolderPlan(result)
    }

    internal fun classify(e: FolderEntry, limits: Limits): PlannedEntry {
        e.skipReason?.let { return PlannedEntry(e, EntryRole.SKIP, it) }
        val segments = e.relPath.split('/')
        if (segments.any { safeName(it) == null } || e.size < 0) return PlannedEntry(e, EntryRole.SKIP, "非法文件名或大小")
        val name = segments.last()
        val ext = name.substringAfterLast('.', "").lowercase()
        if (segments.size - 1 > limits.maxDepth) return PlannedEntry(e, EntryRole.SKIP, "目录层级太深")

        val inResourceDir = segments.dropLast(1).any { seg ->
            val s = seg.lowercase()
            s in RESOURCE_DIRS || s.endsWith("_files") || s.endsWith(".files")
        }
        if (e.size > limits.maxFileBytes && (inResourceDir || ext !in setOf("txt", "epub"))) {
            return PlannedEntry(e, EntryRole.SKIP, "文件太大")
        }
        return when {
            ext in BUNDLE_DOC_EXT && !inResourceDir -> PlannedEntry(e, EntryRole.BUNDLE_DOC)
            inResourceDir || ext in RESOURCE_ONLY_EXT -> PlannedEntry(e, EntryRole.RESOURCE)
            ext == "txt" && e.size > DocumentLoader.MAX_TXT_BYTES -> PlannedEntry(e, EntryRole.SKIP, "文件太大")
            ext == "epub" && e.size > DocumentLoader.MAX_EPUB_BYTES -> PlannedEntry(e, EntryRole.SKIP, "文件太大")
            DocTypes.detect(name, null) != DocType.UNKNOWN || ext in EXTRA_STANDALONE_EXT -> PlannedEntry(e, EntryRole.STANDALONE)
            else -> PlannedEntry(e, EntryRole.SKIP, "不支持的格式")
        }
    }

    /**
     * 目录树里的名字是不可信输入（来自其他 App 的 DocumentsProvider）。
     * 只接受普通文件名：不能为空，不能是 . / ..，不能含路径分隔符或 NUL。
     */
    fun safeName(name: String?): String? {
        if (name == null || name.length > 255 || name.toByteArray(Charsets.UTF_8).size > 255) return null
        val n = name.trim()
        if (n.isEmpty() || n == "." || n == ".." || '/' in n || '\\' in n || '\u0000' in n) return null
        return n
    }

    /** 隐藏文件和 macOS 打包残留（__MACOSX、._xxx、.DS_Store）不导入 */
    fun ignored(name: String): Boolean = name.startsWith(".") || name == "__MACOSX" || name == "Thumbs.db"
}

/**
 * bundle 里的文档在数据库里的 localFile 写成 "<bundle 目录>/<相对路径>"，普通副本只有一个文件名。
 * 这样不需要新增数据库列：有没有 "/" 就能区分。
 */
object BundlePaths {
    fun isBundle(localFile: String) = '/' in localFile

    /** shelf/ 下的顶层目录名（bundle）或文件名（普通副本） */
    fun topLevel(localFile: String) = localFile.substringBefore('/')

    fun relPath(localFile: String) = localFile.substringAfter('/', "")

    fun localFile(bundle: String, relPath: String): String {
        if (FolderImportPlanner.safeName(bundle) != bundle || relPath.isEmpty() || relPath.split('/').any { FolderImportPlanner.safeName(it) != it }) {
            throw IOException("非法路径：$relPath")
        }
        return "$bundle/$relPath"
    }

    /** 同一个文件夹再次导入时用的来源键：文件夹 Uri + 相对路径 */
    fun sourceKey(treeUri: String, relPath: String) = "$treeUri#$relPath"
}

/** 一次导入（多选或文件夹）的结果，显示在书架上 */
data class ImportReport(
    val imported: Int,
    val failed: List<Pair<String, String>>,    // 文件名 → 原因
    val skipped: List<Pair<String, String>>,   // 文件名 → 原因（不支持、太大）
    val resources: Int = 0,                    // 随 HTML / Markdown 一起带进来的资源文件数
) {
    val total get() = imported + failed.size + skipped.size
}

/** 导入进度（书架底部显示） */
data class ImportProgress(val done: Int, val total: Int, val current: String)
