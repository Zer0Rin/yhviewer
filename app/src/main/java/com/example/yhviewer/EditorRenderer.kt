package com.example.yhviewer

/** Source is JSON data for the bundled editor, never executable document HTML. */
object EditorRenderer {
    const val TEMPLATE = "edit/editor.html"
    const val MAX_EDIT_CHARS = 1_000_000
    val CSP = "default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'none'; frame-src 'none'; worker-src 'none'; form-action 'none'; base-uri 'none'; object-src 'none'"

    fun canEdit(text: String) = text.length <= MAX_EDIT_CHARS
    fun langFor(doc: LoadedDoc): String = when (doc.type) {
        DocType.HTML -> "html"
        DocType.MARKDOWN -> "markdown"
        else -> CodeLanguages.forFileName(doc.name) ?: "plaintext"
    }

    fun render(text: String, lang: String, wrap: Boolean, template: String, theme: ReaderTheme): String {
        require(canEdit(text)) { "文档超过 100 万字符，无法编辑" }
        require(PAYLOAD_PLACEHOLDER in template) { "Missing editor payload placeholder" }
        val json = MarkdownRenderer::toJsonString
        val payload = """{"text":${json(text)},"lang":${json(lang)},"wrap":$wrap}"""
        return template.replace(MarkdownRenderer.THEME_PLACEHOLDER, theme.id).replace(PAYLOAD_PLACEHOLDER, payload)
    }
}
