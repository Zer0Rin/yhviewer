package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v16：设置 / 关于页的文案 */
class V16AboutTest {
    @Test fun versionLineNamesTheEdition() {
        assertEquals("版本 0.9.0 · Strict 版", AboutInfo.versionLine("0.9.0", "strict"))
        assertEquals("版本 0.9.0 · Standard 版", AboutInfo.versionLine("0.9.0", "standard"))
        assertEquals("版本 0.9.0", AboutInfo.versionLine("0.9.0", ""))
    }

    @Test fun privacyLinesMatchCapabilities() {
        val strict = AboutInfo.privacyLines(networkCapable = false, pythonAvailable = false)
        assertTrue(strict.any { it.contains("没有网络权限") })
        assertFalse(strict.any { it.contains("Python") })
        val standard = AboutInfo.privacyLines(networkCapable = true, pythonAvailable = true)
        assertTrue(standard.any { it.contains("默认不能联网") })
        assertTrue(standard.any { it.contains("Python") })
        assertFalse(standard.any { it.contains("没有网络权限") })
    }

    /** 许可证文件继续随包分发，但设置页暂时不展示 */
    @Test fun everyListedLicenseIsPackaged() {
        AboutInfo.LICENSES.forEach { l ->
            val main = File("src/main/assets/${l.asset}")
            val standard = File("src/standard/assets/${l.asset}")
            assertTrue("${l.asset} missing", main.isFile || standard.isFile)
            if (l.asset.startsWith("pyodide/")) assertFalse("${l.asset} must not ship in Strict", main.isFile)
        }
    }
}
