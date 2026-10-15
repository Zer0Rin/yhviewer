package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocPermissionsTest {

    private fun directive(csp: String, name: String): String =
        csp.split("; ").first { it.startsWith("$name ") }.removePrefix("$name ")

    // ---------- 存储 ----------

    @Test fun bitsRoundTripForEveryCombination() {
        for (bits in 0 until 128) {
            assertEquals(bits, DocPermissions.fromBits(bits).toBits())
        }
    }

    @Test fun standardIsStoredAsOne() {
        // 迁移里新列的 DEFAULT 1 依赖这个值：老文档升级后就是"标准"档，行为不变
        assertEquals(1, DocPermissions.STANDARD.toBits())
    }

    // ---------- 档位 ----------

    @Test fun presetsMapToTheirTier() {
        assertEquals(PermissionTier.STRICT, PermissionTier.of(DocPermissions.STRICT))
        assertEquals(PermissionTier.STANDARD, PermissionTier.of(DocPermissions.STANDARD))
        assertEquals(PermissionTier.TRUSTED, PermissionTier.of(DocPermissions.TRUSTED))
    }

    @Test fun anyManualChangeBecomesCustom() {
        assertEquals(PermissionTier.CUSTOM, PermissionTier.of(DocPermissions.STANDARD.copy(cdn = true)))
        assertEquals(PermissionTier.CUSTOM, PermissionTier.of(DocPermissions.TRUSTED.copy(network = true)))
    }

    @Test fun trustedExcludesFramesAndNetwork() {
        assertFalse(DocPermissions.TRUSTED.network)
        assertFalse(DocPermissions.TRUSTED.frames)
        assertEquals(PermissionTier.CUSTOM, PermissionTier.of(DocPermissions.TRUSTED.copy(frames = true)))
    }

    @Test fun strictSavedTrustedDoesNotGainNetworkAfterStandardReload() {
        val saved = DocPermissions.TRUSTED.effective(networkCapable = false)
        val reopened = DocPermissions.fromBits(saved.toBits()).effective(networkCapable = true)
        assertFalse(reopened.needsNetwork)
        assertEquals(DocPermissions.TRUSTED.copy(cdn = false, externalMedia = false), reopened)
    }

    @Test fun scriptsOffSaveCannotRestoreStorageOrNavigationWhenReenabled() {
        val saved = DocPermissions.TRUSTED.copy(scripts = false).effective(networkCapable = true)
        val reopened = DocPermissions.fromBits(saved.toBits()).copy(scripts = true).effective(networkCapable = true)
        assertFalse(reopened.persistentStorage)
        assertFalse(reopened.scriptedNavigation)
    }

    @Test fun boundedTrustedPresetKeepsTrustedTier() {
        for (network in listOf(false, true)) for (storage in listOf(false, true)) {
            val saved = DocPermissions.TRUSTED.effective(network, storage)
            assertEquals(PermissionTier.TRUSTED, PermissionTier.of(saved, network, storage))
            assertEquals(PermissionTier.CUSTOM, PermissionTier.of(DocPermissions.TRUSTED.copy(frames = true), network, storage))
        }
    }

    // ---------- 生效规则 ----------

    @Test fun strictBuildDropsNetworkPermissions() {
        val all = DocPermissions.fromBits(127)
        val e = all.effective(networkCapable = false)
        assertFalse(e.needsNetwork)
        assertTrue(e.scripts && e.persistentStorage && e.scriptedNavigation)
    }

    @Test fun storageAndNavigationNeedScripts() {
        val e = DocPermissions(scripts = false, persistentStorage = true, scriptedNavigation = true).effective(true)
        assertFalse(e.persistentStorage)
        assertFalse(e.scriptedNavigation)
    }

    @Test fun unsupportedStorageIsClearedFromNewGrant() {
        val saved = DocPermissions.TRUSTED.effective(networkCapable = true, persistentStorageSupported = false)
        assertFalse(saved.persistentStorage)
        assertFalse(DocPermissions.fromBits(saved.toBits()).effective(true).persistentStorage)
    }

    @Test fun relaxedFlag() {
        assertFalse(DocPermissions.STRICT.isRelaxed)
        assertFalse(DocPermissions.STANDARD.isRelaxed)
        assertTrue(DocPermissions.TRUSTED.isRelaxed)
        assertFalse("Strict 版里 CDN 无效，不算放宽", DocPermissions.STANDARD.copy(cdn = true).effective(false).isRelaxed)
    }

    // ---------- CSP ----------

    @Test fun standardCspMatchesTheOriginalSandbox() {
        val csp = CspPolicy.build(DocPermissions.STANDARD)
        assertEquals("'self' 'unsafe-inline' 'unsafe-eval' data: blob:", directive(csp, "script-src"))
        assertEquals("'none'", directive(csp, "connect-src"))
        assertEquals("'none'", directive(csp, "frame-src"))
        assertEquals("'self' data: blob:", directive(csp, "img-src"))
        assertTrue(csp.startsWith("default-src 'none'"))
    }

    @Test fun strictCspBlocksScripts() {
        assertEquals("'none'", directive(CspPolicy.build(DocPermissions.STRICT), "script-src"))
    }

    @Test fun cdnOpensOnlyCdnHostsAndNoConnections() {
        val csp = CspPolicy.build(DocPermissions.STANDARD.copy(cdn = true))
        assertTrue(directive(csp, "script-src").contains("https://cdn.jsdelivr.net"))
        assertTrue(directive(csp, "style-src").contains("https://cdnjs.cloudflare.com"))
        assertFalse(directive(csp, "script-src").contains("https: "))       // 不是任意 https
        assertEquals("'none'", directive(csp, "connect-src"))              // 库能加载，但页面仍不能主动联网
        assertEquals("'self' data: blob:", directive(csp, "img-src"))
    }

    @Test fun externalMediaOpensImagesFontsMediaOnly() {
        val csp = CspPolicy.build(DocPermissions.STANDARD.copy(externalMedia = true))
        assertTrue(directive(csp, "img-src").endsWith("https:"))
        assertTrue(directive(csp, "font-src").endsWith("https:"))
        assertTrue(directive(csp, "media-src").endsWith("https:"))
        assertEquals("'none'", directive(csp, "connect-src"))
        assertEquals("'none'", directive(csp, "frame-src"))
    }

    @Test fun networkOpensConnectSrc() {
        val csp = CspPolicy.build(DocPermissions.STANDARD.copy(network = true))
        assertEquals("'self' https: wss:", directive(csp, "connect-src"))
    }

    @Test fun formsAndBaseAreAlwaysLocked() {
        for (bits in 0 until 128) {
            val csp = CspPolicy.build(DocPermissions.fromBits(bits))
            assertEquals("'none'", directive(csp, "form-action"))
            assertEquals("'none'", directive(csp, "base-uri"))
            assertFalse(csp.contains("http:"))   // 永远不放行明文 http
        }
    }

    // ---------- 请求拦截 ----------

    @Test fun standardAllowsNoNetwork() {
        assertFalse(RequestPolicy.allowsNetwork(DocPermissions.STANDARD, "https", "cdn.jsdelivr.net"))
    }

    @Test fun cdnOnlyAllowsCdnHosts() {
        val p = DocPermissions.STANDARD.copy(cdn = true)
        assertTrue(RequestPolicy.allowsNetwork(p, "https", "cdn.jsdelivr.net"))
        assertFalse(RequestPolicy.allowsNetwork(p, "https", "evil.example.com"))
        assertFalse(RequestPolicy.allowsNetwork(p, "https", "cdn.jsdelivr.net.evil.com"))
    }

    @Test fun plainHttpIsNeverAllowed() {
        assertFalse(RequestPolicy.allowsNetwork(DocPermissions.fromBits(127), "http", "example.com"))
    }

    @Test fun typeLevelPermissionsOpenAnyHttpsHost() {
        assertTrue(RequestPolicy.allowsNetwork(DocPermissions.STANDARD.copy(externalMedia = true), "https", "example.com"))
    }

    // ---------- 导航 ----------

    @Test fun userTapOpensExternally() {
        assertEquals(
            NavigationPolicy.Decision.OPEN_EXTERNAL,
            NavigationPolicy.decide(DocPermissions.STANDARD, isMainFrame = true, hasGesture = true, scheme = "https"),
        )
    }

    @Test fun scriptedRedirectNeedsPermission() {
        assertEquals(
            NavigationPolicy.Decision.BLOCK,
            NavigationPolicy.decide(DocPermissions.STANDARD, isMainFrame = true, hasGesture = false, scheme = "https"),
        )
        assertEquals(
            NavigationPolicy.Decision.OPEN_EXTERNAL,
            NavigationPolicy.decide(DocPermissions.TRUSTED, isMainFrame = true, hasGesture = false, scheme = "https"),
        )
    }

    @Test fun dangerousSchemesAreAlwaysBlocked() {
        listOf("intent", "javascript", "file", "content", "tel").forEach { scheme ->
            assertEquals(
                scheme,
                NavigationPolicy.Decision.BLOCK,
                NavigationPolicy.decide(DocPermissions.fromBits(127), isMainFrame = true, hasGesture = true, scheme = scheme),
            )
        }
    }

    @Test fun subframeNavigationFollowsFramesPermission() {
        assertEquals(
            NavigationPolicy.Decision.BLOCK,
            NavigationPolicy.decide(DocPermissions.STANDARD, isMainFrame = false, hasGesture = true, scheme = "https"),
        )
        assertEquals(
            NavigationPolicy.Decision.ALLOW_IN_PLACE,
            NavigationPolicy.decide(DocPermissions.STANDARD.copy(frames = true), isMainFrame = false, hasGesture = false, scheme = "https"),
        )
        assertEquals(
            NavigationPolicy.Decision.BLOCK,
            NavigationPolicy.decide(DocPermissions.STANDARD.copy(frames = true), isMainFrame = false, hasGesture = false, scheme = "http"),
        )
    }
    @Test fun enablingFramesOnTrustedIsCustom() {
        assertEquals(PermissionTier.CUSTOM, PermissionTier.of(DocPermissions.TRUSTED.copy(frames = true)))
    }

    @Test fun everyEffectiveCombinationKeepsCspAndRequestPolicyConsistent() {
        for (bits in 0 until 128) for (capable in listOf(false, true)) {
            val p = DocPermissions.fromBits(bits).effective(capable)
            val csp = CspPolicy.build(p)
            assertEquals(p.scripts, directive(csp, "script-src") != "'none'")
            assertEquals(p.network, directive(csp, "connect-src") != "'none'")
            assertEquals(p.frames, directive(csp, "frame-src") != "'none'")
            assertEquals(p.externalMedia || p.frames || p.network, RequestPolicy.allowsNetwork(p, "https", "fixture.example"))
            assertEquals(p.cdn || p.externalMedia || p.frames || p.network, RequestPolicy.allowsNetwork(p, "https", "cdn.jsdelivr.net"))
            if (!capable) assertFalse(p.needsNetwork)
            if (!p.scripts) assertFalse(p.persistentStorage || p.scriptedNavigation)
        }
    }

    @Test fun workersAndPluginsNeverRunInLocalDocuments() {
        for (bits in 0 until 128) {
            val csp = CspPolicy.build(DocPermissions.fromBits(bits))
            assertEquals("'none'", directive(csp, "worker-src"))
            assertEquals("'none'", directive(csp, "object-src"))
        }
    }

}
