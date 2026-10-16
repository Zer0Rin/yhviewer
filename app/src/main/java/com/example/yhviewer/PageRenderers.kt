package com.example.yhviewer

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/** assets 里的页面模板，读一次后缓存 */
object Templates {
    const val CODE = "code/template.html"
    const val TXT = "book/txt.html"

    private val cache = ConcurrentHashMap<String, String>()

    fun get(context: Context, path: String): String =
        cache.getOrPut(path) {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
}

/*
 * 和 MarkdownRenderer 同一个套路：Kotlin 只做字面量替换。
 *  - 先替换主题占位符，再塞入用户内容（用户内容里恰好有 "__THEME__" 也不会被改）
 *  - 用户内容统一走 MarkdownRenderer.toJsonString：所有 "<" 编码成 \u003c，无法闭合 <script>
 * 渲染函数本身不碰 Android API，可在 JVM 单元测试里测。
 */
const val PAYLOAD_PLACEHOLDER = "/*PAYLOAD*/"

object CodeRenderer {
    fun render(text: String, lang: String, wrap: Boolean, template: String, theme: ReaderTheme): String {
        require(PAYLOAD_PLACEHOLDER in template) { "template is missing $PAYLOAD_PLACEHOLDER" }
        val j = MarkdownRenderer::toJsonString
        val payload = """{"text":${j(text)},"lang":${j(lang)},"wrap":$wrap}"""
        return template
            .replace(MarkdownRenderer.THEME_PLACEHOLDER, theme.id)
            .replace(PAYLOAD_PLACEHOLDER, payload)
    }
}

object TxtRenderer {
    fun render(chapter: TxtChapter, index: Int, count: Int, template: String, theme: ReaderTheme, reading: ReadingPrefs = ReadingPrefs()): String {
        require(PAYLOAD_PLACEHOLDER in template) { "template is missing $PAYLOAD_PLACEHOLDER" }
        val j = MarkdownRenderer::toJsonString
        val payload = """{"reading":${j(reading.toCss())},"title":${j(chapter.title)},"text":${j(chapter.text)},"position":${j("${index + 1} / $count")}}"""
        return template
            .replace(MarkdownRenderer.THEME_PLACEHOLDER, theme.id)
            .replace(PAYLOAD_PLACEHOLDER, payload)
    }
}
