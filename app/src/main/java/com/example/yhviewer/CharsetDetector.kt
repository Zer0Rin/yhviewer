package com.example.yhviewer

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 文本编码识别。中文 TXT 小说大量是 GBK，按 UTF-8 读会整篇乱码。
 *
 * 策略（按顺序）：
 *  1. 有 BOM：直接按 BOM 判断（UTF-8 / UTF-16LE / UTF-16BE）
 *  2. 严格按 UTF-8 解码，遇到非法字节就失败。GBK 文本几乎不可能恰好是合法 UTF-8，所以这一步很可靠
 *  3. 严格按 GB18030（GBK 的超集）解码
 *  4. 严格按 Big5 解码
 *  5. 都失败：按 UTF-8 宽松解码（非法字节变成 �），至少能打开
 *
 * 已知局限：Big5 文本有时也能被 GB18030 "成功"解码成乱码。要更准需要统计模型（如 ICU CharsetDetector）。
 * 纯 JVM 实现，可单元测试。
 */
object CharsetDetector {

    data class Result(val text: String, val charset: Charset)

    private val GB18030: Charset = Charset.forName("GB18030")
    private val BIG5: Charset = Charset.forName("Big5")

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
        for (cs in listOf(Charsets.UTF_8, GB18030, BIG5)) {
            strictDecode(bytes, cs)?.let { return Result(it, cs) }
        }
        return Result(String(bytes, Charsets.UTF_8), Charsets.UTF_8)
    }

    private fun strictDecode(bytes: ByteArray, cs: Charset): String? = try {
        cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }
}
