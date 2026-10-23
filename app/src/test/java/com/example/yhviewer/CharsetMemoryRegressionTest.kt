package com.example.yhviewer

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CoderResult
import org.junit.Assert.*
import org.junit.Test

class CharsetMemoryRegressionTest {
    @Test fun strictValidationUsesBoundedWorkspaceBeforeProducingText() {
        val capacities = mutableListOf<Int>()
        val tracking = object : Charset("Tracking-UTF8", null) {
            override fun contains(cs: Charset) = false
            override fun newEncoder() = Charsets.UTF_8.newEncoder()
            override fun newDecoder(): CharsetDecoder {
                val delegate = Charsets.UTF_8.newDecoder()
                return object : CharsetDecoder(this, 1f, 1f) {
                    override fun decodeLoop(input: ByteBuffer, output: CharBuffer): CoderResult {
                        capacities += output.capacity()
                        return delegate.decode(input, output, false)
                    }
                }
            }
        }
        val source = "x".repeat(256 * 1024).toByteArray()
        val method = CharsetDetector::class.java.getDeclaredMethod("strictDecode", ByteArray::class.java, Charset::class.java)
        method.isAccessible = true
        assertEquals(String(source, Charsets.UTF_8), method.invoke(CharsetDetector, source, tracking))
        assertTrue("Validation must not allocate a source-sized CharBuffer: ${capacities.first()}", capacities.first() <= 8192)
    }

    @Test fun detectedTextAndCharsetMatchOriginalDecoderAcrossEncodingsAndErrors() {
        fun original(bytes: ByteArray): CharsetDetector.Result {
            if (bytes.take(3) == listOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())) return CharsetDetector.Result(String(bytes, 3, bytes.size - 3, Charsets.UTF_8), Charsets.UTF_8)
            if (bytes.take(2) == listOf(0xff.toByte(), 0xfe.toByte())) return CharsetDetector.Result(String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE), Charsets.UTF_16LE)
            if (bytes.take(2) == listOf(0xfe.toByte(), 0xff.toByte())) return CharsetDetector.Result(String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE), Charsets.UTF_16BE)
            for (cs in listOf(Charsets.UTF_8, Charset.forName("GB18030"), Charset.forName("Big5"))) {
                val decoded = runCatching { cs.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() }.getOrNull()
                if (decoded != null) return CharsetDetector.Result(decoded, cs)
            }
            return CharsetDetector.Result(String(bytes, Charsets.UTF_8), Charsets.UTF_8)
        }
        val text = "第1章 😀\r\n繁體与简体\r第二章\n正文é".repeat(400)
        val samples = mutableListOf<ByteArray>()
        listOf("UTF-8", "UTF-16LE", "UTF-16BE", "GB18030", "Big5").forEach { samples += text.toByteArray(Charset.forName(it)) }
        samples += byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + samples[0]
        samples += byteArrayOf(0xff.toByte(), 0xfe.toByte()) + samples[1]
        samples += byteArrayOf(0xfe.toByte(), 0xff.toByte()) + samples[2]
        samples += "a".repeat(8191).toByteArray() + "😀中文".toByteArray() // decoder workspace boundary
        samples += "a".repeat(8191).toByteArray() + byteArrayOf(0xf0.toByte(), 0x9f.toByte()) // truncated multibyte
        val random = kotlin.random.Random(419)
        repeat(120) { samples += random.nextBytes(random.nextInt(0, 2000)) }
        // v1.0：旧实现把 Big5 和无 BOM 的 UTF-16 都"成功"解成 GB18030 乱码（真机验收发现），这三种样本的
        // 正确结果不再等于旧实现，改为断言解码正确。其余样本（UTF-8、GB18030、带 BOM、缓冲区边界、截断）
        // 仍必须和旧实现逐字一致。
        val changed = setOf(1, 2, 4) // UTF-16LE、UTF-16BE、Big5（均无 BOM）
        samples.take(9).forEachIndexed { i, bytes ->
            if (i in changed) {
                val cs = listOf("UTF-8", "UTF-16LE", "UTF-16BE", "GB18030", "Big5")[i]
                assertEquals("sample $i", String(bytes, Charset.forName(cs)), CharsetDetector.decode(bytes).text)
                assertEquals("sample $i", cs, CharsetDetector.decode(bytes).charset.name())
            } else {
                assertEquals("sample $i", original(bytes), CharsetDetector.decode(bytes))
            }
        }
        // 随机字节：本来就无法"正确"解码。只要求不抛异常、长度合理、编码在候选范围内
        val allowed = setOf("UTF-8", "GB18030", "Big5", "UTF-16LE", "UTF-16BE")
        samples.drop(9).forEach { bytes ->
            val r = CharsetDetector.decode(bytes)
            assertTrue(r.charset.name() in allowed)
            assertTrue(bytes.isEmpty() || r.text.isNotEmpty())
        }
    }
}
