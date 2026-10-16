package com.example.yhviewer

/** Decode the string/boolean JSON values returned by WebView.evaluateJavascript. */
object JsonStrings {
    fun decodeStringOrNull(json: String?): String? {
        val s = json?.trim(' ', '\t', '\r', '\n') ?: return null
        if (s.isEmpty() || s == "null") return null
        require(s.length >= 2 && s.first() == '"' && s.last() == '"') { "Expected a JSON string" }
        val end = s.lastIndex
        val out = StringBuilder(end)
        var i = 1
        while (i < end) {
            val c = s[i++]
            if (c != '\\') {
                require(c != '"' && c.code >= 0x20) { "Unescaped JSON string character" }
                out.append(c)
                continue
            }
            require(i < end) { "Dangling JSON escape" }
            when (val escape = s[i++]) {
                '"', '\\', '/' -> out.append(escape)
                'b' -> out.append('\b')
                'f' -> out.append('\u000c')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    require(i + 4 <= end) { "Truncated Unicode escape" }
                    val digits = s.substring(i, i + 4)
                    require(digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "Invalid Unicode escape" }
                    out.append(digits.toInt(16).toChar())
                    i += 4
                }
                else -> throw IllegalArgumentException("Invalid JSON escape")
            }
        }
        return out.toString()
    }

    fun decodeBoolean(json: String?): Boolean = json?.trim() == "true"
}
