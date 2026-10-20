package com.example.yhviewer

import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * 书架和阅读页共用的小部件，对应 YHTheme 顶部注释里"从三套设计系统各取一部分"：
 *
 *   DotRule / DotProgress  ← Lattice："一切由点构成"。输入框底线和进度条是一排点
 *   Modifier.pressScale     ← Lens：按下轻微下沉，松手弹回（不晃动、不持续动画）
 *   FloatingCapsule         ← Lens：浮在内容上的胶囊操作栏，不透明底 + 细线 + 柔和投影
 *
 * 只用 Compose 自带 API，没有新依赖。真正的"背景模糊"在 Compose 里要么需要第三方库，
 * 要么拿不到 WebView 的像素（阅读页正文是 WebView），所以胶囊使用不透明的主题底色。
 */

// ======================= 点线（Lattice） =======================

/** 点的直径和间距：1.5dp 的点每 6dp 一个，在 3x 屏上是清楚的一排点，不会糊成虚线 */
private val DOT = 1.5.dp
private val DOT_PITCH = 6.dp

/**
 * 在元素底部画一排点。focused 时点变成强调色、间距变密（聚焦 = 点"收紧"），失焦恢复。
 * 用法：Modifier.dotRule(focused)
 */
fun Modifier.dotRule(color: Color, focusedColor: Color, focused: Boolean): Modifier = drawBehind {
    val dot = DOT.toPx()
    val pitch = (if (focused) DOT_PITCH * 0.75f else DOT_PITCH).toPx()
    val y = size.height - dot / 2
    drawLine(
        color = if (focused) focusedColor else color,
        start = Offset(dot / 2, y),
        end = Offset(size.width, y),
        strokeWidth = dot,
        cap = StrokeCap.Round,
        // 长度 0.01 的线段 + 圆头 = 圆点
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, pitch), 0f),
    )
}

/**
 * 点阵进度：一排圆点，读过的部分是强调色，没读的是弱细线色。
 * 点数随宽度变化（每 7dp 一个），读到 1% 也至少亮一个点，读完全亮。
 */
@Composable
fun DotProgress(fraction: Float, modifier: Modifier = Modifier, dotSize: Dp = 3.dp) {
    val yh = LocalYH.current
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(dotSize)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(f, 0f..1f) }
            .drawBehind {
                val d = dotSize.toPx()
                val pitch = d + 4.dp.toPx()
                val count = ((size.width + 4.dp.toPx()) / pitch).toInt().coerceAtLeast(1)
                val lit = dotProgressLit(f, count)
                for (i in 0 until count) {
                    drawCircle(
                        color = if (i < lit) yh.accent else yh.lineSoft,
                        radius = d / 2,
                        center = Offset(i * pitch + d / 2, size.height / 2),
                    )
                }
            },
    )
}

/** 亮几个点（纯函数，有测试）：0 → 0；(0, 1) → 至少 1、至多 count-1；1 → 全亮 */
internal fun dotProgressLit(fraction: Float, count: Int): Int = when {
    fraction <= 0f -> 0
    fraction >= 0.995f -> count
    else -> (fraction * count).toInt().coerceIn(1, (count - 1).coerceAtLeast(1))
}

// ======================= 按压反馈（Lens） =======================

/**
 * 按下时缩到 0.97，松手用弹簧回到 1。interactionSource 要和 clickable / combinedClickable 共用同一个。
 * 用 graphicsLayer，不触发重新布局。
 */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressed: Float = 0.97f): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "pressScale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

// ======================= 浮动胶囊（Lens） =======================

/**
 * 浮在内容上方的胶囊栏：左右留 16dp，底部避开导航栏再留 12dp。
 * 用于多选操作栏和阅读页的章节栏。
 */
@Composable
fun FloatingCapsule(
    modifier: Modifier = Modifier,
    /** v13：不为 null 时做成液态玻璃，并把位置登记给页面做背景模糊（见 Glass.kt） */
    glassKey: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val yh = LocalYH.current
    val surface = if (glassKey != null) {
        Modifier
            .glassRegion(glassKey, visible = true)
            .liquidGlass(PillShape, blurBehind = LocalGlassHost.current?.supported == true)
    } else {
        Modifier
            .shadow(elevation = 12.dp, shape = PillShape, ambientColor = yh.shadow, spotColor = yh.shadow)
            .background(yh.surface, PillShape)
            .border(1.dp, yh.lineSoft, PillShape)
    }
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

// ======================= 文字 =======================

/** 相对时间：一分钟内显示"刚刚"，其他交给系统（跟随系统语言） */
fun relativeTime(time: Long, now: Long = System.currentTimeMillis()): CharSequence =
    if (now - time in 0 until DateUtils.MINUTE_IN_MILLIS) "刚刚"
    else DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS)
