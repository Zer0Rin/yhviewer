package com.example.yhviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
import kotlin.math.roundToInt

/*
 * v13：液态玻璃（毛玻璃）。
 *
 * Compose 没法模糊"另一个 View（WebView）画出来的内容"，所以模糊交给网页自己做：
 * 原生控件（底部章节胶囊、拖动中的滑杆）把自己在屏幕上的位置登记到 GlassHost，
 * GlassBridge 换算成 WebView 里的 CSS 像素，调用页面的 yhGlass([...])；页面在同一位置放一个
 * position:fixed + backdrop-filter:blur 的空 div，把下面的正文（包括连续滚动里的章节 iframe）模糊掉。
 * 原生控件本身只画一层很淡的色调、顶部高光和描边，叠在模糊上就是毛玻璃。
 *
 * 只有我们自己的页面模板有 yhGlass：连续滚动外层页面、TXT 单章页、Markdown。
 * 原样 HTML 和禁用 JS 的 EPUB 单章模式不支持，这时 supported = false，控件改用不透明度更高的底色。
 */

/** 屏幕像素里的一块区域 */
data class GlassRect(val left: Float, val top: Float, val width: Float, val height: Float, val radius: Float)

class GlassHost {
    /** 当前页面能不能做背景模糊（页面模板里有 yhGlass） */
    var supported by mutableStateOf(false)
    private val rects = mutableStateMapOf<String, GlassRect>()
    /** 页面重新加载后要重发一次 */
    var pageTick by mutableIntStateOf(0)
        private set

    fun set(key: String, rect: GlassRect?) {
        if (rect == null) rects.remove(key) else if (rects[key] != rect) rects[key] = rect
    }
    fun pageLoaded() { pageTick++ }
    internal fun snapshot(): List<GlassRect> = rects.entries.sortedBy { it.key }.map { it.value }
}

val LocalGlassHost = staticCompositionLocalOf<GlassHost?> { null }

/** 纯函数，JVM 可测：屏幕像素 → WebView 里的 CSS 像素，生成 yhGlass 调用 */
object GlassScript {
    const val MAX_RECTS = 4

    fun build(rects: List<GlassRect>, viewLeft: Float, viewTop: Float, cssScale: Float): String {
        val s = if (cssScale.isFinite() && cssScale > 0f) cssScale else 1f
        val items = rects.take(MAX_RECTS).joinToString(",") { r ->
            fun f(v: Float) = String.format(Locale.ROOT, "%.1f", v)
            "{\"x\":${f((r.left - viewLeft) / s)},\"y\":${f((r.top - viewTop) / s)}," +
                "\"w\":${f(r.width / s)},\"h\":${f(r.height / s)},\"r\":${f(r.radius / s)}}"
        }
        return "window.yhGlass && window.yhGlass([$items])"
    }
}

/**
 * 把这块控件登记成毛玻璃区域。visible = false（或离开组合）时撤销。
 * radius 按控件形状给（胶囊传高度的一半即可，传 null 自动取高度一半）。
 */
fun Modifier.glassRegion(key: String, visible: Boolean, radius: Dp? = null): Modifier = composed {
    val host = LocalGlassHost.current ?: return@composed this
    val density = LocalDensity.current
    val latestVisible by rememberUpdatedState(visible)
    DisposableEffect(host, key) { onDispose { host.set(key, null) } }
    LaunchedEffect(visible) { if (!visible) host.set(key, null) }
    this.onGloballyPositioned { c ->
        if (!latestVisible) return@onGloballyPositioned
        val p = c.positionOnScreen()
        val h = c.size.height.toFloat()
        val r = radius?.let { with(density) { it.toPx() } } ?: (h / 2f)
        host.set(key, GlassRect(p.x, p.y, c.size.width.toFloat(), h, r))
    }
}

/** 区域或页面变化时把模糊区域同步给页面 */
@Composable
fun GlassBridge(host: GlassHost, handle: WebViewHandle) {
    LaunchedEffect(host, handle) {
        snapshotFlow { host.supported to (host.snapshot() to host.pageTick) }.collectLatest { (supported, state) ->
            if (!supported) return@collectLatest
            val view = handle.webView ?: return@collectLatest
            val loc = IntArray(2).also(view::getLocationOnScreen)
            @Suppress("DEPRECATION") val scale = view.scale
            handle.evaluate(GlassScript.build(state.first, loc[0].toFloat(), loc[1].toFloat(), scale))
        }
    }
}

/**
 * 液态玻璃的外观：很淡的色调 + 顶部高光 + 上亮下暗的描边。
 * blurBehind = false（页面不能模糊）时色调加深，保证文字看得清。
 */
fun Modifier.liquidGlass(shape: Shape, blurBehind: Boolean): Modifier = composed {
    val yh = LocalYH.current
    val tint = yh.surface.copy(alpha = if (blurBehind) 0.34f else 0.9f)
    val sheen = Color.White.copy(alpha = if (yh.dark) 0.07f else 0.32f)
    val edgeTop = Color.White.copy(alpha = if (yh.dark) 0.22f else 0.75f)
    val edgeBottom = yh.line.copy(alpha = if (yh.dark) 0.55f else 0.35f)
    this
        .clip(shape)
        .background(tint)
        .background(Brush.verticalGradient(listOf(sheen, Color.Transparent)))
        .border(1.dp, Brush.verticalGradient(listOf(edgeTop, edgeBottom)), shape)
}
