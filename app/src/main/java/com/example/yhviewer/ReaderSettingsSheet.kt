package com.example.yhviewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

/** v13：同一个设置面板用在书和单页文档上，按内容显示不同的项 */
enum class SettingsScope {
    /** TXT / EPUB：书签、主题、字号、行距、页边距、字体、翻页开关（v16 去掉了"进度"：目录可以代替） */
    BOOK,
    /** Markdown：主题、字号、行距、页边距、字体 */
    MARKDOWN,
    /** 代码 / 纯文本：主题、字号、自动换行 */
    CODE,
    /** 原样 HTML：只有字号（网页自带排版，不套主题、不改行距） */
    HTML,
    /** SVG 图片：只有主题 */
    IMAGE,
}

private val GLASS_SHAPE = RoundedCornerShape(24.dp)

/** v15：面板顶端最高到状态栏下方这么远（留出阅读页顶栏的位置） */
private val SHEET_TOP_GAP = 56.dp
/** M3 默认拖拽把手占的高度（上下各 22dp 边距 + 4dp 横线） */
private val DRAG_HANDLE_HEIGHT = 48.dp


/**
 * 阅读设置（从底部弹出）。
 *
 * v13：拖动字号、行距、页边距时，面板其他内容、遮罩、阅读页的顶栏和底部胶囊都隐去，只留正在拖的滑杆，
 * 滑杆变成液态玻璃浮在正文上（见 Glass.kt），正文实时跟着变（onPreview）；松手提交（onReading）并恢复面板。
 * EPUB 单章模式禁用了 JS，改排版要重新加载章节，所以那里只在松手时生效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    theme: ReaderTheme,
    onTheme: (ReaderTheme) -> Unit,
    reading: ReadingPrefs,
    onReading: (ReadingPrefs) -> Unit,
    onDismiss: () -> Unit,
    scope: SettingsScope = SettingsScope.BOOK,
    onPreview: (ReadingPrefs?) -> Unit = {},
    onDraggingChange: (Boolean) -> Unit = {},
    onBookmark: (() -> Unit)? = null,
    wrap: Boolean? = null,
    onWrap: (() -> Unit)? = null,
) {
    var active by remember { mutableStateOf<String?>(null) }
    val dragging = active != null
    val latestOnDragging by rememberUpdatedState(onDraggingChange)
    LaunchedEffect(dragging) { latestOnDragging(dragging) }

    // v17：开始拖动时其他内容隐去得快（120ms），松手后恢复稍慢（160ms），都用强 ease-out
    val chrome by animateFloatAsState(
        if (dragging) 0f else 1f,
        if (dragging) Motion.exit() else Motion.enter(),
        label = "chrome",
    )
    // v14：只对"拖动中"的透明度做动画，颜色本身直接取主题色。以前对颜色做动画，切主题时面板会
    // 比阅读页晚 160ms 渐变过去，看起来像分几块变色
    val scrimBase = BottomSheetDefaults.ScrimColor
    val containerBase = BottomSheetDefaults.ContainerColor
    val scrim = scrimBase.copy(alpha = scrimBase.alpha * chrome)
    val container = containerBase.copy(alpha = containerBase.alpha * chrome)
    val glassBehind = LocalGlassHost.current?.supported == true

    // 一行设置：拖动别的滑杆时隐去（保留占位，正在拖的滑杆不会移动）
    @Composable
    fun SettingRow(key: String?, content: @Composable () -> Unit) {
        // 在 graphicsLayer 里读透明度：淡入淡出时只重绘，不重组整个面板
        Box(Modifier.fillMaxWidth().graphicsLayer { alpha = if (key != null && key == active) 1f else chrome }) { content() }
    }

    @Composable
    fun GlassSlider(
        key: String,
        title: String,
        valueText: (Float) -> String,
        initial: Float,
        range: ClosedFloatingPointRange<Float>,
        steps: Int,
        toPrefs: (Float) -> ReadingPrefs,
    ) {
        var draft by remember(initial) { mutableFloatStateOf(initial) }
        val interaction = remember { MutableInteractionSource() }
        val draggedNow by interaction.collectIsDraggedAsState()
        val pressedNow by interaction.collectIsPressedAsState()
        val touching = draggedNow || pressedNow
        LaunchedEffect(touching) {
            if (touching) active = key else if (active == key) active = null
        }
        val isActive = active == key
        val glass by animateFloatAsState(
            if (isActive) 1f else 0f,
            if (isActive) Motion.enter() else Motion.exit(),
            label = "glass",
        )
        SettingRow(key) {
            Box(Modifier.fillMaxWidth().glassRegion("slider-$key", visible = isActive, radius = 24.dp)) {
                // 玻璃底：只在拖动时出现（淡入），不改变布局
                Box(Modifier.matchParentSize().graphicsLayer { alpha = glass }.liquidGlass(GLASS_SHAPE, glassBehind))
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Label(title, valueText(draft))
                    Slider(
                        modifier = Modifier.semantics { contentDescription = title },
                        value = draft,
                        onValueChange = {
                            if (it != draft) {
                                draft = it
                                onPreview(toPrefs(it))
                            }
                        },
                        valueRange = range,
                        steps = steps,
                        interactionSource = interaction,
                        onValueChangeFinished = { onReading(toPrefs(draft)) },
                    )
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { onPreview(null); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = container,
        scrimColor = scrim,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(Modifier.graphicsLayer { alpha = chrome }.testTag("reader-aa-sheet-handle")) {
                BottomSheetDefaults.DragHandle()
            }
        },
    ) {
        // v15：内容比屏幕高时（EPUB 有"进度"一行，面板比 TXT 高），M3 会把面板一直展开到屏幕顶端、
        // 盖住状态栏，把手上方多出一块空白，看起来像面板在自己往上爬。这里把面板限制在状态栏下方
        // SHEET_TOP_GAP 处，多出的内容在面板里滚动；内容不高时不受影响（和 TXT 一样贴着内容）。
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cap = if (constraints.hasBoundedHeight) {
            (maxHeight - SHEET_TOP_GAP - DRAG_HANDLE_HEIGHT).coerceAtLeast(240.dp)
        } else Dp.Infinity
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = cap)
                .verticalScroll(rememberScrollState(), enabled = !dragging)
                .padding(horizontal = 8.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val pad = Modifier.padding(horizontal = 12.dp)
            if (scope == SettingsScope.BOOK) {
                onBookmark?.let { add ->
                    SettingRow(null) { TextButton(onClick = add, modifier = Modifier.fillMaxWidth()) { Text("添加当前位置书签") } }
                }
            }

            if (scope != SettingsScope.HTML) SettingRow(null) {
                Column(pad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("主题", style = MaterialTheme.typography.titleSmall)
                    ChipRow {
                        ReaderTheme.entries.forEach { t ->
                            FilterChip(selected = t == theme, onClick = { onTheme(t) }, label = { Text(t.label) })
                        }
                    }
                }
            }

            if (scope == SettingsScope.HTML) SettingRow(null) {
                Text(
                    "网页自带排版，这里只调整文字大小。",
                    modifier = pad,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (scope != SettingsScope.IMAGE) {
                GlassSlider(
                    key = "scale", title = "字号", valueText = { "${it.roundToInt()}%" },
                    initial = reading.fontScale.toFloat(),
                    range = ReadingPrefs.MIN_SCALE.toFloat()..ReadingPrefs.MAX_SCALE.toFloat(),
                    steps = (ReadingPrefs.MAX_SCALE - ReadingPrefs.MIN_SCALE) / 10 - 1,   // 每格 10%
                ) { reading.copy(fontScale = it.roundToInt()) }
            }

            if (scope == SettingsScope.BOOK || scope == SettingsScope.MARKDOWN) {
                GlassSlider(
                    key = "line", title = "行距", valueText = { String.format(Locale.ROOT, "%.1f", it) },
                    initial = reading.lineHeight,
                    range = ReadingPrefs.MIN_LINE..ReadingPrefs.MAX_LINE,
                    steps = ((ReadingPrefs.MAX_LINE - ReadingPrefs.MIN_LINE) * 10).roundToInt() - 1,   // 每格 0.1
                ) { reading.copy(lineHeight = (it * 10).roundToInt() / 10f) }

                GlassSlider(
                    key = "margin", title = "页边距", valueText = { "${it.roundToInt()}" },
                    initial = reading.margin.toFloat(),
                    range = ReadingPrefs.MIN_MARGIN.toFloat()..ReadingPrefs.MAX_MARGIN.toFloat(),
                    steps = (ReadingPrefs.MAX_MARGIN - ReadingPrefs.MIN_MARGIN) / 4 - 1,   // 每格 4
                ) { reading.copy(margin = it.roundToInt()) }

                SettingRow(null) {
                    Column(pad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("字体", style = MaterialTheme.typography.titleSmall)
                        ChipRow {
                            ReadingFont.entries.forEach { f ->
                                FilterChip(
                                    selected = f == reading.font,
                                    onClick = { onReading(reading.copy(font = f)) },
                                    label = { Text(f.label) },
                                )
                            }
                        }
                    }
                }
            }

            if (scope == SettingsScope.CODE && wrap != null && onWrap != null) SettingRow(null) {
                Box(pad) { SwitchRow("自动换行", wrap) { onWrap() } }
            }

            if (scope == SettingsScope.BOOK) SettingRow(null) {
                Column(pad, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SwitchRow("章节连续滚动", reading.continuous) { onReading(reading.copy(continuous = it)) }
                    SwitchRow("点击屏幕左右两侧翻页", reading.tapToTurn) { onReading(reading.copy(tapToTurn = it)) }
                    SwitchRow("阅读时屏幕常亮", reading.keepScreenOn) { onReading(reading.copy(keepScreenOn = it)) }
                }
            }
        }
        }
    }
}

@Composable
private fun Label(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
