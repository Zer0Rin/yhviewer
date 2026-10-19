package com.example.yhviewer

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * v11：TXT 章节索引。
 *
 * 以前每次打开都把整本书读进内存、全文跑正则切章、再把每章复制一份，几千万字的小说要十几秒，
 * 内存里同时有三四份全文。现在：
 *  - 导入时（老书第一次打开时）切一次章，把每章正文在 UTF-8 副本里的**字节范围**写进索引文件
 *  - 打开时只读索引（几千行），正文按需从副本里按字节范围读一章
 *  - 索引头记录副本大小和修改时间，对不上就重建
 *
 * 纯 JVM，可单元测试。
 */
object TxtIndex {
    const val VERSION = 2
    // Private metadata is bounded independently of the source (32 MiB import cap).
    internal const val MAX_INDEX_BYTES = 8 * 1024 * 1024
    private const val MAGIC = "YHTXT"

    /** 一章：标题 + 正文在副本里的字节范围 [start, end) */
    data class Entry(val title: String, val start: Long, val end: Long)

    /**
     * raw = 副本按 UTF-8 解码后的原文（可能含 \r\n）。
     * 切章在规范化文本上做（和 TxtChapters.split 完全一致），再把边界换算成原文字节偏移。
     */
    fun build(raw: String): List<Entry> {
        val text = TxtChapters.normalize(raw)
        val ranges = TxtChapters.ranges(text)
        val positions = IntArray(ranges.size * 2).also { p ->
            ranges.forEachIndexed { i, r -> p[2 * i] = r.start; p[2 * i + 1] = r.end }
        }
        val order = positions.indices.sortedBy { positions[it] }
        val sorted = IntArray(positions.size) { positions[order[it]] }
        val bytes = byteOffsets(raw, sorted)
        val mapped = LongArray(positions.size)
        order.forEachIndexed { k, original -> mapped[original] = bytes[k] }
        return ranges.mapIndexed { i, r -> Entry(r.title, mapped[2 * i], mapped[2 * i + 1]) }
    }

    /**
     * 规范化文本里的位置（升序）→ 原文 UTF-8 字节偏移。
     * 规范化只把 "\r\n" 和单独的 "\r" 换成 "\n"，所以一遍扫描就能对上。
     */
    internal fun byteOffsets(raw: String, sortedNormPositions: IntArray): LongArray {
        val out = LongArray(sortedNormPositions.size)
        var r = 0
        var n = 0
        var b = 0L
        var k = 0
        while (k < out.size) {
            while (k < out.size && sortedNormPositions[k] <= n) out[k++] = b
            if (k >= out.size || r >= raw.length) break
            val c = raw[r]
            when {
                c == '\r' && r + 1 < raw.length && raw[r + 1] == '\n' -> { r += 2; b += 2; n += 1 }
                c.isHighSurrogate() && r + 1 < raw.length && raw[r + 1].isLowSurrogate() -> { r += 2; b += 4; n += 2 }
                else -> { r += 1; b += utf8Length(c); n += 1 }
            }
        }
        while (k < out.size) out[k++] = b
        return out
    }

    private fun utf8Length(c: Char): Int = when {
        c.code < 0x80 -> 1
        c.code < 0x800 -> 2
        Character.isSurrogate(c) -> 1 // 单独的代理项：String.toByteArray 写成 '?'
        else -> 3
    }

    // ---------------- 索引文件 ----------------

    fun encode(entries: List<Entry>, fileSize: Long, fileMtime: Long): String = buildString {
        append(MAGIC).append('\t').append(VERSION).append('\t').append(fileSize).append('\t').append(fileMtime).append('\n')
        entries.forEach { e ->
            append(e.start).append('\t').append(e.end).append('\t')
            append(Base64.getEncoder().encodeToString(e.title.toByteArray(Charsets.UTF_8))).append('\n')
        }
    }

