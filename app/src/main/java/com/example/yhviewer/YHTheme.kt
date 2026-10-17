package com.example.yhviewer

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * App 外壳（书架、面板、对话框）的视觉语言。v8 起：
 *
 *  - **颜色跟随阅读主题**：书架、阅读页、编辑器用同一套色板（纸张 / 墨色 / 终端 / 跟随系统），
 *    从书架进入阅读不再有"外壳偏黄、正文偏白"的色差。色值和 assets/shared/themes.css 一致。
 *  - 三套参考设计系统各取一部分（见 docs/design.md）：
 *      Overture：细线分隔、直角"印刷品"封面、大写字距小标签
 *      Lattice ：点线输入框（聚焦时点变成强调色）、点阵进度、选中标签下的短标记
 *      Lens    ：浮动胶囊操作栏、按压时轻微下沉回弹
 *  - 形状规则：**内容直角，控件圆润**。封面、列表行是直角；按钮、筛选片、胶囊栏是全圆角；对话框和面板 12–16dp。
 *  - 强调色只标记动作、选中、进度；冷色标记焦点和信息。
 *  - 间距用 4dp 网格：4 / 8 / 12 / 16 / 24 / 32
 */

// ======================= 色板 =======================

@Immutable
data class YHColors(
    val bg: Color,
    val surface: Color,
    val fg: Color,
    val muted: Color,
    val subtle: Color,
    val accent: Color,     // 强调色：动作、进度、选中
    val onAccent: Color,
    val cold: Color,       // 冷色：焦点、信息
    val line: Color,       // 细线（强）：输入框底线、方框
    val lineSoft: Color,   // 细线（弱）：列表分隔、进度底色
    val shadow: Color,     // 封面投影颜色
    val dark: Boolean,
)

/**
 * 每个阅读主题的完整色板。bg / surface / fg / muted / line / accent 和 themes.css 对应
 * （line = --border；accent 取 --link，--accent 在表面底色上对小字对比度不够 4.5:1）。
 * 其余几个是外壳额外需要的：subtle（更弱的文字）、lineStrong、cold、shadow。
 */
data class ReaderPalette(
    val bg: Long, val surface: Long, val fg: Long, val muted: Long, val line: Long, val accent: Long, val dark: Boolean,
    val subtle: Long, val lineStrong: Long, val cold: Long, val shadow: Long,
) {
    fun toColors() = YHColors(
        bg = Color(bg), surface = Color(surface), fg = Color(fg), muted = Color(muted), subtle = Color(subtle),
        accent = Color(accent), onAccent = Color(bg), cold = Color(cold),
        line = Color(lineStrong), lineSoft = Color(line), shadow = Color(shadow), dark = dark,
    )

    companion object {
        val PAPER = ReaderPalette(
            bg = 0xFFFAF9F5, surface = 0xFFF0EEE6, fg = 0xFF2B2A27, muted = 0xFF6E6C65, line = 0xFFE6E3D9,
            accent = 0xFFAC4D29, dark = false,
            subtle = 0xFF6E6C65, lineStrong = 0xFFD3CFC3, cold = 0xFF3E63A8, shadow = 0xFF2D2A22,
        )
        val INK = ReaderPalette(
            bg = 0xFF262624, surface = 0xFF30302D, fg = 0xFFE8E5DC, muted = 0xFFA29F96, line = 0xFF3B3A36,
            accent = 0xFFE08A6C, dark = true,
            subtle = 0xFFA29F96, lineStrong = 0xFF55534D, cold = 0xFF8DB4F2, shadow = 0xFF000000,
        )
        val TERMINAL = ReaderPalette(
            bg = 0xFF1D2A32, surface = 0xFF172229, fg = 0xFFD3DAE0, muted = 0xFF7F929E, line = 0xFF2E3E48,
            accent = 0xFFE9A23B, dark = true,
            subtle = 0xFF7F929E, lineStrong = 0xFF46596A, cold = 0xFF7CB7EC, shadow = 0xFF000000,
        )

        fun of(theme: ReaderTheme, systemDark: Boolean): ReaderPalette = when (theme) {
            ReaderTheme.PAPER -> PAPER
            ReaderTheme.INK -> INK
            ReaderTheme.TERMINAL -> TERMINAL
            ReaderTheme.AUTO -> if (systemDark) INK else PAPER
        }
    }
}

val LocalYH = staticCompositionLocalOf { ReaderPalette.PAPER.toColors() }

