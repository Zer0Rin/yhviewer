package com.example.yhviewer

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 文本编码识别。中文 TXT 小说大量是 GBK，按 UTF-8 读会整篇乱码。
 *
 * 策略：
 *  1. 有 BOM：直接按 BOM 判断（UTF-8 / UTF-16LE / UTF-16BE）
 *  2. 严格按 UTF-8 解码成功、而且不是"大量 NUL"（没有 BOM 的 UTF-16 英文文本恰好也是合法 UTF-8）→ UTF-8
 *  3. 否则在 GB18030、Big5、UTF-16LE、UTF-16BE 里，取开头一段（256 KB）严格解码，按"像不像正常文本"打分，
 *     选分最高的再完整解码；完整解码失败就换下一个
 *  4. 都失败：按 UTF-8 宽松解码（非法字节变成 �），至少能打开
 *
 * v1.0 修正：以前第 3 步是"GB18030 能解就用 GB18030"，但 GB18030 几乎能"成功"解码任何字节：
 * Big5 繁体和没有 BOM 的 UTF-16 都会被解成乱码（真机验收发现）。打分看常用汉字、中文标点、ASCII 的比例：
 * Big5 被当成 GB18030 时，常用字的首字节会落到 GB 的日文假名、希腊字母、俄文字母区；UTF-16 被当成
 * 单字节编码时会出现大量控制字符。纯 JVM 实现，可单元测试。
 */
object CharsetDetector {

    data class Result(val text: String, val charset: Charset)

    private val GB18030: Charset = Charset.forName("GB18030")
    private val BIG5: Charset = Charset.forName("Big5")
    private const val SAMPLE_BYTES = 256 * 1024

    fun decode(bytes: ByteArray): Result {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Result(String(bytes, 3, bytes.size - 3, Charsets.UTF_8), Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Result(String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE), Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Result(String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE), Charsets.UTF_16BE)
        }
        strictDecode(bytes, Charsets.UTF_8)?.let { if (!mostlyNul(it)) return Result(it, Charsets.UTF_8) }

        val candidates = buildList {
            add(GB18030)
            add(BIG5)
            if (bytes.size % 2 == 0) { add(Charsets.UTF_16LE); add(Charsets.UTF_16BE) }
        }
        val ranked = candidates
            .mapNotNull { cs -> sampleDecode(bytes, cs)?.let { cs to TextScore.of(it) } }
            .sortedByDescending { it.second } // 稳定排序：同分时保留上面的顺序（GB18030 优先）
        for ((cs, _) in ranked) {
            strictDecode(bytes, cs)?.let { return Result(it, cs) }
        }
        return Result(String(bytes, Charsets.UTF_8), Charsets.UTF_8)
    }

    /** 超过 1% 是 NUL：多半是没有 BOM 的 UTF-16，不当 UTF-8 用 */
    private fun mostlyNul(text: String): Boolean {
        if (text.isEmpty()) return false
        val n = minOf(text.length, SAMPLE_BYTES)
        var nul = 0
        for (i in 0 until n) if (text[i] == '\u0000') nul++
        return nul * 100 > n
    }

    /** 严格解码开头一段；末尾被截断的半个字符不算错误 */
    private fun sampleDecode(bytes: ByteArray, cs: Charset): String? = try {
        val decoder = cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val n = minOf(bytes.size, SAMPLE_BYTES)
        val input = ByteBuffer.wrap(bytes, 0, n)
        val output = CharBuffer.allocate(n + 16)
        val result = decoder.decode(input, output, n == bytes.size)
        if (result.isError) null else { output.flip(); output.toString() }
    } catch (_: CharacterCodingException) {
        null
    }

    internal fun strictUtf8(bytes: ByteArray): String? = strictDecode(bytes, Charsets.UTF_8)

    private fun strictDecode(bytes: ByteArray, cs: Charset): String? = try {
        val decoder = cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = ByteBuffer.wrap(bytes)
        val output = CharBuffer.allocate(8192)
        while (true) {
            val result = decoder.decode(input, output, true)
            result.throwIfError()
            if (result.isUnderflow) break
            output.clear()
        }
        output.clear()
        while (true) {
            val result = decoder.flush(output)
            result.throwIfError()
            if (result.isUnderflow) break
            output.clear()
        }
        // The platform String decoder can allocate the result directly. Keeping a
        // whole-source CharBuffer alive while copying to String doubled the peak.
        String(bytes, cs)
    } catch (_: CharacterCodingException) {
        null
    }

    private fun java.nio.charset.CoderResult.throwIfError() {
        if (isError) throwException()
    }
}
