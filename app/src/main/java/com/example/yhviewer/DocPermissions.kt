package com.example.yhviewer

/*
 * 每份 HTML 文档自己的权限，以及由权限推导出的三条策略：
 *  - CspPolicy：写进响应头的 Content-Security-Policy（浏览器内核层面，按"资源类型"放行）
 *  - RequestPolicy：shouldInterceptRequest 里决定哪些请求可以真正出网（按"主机"放行）
 *  - NavigationPolicy：页面跳转时，交给系统浏览器还是拦截
 * 两层是"与"的关系：CSP 允许这种资源类型，并且拦截层允许这个主机，请求才会发出去。
 *
 * 全部是纯 Kotlin，可以在 JVM 单元测试里穷举验证。
 *
 * 不论权限怎么设，下面这些永远不放开（不在本文件里，由 SandboxWebView 写死）：
 * 读取手机文件（file:// / content://）、JS 桥、读取其他文档的数据。
 */

data class DocPermissions(
    val scripts: Boolean = true,
    val persistentStorage: Boolean = false,
    val scriptedNavigation: Boolean = false,
    val cdn: Boolean = false,
    val externalMedia: Boolean = false,
    val frames: Boolean = false,
    val network: Boolean = false,
) {
    /** 需要联网才有意义的权限；Strict 版里一律无效 */
    val needsNetwork: Boolean get() = cdn || externalMedia || frames || network

    /**
     * 实际生效的权限：
     *  - Strict 版（没有 INTERNET 权限）去掉所有联网类权限
     *  - 本地数据、脚本跳转都依赖脚本，脚本关闭时一并失效
     */
    fun effective(networkCapable: Boolean): DocPermissions {
        var p = this
        if (!networkCapable) p = p.copy(cdn = false, externalMedia = false, frames = false, network = false)
        if (!p.scripts) p = p.copy(persistentStorage = false, scriptedNavigation = false)
        return p
    }

    /** 比默认档"标准"更宽松（用于阅读页顶栏的"已放宽"提示）。传入的应是 effective 之后的值 */
    val isRelaxed: Boolean get() = persistentStorage || scriptedNavigation || needsNetwork

    fun toBits(): Int =
        (if (scripts) SCRIPTS else 0) or
            (if (persistentStorage) STORAGE else 0) or
            (if (scriptedNavigation) NAVIGATION else 0) or
            (if (cdn) CDN else 0) or
            (if (externalMedia) MEDIA else 0) or
            (if (frames) FRAMES else 0) or
            (if (network) NETWORK else 0)

    companion object {
        // 存进数据库的位标记。只能新增，不能改已有的值
        const val SCRIPTS = 1
        const val STORAGE = 2
        const val NAVIGATION = 4
        const val CDN = 8
        const val MEDIA = 16
        const val FRAMES = 32
        const val NETWORK = 64

        fun fromBits(bits: Int) = DocPermissions(
            scripts = bits and SCRIPTS != 0,
            persistentStorage = bits and STORAGE != 0,
            scriptedNavigation = bits and NAVIGATION != 0,
            cdn = bits and CDN != 0,
            externalMedia = bits and MEDIA != 0,
            frames = bits and FRAMES != 0,
            network = bits and NETWORK != 0,
        )

        val STRICT = DocPermissions(scripts = false)
        val STANDARD = DocPermissions(scripts = true)

        /** 信任：不包含嵌入页面和主动联网；两者只能单独手动打开 */
        val TRUSTED = DocPermissions(
            scripts = true, persistentStorage = true, scriptedNavigation = true,
            cdn = true, externalMedia = true, frames = false, network = false,
        )
    }
}

enum class PermissionTier(val label: String, val preset: DocPermissions?) {
    STRICT("严格", DocPermissions.STRICT),
    STANDARD("标准", DocPermissions.STANDARD),
    TRUSTED("信任", DocPermissions.TRUSTED),
    CUSTOM("自定义", null),
    ;

    companion object {
        /** 正好等于某个预设就显示那个档位，否则是"自定义" */
        fun of(p: DocPermissions): PermissionTier = entries.firstOrNull { it.preset == p } ?: CUSTOM
    }
}

object CspPolicy {
    val CDN_HOSTS = listOf("https://cdnjs.cloudflare.com", "https://cdn.jsdelivr.net", "https://unpkg.com")

    /** iframe 内的第三方页面不继承本文档 CSP，frames 权限等于允许它自行联网。
     * @param p 实际生效的权限（effective 之后） */
    fun build(p: DocPermissions): String {
        val cdn = if (p.cdn) CDN_HOSTS else emptyList()
        val media = if (p.externalMedia) listOf("https:") else emptyList()

        val script = if (p.scripts) listOf("'self'", "'unsafe-inline'", "'unsafe-eval'", "data:", "blob:") + cdn else listOf("'none'")
        val style = listOf("'self'", "'unsafe-inline'", "data:") + cdn
        val img = listOf("'self'", "data:", "blob:") + media
        val font = listOf("'self'", "data:") + cdn + media
        val mediaSrc = listOf("'self'", "data:", "blob:") + media
        val connect = if (p.network) listOf("'self'", "https:", "wss:") else listOf("'none'")
        val frame = if (p.frames) listOf("https:") else listOf("'none'")

        return listOf(
            "default-src 'none'",
            "script-src " + script.joinToString(" "),
            "style-src " + style.joinToString(" "),
            "img-src " + img.joinToString(" "),
            "font-src " + font.distinct().joinToString(" "),
            "media-src " + mediaSrc.joinToString(" "),
            "connect-src " + connect.joinToString(" "),
            "frame-src " + frame.joinToString(" "),
            "form-action 'none'",
            "base-uri 'none'",
            "worker-src 'none'",
            "object-src 'none'",
        ).joinToString("; ")
    }
}

object RequestPolicy {
    private val cdnHosts = CspPolicy.CDN_HOSTS.map { it.removePrefix("https://") }.toSet()

    /**
     * 不属于虚拟域名的请求，能不能真正发到网络上。只放行 https。
     * 外部媒体 / 嵌入页面 / 主动联网 是按"类型"授权的，主机层面放开全部 https，
     * 具体哪类资源能加载由 CSP 决定。只开了 CDN 时，主机层面也只放行三个 CDN。
     * iframe 内的第三方页面不继承本文档 CSP，frames 权限等于允许它自行联网。
     */
    fun allowsNetwork(p: DocPermissions, scheme: String?, host: String?): Boolean {
        if (scheme != "https" || host.isNullOrEmpty()) return false
        if (p.externalMedia || p.frames || p.network) return true
        return p.cdn && host in cdnHosts
    }
}

object NavigationPolicy {
    enum class Decision { OPEN_EXTERNAL, ALLOW_IN_PLACE, BLOCK }

    /**
     * 跳出文档的导航：
     *  - 主框架：用户亲手点击，或者授权了"脚本打开外部链接"，才交给系统浏览器；文档本身永远不被替换
     *  - 子框架（iframe）：授权了"嵌入外部页面"时，https 页面可以在框架内加载，其余拦截
     */
    fun decide(p: DocPermissions, isMainFrame: Boolean, hasGesture: Boolean, scheme: String?): Decision {
        if (!isMainFrame) return if (p.frames && scheme == "https") Decision.ALLOW_IN_PLACE else Decision.BLOCK
        if (scheme !in setOf("http", "https", "mailto")) return Decision.BLOCK
        return if (hasGesture || p.scriptedNavigation) Decision.OPEN_EXTERNAL else Decision.BLOCK
    }
}
