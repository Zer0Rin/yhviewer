package com.example.yhviewer

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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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
 * App 外壳（书架、面板、对话框）的视觉语言，取自 Overture 设计系统：
 *   - 衬线（Bodoni Moda）只用于标题和书名，界面文字用无衬线（Manrope）；中文落到系统字体
 *   - 层次靠色调和细线，不用圆角卡片：圆角全部为 0
 *   - 朱红只标记动作、选中、进度和错误；冷蓝标记焦点和信息
 *   - 间距用 4dp 网格：4 / 8 / 12 / 16 / 24 / 32
 * 浅色 = Paper，深色 = Stage，跟随系统。阅读页另有一套跟随阅读主题的配色（见下方 ReaderPalette）。
 */

// ======================= 色板 =======================

@Immutable
data class YHColors(
    val bg: Color,
    val surface: Color,
    val fg: Color,
    val muted: Color,
    val subtle: Color,
    val accent: Color,     // 朱红：动作、进度、选中
    val onAccent: Color,
    val cold: Color,       // 冷蓝：焦点、信息
    val line: Color,       // 细线（强）
    val lineSoft: Color,   // 细线（弱）：列表分隔
    val shadow: Color,     // 封面投影颜色
    val dark: Boolean,
)

val PaperColors = YHColors(
    bg = Color(0xFFEEE8DC), surface = Color(0xFFE5DDCE), fg = Color(0xFF211A16),
    muted = Color(0xFF65584D), subtle = Color(0xFF88796B),
    accent = Color(0xFFA02B1D), onAccent = Color(0xFFEEE8DC), cold = Color(0xFF1F4A8F),
    line = Color(0xFFA39483), lineSoft = Color(0xFFCFC2B1), shadow = Color(0xFF2D190C), dark = false,
)

val StageColors = YHColors(
    bg = Color(0xFF0C0A09), surface = Color(0xFF191411), fg = Color(0xFFF1EBDF),
    muted = Color(0xFFB9ADA0), subtle = Color(0xFF83766D),
    accent = Color(0xFFFF6654), onAccent = Color(0xFF0C0A09), cold = Color(0xFF8FB1FF),
    line = Color(0xFF66574B), lineSoft = Color(0xFF3D312A), shadow = Color(0xFF000000), dark = true,
)

val LocalYH = staticCompositionLocalOf { PaperColors }

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
// 字体文件见 INTEGRATION.md：res/font/bodoni_moda.ttf、res/font/manrope.ttf（Google Fonts，OFL）
// 两个都是可变字体，用 variationSettings 取字重（minSdk 26 支持）。

@OptIn(ExperimentalTextApi::class)
private fun bodoni(weight: Int) = Font(
    R.font.bodoni_moda, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", 28f)),
)

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    R.font.manrope, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

object YHFonts {
    val display = FontFamily(bodoni(400), bodoni(500), bodoni(600), bodoni(700))
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
        headlineLarge = TextStyle(fontFamily = YHFonts.display, fontWeight = FontWeight.Medium, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.01).em),
        headlineMedium = t.headlineMedium.display(),
        headlineSmall = TextStyle(fontFamily = YHFonts.display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
        // 书名
        titleLarge = TextStyle(fontFamily = YHFonts.display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp),
        titleMedium = TextStyle(fontFamily = YHFonts.display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
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

private val Square = RoundedCornerShape(0.dp)
private val YHShapes = Shapes(
    extraSmall = Square, small = Square, medium = Square, large = Square, extraLarge = Square,
)

/** 包住整个 App（MainActivity.setContent 里替换原来的主题） */
@Composable
fun YHTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) StageColors else PaperColors
    CompositionLocalProvider(LocalYH provides colors) {
        MaterialTheme(colorScheme = colors.toScheme(), typography = YHTypography, shapes = YHShapes, content = content)
    }
}

// ======================= 阅读页配色 =======================

/**
 * 阅读页的顶栏、底栏、目录抽屉、设置面板跟随**阅读主题**，而不是 App 的浅 / 深色：
 * 终端主题配一个浅灰顶栏会很突兀（之前的截图就是这样）。
 * 颜色和 assets/shared/themes.css 里的同名主题保持一致，改那边时这里也要改（ReaderPaletteTest 会提醒）。
 */
data class ReaderPalette(
    val bg: Long, val surface: Long, val fg: Long, val muted: Long, val line: Long, val accent: Long, val dark: Boolean,
) {
    companion object {
        val PAPER = ReaderPalette(0xFFFAF9F5, 0xFFF0EEE6, 0xFF2B2A27, 0xFF75736C, 0xFFE6E3D9, 0xFFC4633F, dark = false)
        val INK = ReaderPalette(0xFF262624, 0xFF30302D, 0xFFE8E5DC, 0xFFA29F96, 0xFF3B3A36, 0xFFD97757, dark = true)
        val TERMINAL = ReaderPalette(0xFF1D2A32, 0xFF172229, 0xFFD3DAE0, 0xFF7F929E, 0xFF2E3E48, 0xFFE9A23B, dark = true)

        fun of(theme: ReaderTheme, systemDark: Boolean): ReaderPalette = when (theme) {
            ReaderTheme.PAPER -> PAPER
            ReaderTheme.INK -> INK
            ReaderTheme.TERMINAL -> TERMINAL
            ReaderTheme.AUTO -> if (systemDark) INK else PAPER
        }
    }
}

@Composable
fun rememberReaderPalette(theme: ReaderTheme): ReaderPalette = ReaderPalette.of(theme, isSystemInDarkTheme())

/** 阅读页局部主题：只换颜色，字体和形状沿用外层 YHTheme */
@Composable
fun ReaderChromeTheme(palette: ReaderPalette, content: @Composable () -> Unit) {
    val c = YHColors(
        bg = Color(palette.bg), surface = Color(palette.surface), fg = Color(palette.fg),
        muted = Color(palette.muted), subtle = Color(palette.muted), accent = Color(palette.accent),
        onAccent = Color(palette.bg), cold = Color(palette.accent),
        line = Color(palette.line), lineSoft = Color(palette.line), shadow = Color.Black, dark = palette.dark,
    )
    CompositionLocalProvider(LocalYH provides c) {
        MaterialTheme(
            colorScheme = c.toScheme(),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}