    /** 版本、大小、修改时间任一对不上，或格式损坏 → null（调用方重建） */
    fun decode(text: String, fileSize: Long, fileMtime: Long): List<Entry>? {
        if (text.length > MAX_INDEX_BYTES || fileSize < 0) return null
        val lines = text.split('\n')
        val head = lines.firstOrNull()?.split('\t') ?: return null
        if (head.size != 4 || head[0] != MAGIC || head[1] != VERSION.toString()) return null
        if (head[2] != fileSize.toString() || head[3] != fileMtime.toString()) return null
        val out = ArrayList<Entry>(lines.size)
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isEmpty()) continue
            val parts = line.split('\t', limit = 3)
            if (parts.size != 3) return null
            val start = parts[0].toLongOrNull() ?: return null
            val end = parts[1].toLongOrNull() ?: return null
            if (start < 0 || end < start || end > fileSize || end - start > Int.MAX_VALUE ||
                (out.isNotEmpty() && (start < out.last().end || start <= out.last().start))) return null
            val title = runCatching { strictUtf8(Base64.getDecoder().decode(parts[2])) }.getOrNull() ?: return null
            out += Entry(title, start, end)
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** 严格 UTF-8 解码；副本有坏字节时返回 null（字节偏移会对不上，改走内存模式） */
    fun strictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }
}

/**
 * 打开的一本 TXT。章节正文按需读取，最近读过的几章缓存在内存里。
 * 线程安全：WebView 的资源回调在后台线程里读章节。
 */
class TxtBook private constructor(
    private val file: File?,
    val entries: List<TxtIndex.Entry>,
    /** 内存模式（副本不是合法 UTF-8 时）：直接持有全部章节 */
    private val inMemory: List<TxtChapter>?,
) {
    val size: Int get() = entries.size
    val titles: List<String> = entries.map { it.title }

    private val cache = object : LinkedHashMap<Int, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?) = this.size > CACHE_CHAPTERS
    }

    fun chapter(index: Int): TxtChapter = TxtChapter(entries[index].title, text(index))

    fun text(index: Int): String {
        inMemory?.let { return it[index].text }
        synchronized(cache) { cache[index]?.let { return it } }
        val e = entries[index]
        val bytes = ByteArray((e.end - e.start).toInt())
        RandomAccessFile(file!!, "r").use { f ->
            f.seek(e.start)
            f.readFully(bytes)
        }
        val text = TxtChapters.normalize(String(bytes, Charsets.UTF_8)).trim('\n')
        synchronized(cache) { cache[index] = text }
        return text
    }

    /** 全书搜索用：一次读完整个副本再切，比逐章随机读快 */
    fun allTexts(): List<String> {
        inMemory?.let { list -> return list.map { it.text } }
        val bytes = file!!.readBytes()
        return entries.map { e ->
            TxtChapters.normalize(String(bytes, e.start.toInt(), (e.end - e.start).toInt(), Charsets.UTF_8)).trim('\n')
        }
    }

    companion object {
        private const val CACHE_CHAPTERS = 8

        /** 读索引；没有或过期就切章并写索引。indexFile 写失败不影响打开 */
        fun open(file: File, indexFile: File): TxtBook {
            if (!file.exists()) throw IOException("本地副本丢失，请从原文件重新打开")
            val size = file.length()
            val mtime = file.lastModified()
            runCatching { indexFile.takeIf { it.isFile && it.length() <= TxtIndex.MAX_INDEX_BYTES }?.inputStream()?.use {
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (out.size() <= TxtIndex.MAX_INDEX_BYTES) {
                    val read = it.read(buffer, 0, minOf(buffer.size, TxtIndex.MAX_INDEX_BYTES + 1 - out.size()))
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
                if (out.size() > TxtIndex.MAX_INDEX_BYTES) null else TxtIndex.strictUtf8(out.toByteArray())
            } }.getOrNull()
                ?.let { TxtIndex.decode(it, size, mtime) }
                ?.let { return TxtBook(file, it, null) }
            return build(file, file.readBytes(), indexFile)
        }

        /** 导入时用：已经有字节在手，直接建索引 */
        fun build(file: File, bytes: ByteArray, indexFile: File): TxtBook {
            val raw = TxtIndex.strictUtf8(bytes)
                ?: return fromText(String(bytes, Charsets.UTF_8))
            val entries = TxtIndex.build(raw)
            runCatching {
                indexFile.parentFile?.mkdirs()
                val tmp = File(indexFile.path + ".tmp")
                tmp.writeText(TxtIndex.encode(entries, file.length(), file.lastModified()), Charsets.UTF_8)
                if (!tmp.renameTo(indexFile)) tmp.delete()
            }
            return TxtBook(file, entries, null)
        }

        /** 内存模式，也给测试用 */
        fun fromText(raw: String): TxtBook {
            val chapters = TxtChapters.split(raw)
            return TxtBook(null, chapters.map { TxtIndex.Entry(it.title, 0, 0) }, chapters)
        }
    }
}