private fun YHColors.toScheme(): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent, onPrimary = onAccent,
        primaryContainer = surface, onPrimaryContainer = fg,
        secondary = cold, onSecondary = bg,
        secondaryContainer = surface, onSecondaryContainer = fg,
        tertiary = cold,
        background = bg, onBackground = fg,
        surface = bg, onSurface = fg,
        surfaceVariant = surface, onSurfaceVariant = muted,
        surfaceContainerLowest = bg, surfaceContainerLow = surface,
        surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surface,
        surfaceTint = Color.Transparent, // 不要 M3 的"抬高变色"，层次只靠色调和细线
        outline = line, outlineVariant = lineSoft,
        error = accent, onError = onAccent,
        inverseSurface = fg, inverseOnSurface = bg, inversePrimary = accent,
        scrim = Color(0x99000000),
    )
}

// ======================= 字体 =======================
// 标题用系统衬线（Noto Serif，中文落到 Noto Serif CJK；机型没有中文衬线时自动用黑体），和正文阅读字体一致。
// v7 用过 Bodoni Moda：西文细线在 16sp 下太细，"1" 和 "l" 难分，和中文混排也不协调，v8 去掉。
// 界面文字用 Manrope（res/font/manrope.ttf，可变字体）。

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    R.font.manrope, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

object YHFonts {
    val display: FontFamily = FontFamily.Serif
    val sans = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700))
}

private val YHTypography = Typography().let { t ->
    fun TextStyle.sans() = copy(fontFamily = YHFonts.sans)
    fun TextStyle.display() = copy(fontFamily = YHFonts.display)
    Typography(
        displayLarge = t.displayLarge.display(),
        displayMedium = t.displayMedium.display(),
        displaySmall = t.displaySmall.display(),
        // 书架大标题
        headlineLarge = TextStyle(fontFamily = YHFonts.display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
        headlineMedium = t.headlineMedium.display(),
        headlineSmall = TextStyle(fontFamily = YHFonts.display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
        // 书名
        titleLarge = TextStyle(fontFamily = YHFonts.display, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp),
        titleMedium = TextStyle(fontFamily = YHFonts.display, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
        titleSmall = t.titleSmall.sans().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontFamily = YHFonts.sans, fontSize = 16.sp, lineHeight = 26.sp),
        bodyMedium = TextStyle(fontFamily = YHFonts.sans, fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = YHFonts.sans, fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge = TextStyle(fontFamily = YHFonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = YHFonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
        // 小标签：大写 + 字距 0.12em（"继续阅读"、"7 本书 · 12 份文档"）
        labelSmall = TextStyle(fontFamily = YHFonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.12.em),
    )
}

// 内容直角（封面、列表行在各自代码里用 RectangleShape），控件和面板圆润
private val YHShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // 下拉菜单
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),      // 对话框内的块
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),  // 对话框、底部面板
)

/** 胶囊：按钮、筛选片、浮动操作栏 */
val PillShape = RoundedCornerShape(percent = 50)

/**
 * 包住整个 App。颜色由阅读主题决定（vm.theme），状态栏 / 导航栏图标颜色跟着色板深浅走。
 * 阅读页的沉浸模式（ImmersiveSystemBars）会临时改图标颜色，离开时恢复成这里设置的值。
 */
@Composable
fun YHTheme(readerTheme: ReaderTheme, content: @Composable () -> Unit) {
    val palette = rememberReaderPalette(readerTheme)
    val colors = palette.toColors()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !palette.dark
            controller.isAppearanceLightNavigationBars = !palette.dark
        }
    }
    CompositionLocalProvider(LocalYH provides colors) {
        MaterialTheme(colorScheme = colors.toScheme(), typography = YHTypography, shapes = YHShapes, content = content)
    }
}

// ======================= 阅读页配色 =======================

@Composable
fun rememberReaderPalette(theme: ReaderTheme): ReaderPalette = ReaderPalette.of(theme, isSystemInDarkTheme())

/**
 * 阅读页局部主题。v8 起外壳本身就跟随阅读主题，这里和外层完全一致；
 * 保留它是为了阅读页在主题切换的同一帧里也拿到新色板（外层和这里读的是同一个 vm.theme）。
 */
@Composable
fun ReaderChromeTheme(palette: ReaderPalette, content: @Composable () -> Unit) {
    val c = palette.toColors()
    CompositionLocalProvider(LocalYH provides c) {
        MaterialTheme(
            colorScheme = c.toScheme(),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}
