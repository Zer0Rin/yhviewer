package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

/** v1.0：真机验收发现 Big5 和无 BOM 的 UTF-16 被当成 GB18030 解成乱码 */
class V1EncodingTest {
    private val simplified = "第一章 夜车\n　　夜色渐深，城市的灯光一盏盏亮起。他沿着长街慢慢走着，雨水打湿了青石板。\n" +
        "第二章 抵达\n　　天亮了，列车终于到站。她提着箱子站在月台上，看着远处的山。\n简体阅读测试：记录 00001。\n"
    private val traditional = "第一章 夜車\n　　夜色漸深，城市的燈光一盞盞亮起。他沿著長街慢慢走著，雨水打濕了青石板。\n" +
        "第二章 抵達\n　　天亮了，列車終於到站。她提著箱子站在月台上，看著遠處的山。\n繁體閱讀測試：記錄 00001。\n"

    private fun detect(text: String, cs: String) = CharsetDetector.decode(text.toByteArray(Charset.forName(cs)))

    @Test fun big5TraditionalIsNotGarbled() {
        val r = detect(traditional.repeat(20), "Big5")
        assertEquals("Big5", r.charset.name())
        assertEquals(traditional.repeat(20), r.text)
    }

    @Test fun utf16WithoutBomIsDetected() {
        val le = detect(simplified.repeat(20), "UTF-16LE")
        assertEquals("UTF-16LE", le.charset.name())
        assertEquals(simplified.repeat(20), le.text)
        val be = detect(traditional.repeat(20), "UTF-16BE")
        assertEquals("UTF-16BE", be.charset.name())
        assertEquals(traditional.repeat(20), be.text)
        // 纯英文的 UTF-16LE 恰好也是合法 UTF-8（夹着 NUL），不能当成 UTF-8
        val ascii = detect("Chapter 1\nHello world, this is a test.\n".repeat(10), "UTF-16LE")
        assertEquals("UTF-16LE", ascii.charset.name())
    }

    @Test fun gbkStillWinsForSimplifiedAndTraditionalInGbk() {
        assertEquals("GB18030", detect(simplified.repeat(20), "GBK").charset.name())
        val t = detect(traditional.repeat(20), "GBK")
        assertEquals("GB18030", t.charset.name())
        assertEquals(traditional.repeat(20), t.text)
    }

    @Test fun utf8Unchanged() {
        assertEquals(Charsets.UTF_8, detect(simplified, "UTF-8").charset)
        assertEquals(Charsets.UTF_8, detect(traditional, "UTF-8").charset)
    }

    @Test fun scoreSeparatesTextFromMojibake() {
        val big5Bytes = traditional.toByteArray(Charset.forName("Big5"))
        val asGb = String(big5Bytes, Charset.forName("GB18030"))
        assert(TextScore.of(traditional) > TextScore.of(asGb) + 0.2) { "${TextScore.of(traditional)} vs ${TextScore.of(asGb)}" }
    }
}
