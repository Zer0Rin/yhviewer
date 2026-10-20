package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v16：设置 / 关于页的文案和许可证文件 */
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

    /** 列出的许可证文件都随包分发：Pyodide / CPython 只在 Standard 版，其余在公共 assets */
    @Test fun everyListedLicenseIsPackaged() {
        AboutInfo.LICENSES.forEach { l ->
            val main = File("src/main/assets/${l.asset}")
            val standard = File("src/standard/assets/${l.asset}")
            assertTrue("${l.asset} missing", main.isFile || standard.isFile)
            if (l.asset.startsWith("pyodide/")) assertFalse("${l.asset} must not ship in Strict", main.isFile)
        }
    }
}
