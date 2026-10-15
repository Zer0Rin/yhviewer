package com.example.yhviewer

/**
 * 文件名 → highlight.js 语言 id。只列 highlight.js "common" 包里有的语言；
 * 没有对应语法的类型（日志、CSV 等）映射到 plaintext：依然按代码方式显示（等宽、行号），只是不着色。
 * 纯 Kotlin，可在 JVM 单元测试里直接测。
 */
object CodeLanguages {

    /** 没有扩展名、靠文件名识别的特殊文件（先于扩展名匹配） */
    private val byFileName = mapOf(
        "makefile" to "makefile",
        "gnumakefile" to "makefile",
        "dockerfile" to "bash",
        "cmakelists.txt" to "plaintext",
        ".gitignore" to "plaintext",
        ".gitattributes" to "plaintext",
        ".editorconfig" to "ini",
        ".env" to "ini",
    )

    private val byExtension = mapOf(
        "kt" to "kotlin", "kts" to "kotlin",
        "java" to "java", "gradle" to "java",          // Groovy 没在 common 包里，Java 语法最接近
        "py" to "python", "pyw" to "python",
        "js" to "javascript", "mjs" to "javascript", "cjs" to "javascript", "jsx" to "javascript",
        "ts" to "typescript", "tsx" to "typescript", "mts" to "typescript", "cts" to "typescript",
        "go" to "go",
        "rs" to "rust",
        "c" to "c", "h" to "c",
        "cpp" to "cpp", "cc" to "cpp", "cxx" to "cpp", "hpp" to "cpp", "hh" to "cpp", "hxx" to "cpp",
        "m" to "objectivec", "mm" to "objectivec",
        "cs" to "csharp",
        "swift" to "swift",
        "rb" to "ruby",
        "php" to "php",
        "pl" to "perl", "pm" to "perl",
        "lua" to "lua",
        "r" to "r",
        "sh" to "bash", "bash" to "bash", "zsh" to "bash",
        "sql" to "sql",
        "json" to "json", "jsonc" to "json", "jsonl" to "json", "har" to "json",
        "yaml" to "yaml", "yml" to "yaml",
        "toml" to "ini", "ini" to "ini", "cfg" to "ini", "conf" to "ini", "properties" to "ini",
        "xml" to "xml", "xsd" to "xml", "plist" to "xml", "vue" to "xml", "svelte" to "xml",
        "css" to "css", "scss" to "scss", "less" to "less",
        "diff" to "diff", "patch" to "diff",
        "mk" to "makefile",
        "graphql" to "graphql", "gql" to "graphql",
        "log" to "plaintext", "csv" to "plaintext", "tsv" to "plaintext",
    )

    /** 返回语言 id；不是代码文件时返回 null（.html / .md / .txt 交给各自的阅读器） */
    fun forFileName(name: String): String? {
        val lower = name.trim().lowercase()
        byFileName[lower]?.let { return it }
        val ext = lower.substringAfterLast('.', missingDelimiterValue = "")
        if (ext.isEmpty()) return null
        return byExtension[ext]
    }

    /** 书架角标用：KT、PY、JSON……最多 4 个字符 */
    fun badge(name: String): String {
        val lower = name.trim().lowercase()
        if (lower in byFileName) return lower.trimStart('.').take(4).uppercase()
        return lower.substringAfterLast('.', "").take(4).uppercase().ifEmpty { "CODE" }
    }
}
