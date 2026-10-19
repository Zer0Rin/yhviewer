package com.example.yhviewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 长文本阅读设置（从底部弹出）。
 *
 * 滑杆拖动时只更新面板里的数字，松手才提交：
 * TXT 可以实时改 CSS，但 EPUB 章节禁用了 JS，每次提交都要重新加载章节，拖动中逐帧提交会一直闪。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    theme: ReaderTheme,
    onTheme: (ReaderTheme) -> Unit,
    reading: ReadingPrefs,
    onReading: (ReadingPrefs) -> Unit,
    chapter: Int,
    chapterCount: Int,
    onChapter: (Int) -> Unit,
    onDismiss: () -> Unit,
    onBookmark: (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            onBookmark?.let { add ->
                TextButton(onClick = add, modifier = Modifier.fillMaxWidth()) { Text("添加当前位置书签") }
            }
            if (chapterCount > 1) {
                var draft by remember(chapter) { mutableFloatStateOf(chapter.toFloat()) }
                Label("进度", "第 ${draft.roundToInt() + 1} / $chapterCount 章")
                Slider(
                    modifier = Modifier.semantics { contentDescription = "章节进度" },
                    value = draft,
                    onValueChange = { draft = it },
                    valueRange = 0f..(chapterCount - 1).toFloat(),
                    steps = (chapterCount - 2).coerceAtLeast(0),
                    onValueChangeFinished = { onChapter(draft.roundToInt()) },
                )
            }

            Text("主题", style = MaterialTheme.typography.titleSmall)
            ChipRow {
                ReaderTheme.entries.forEach { t ->
                    FilterChip(selected = t == theme, onClick = { onTheme(t) }, label = { Text(t.label) })
                }
            }

            var scale by remember(reading.fontScale) { mutableFloatStateOf(reading.fontScale.toFloat()) }
            Label("字号", "${scale.roundToInt()}%")
            Slider(
                modifier = Modifier.semantics { contentDescription = "字号" },
                value = scale,
                onValueChange = { scale = it },
                valueRange = ReadingPrefs.MIN_SCALE.toFloat()..ReadingPrefs.MAX_SCALE.toFloat(),
                steps = (ReadingPrefs.MAX_SCALE - ReadingPrefs.MIN_SCALE) / 10 - 1,   // 每格 10%
                onValueChangeFinished = { onReading(reading.copy(fontScale = scale.roundToInt())) },
            )

            var line by remember(reading.lineHeight) { mutableFloatStateOf(reading.lineHeight) }
            Label("行距", String.format(Locale.ROOT, "%.1f", line))
            Slider(
                modifier = Modifier.semantics { contentDescription = "行距" },
                value = line,
                onValueChange = { line = it },
                valueRange = ReadingPrefs.MIN_LINE..ReadingPrefs.MAX_LINE,
                steps = ((ReadingPrefs.MAX_LINE - ReadingPrefs.MIN_LINE) * 10).roundToInt() - 1,   // 每格 0.1
                onValueChangeFinished = { onReading(reading.copy(lineHeight = (line * 10).roundToInt() / 10f)) },
            )

            var margin by remember(reading.margin) { mutableFloatStateOf(reading.margin.toFloat()) }
            Label("页边距", "${margin.roundToInt()}")
            Slider(
                modifier = Modifier.semantics { contentDescription = "页边距" },
                value = margin,
                onValueChange = { margin = it },
                valueRange = ReadingPrefs.MIN_MARGIN.toFloat()..ReadingPrefs.MAX_MARGIN.toFloat(),
                steps = (ReadingPrefs.MAX_MARGIN - ReadingPrefs.MIN_MARGIN) / 4 - 1,   // 每格 4
                onValueChangeFinished = { onReading(reading.copy(margin = margin.roundToInt())) },
            )

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

            SwitchRow("点击屏幕左右两侧翻页", reading.tapToTurn) { onReading(reading.copy(tapToTurn = it)) }
            SwitchRow("阅读时屏幕常亮", reading.keepScreenOn) { onReading(reading.copy(keepScreenOn = it)) }
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
