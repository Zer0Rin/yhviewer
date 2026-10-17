package com.example.yhviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DrawerValue

/** 章节列表的一行：原始序号 + 标题。筛选后序号不变，点哪行就跳哪章 */
internal data class TocEntry(val index: Int, val title: String)

/**
 * 筛选目录：输入数字按章节号匹配（"12" → 第 12 章，也匹配标题里含 12 的），其他按标题包含匹配，忽略大小写。
 * 纯函数，JVM 可测。
 */
internal fun filterToc(titles: List<String>, query: String): List<TocEntry> {
    val q = query.trim()
    val all = titles.mapIndexed { i, t -> TocEntry(i, t) }
    if (q.isEmpty()) return all
    val n = q.toIntOrNull()
    return all.filter { (i, t) -> (n != null && i + 1 == n) || t.contains(q, ignoreCase = true) }
}

/** 超过这个章数才显示筛选框；短书一屏就能看完 */
internal const val TOC_FILTER_THRESHOLD = 20

/**
 * 目录抽屉的内容（放进 ModalNavigationDrawer 的 drawerContent）。
 * 打开时自动滚到当前章，当前章用朱红竖线 + 加粗标出，已读过的章节颜色变浅。
 */
@Composable
fun ChapterDrawerContent(
    drawerState: DrawerState,
    bookTitle: String,
    titles: List<String>,
    current: Int,
    onPick: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val yh = LocalYH.current
    var query by rememberSaveable { mutableStateOf("") }
    val entries = remember(titles, query) { filterToc(titles, query) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // 每次开始打开：清空筛选，滚到当前章上方 3 行，让当前章落在视野中上部
    val opening = drawerState.targetValue == DrawerValue.Open
    LaunchedEffect(opening, current, titles.size) {
        if (!opening) {
            // Drawer content stays composed offscreen; its filter must release IME/focus.
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        } else {
            query = ""
            // Wait until the cleared query has been composed into the list.
            androidx.compose.runtime.withFrameNanos { }
            if (titles.isNotEmpty()) listState.scrollToItem((current - 3).coerceIn(0, titles.lastIndex))
        }
    }

    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.84f)
            .background(yh.surface)
            .drawBehind { // 右侧一条细线代替阴影
                // Keep the entire border inside the drawer: centered strokes at size.width
                // otherwise bleed onto the reader's left edge when this sheet is offscreen.
                val border = 1.dp.toPx()
                drawRect(
                    color = yh.line,
                    topLeft = Offset(size.width - border, 0f),
                    size = androidx.compose.ui.geometry.Size(border, size.height),
                )
            },
    ) {
        // ---------- 头部：书名 + 全书进度 ----------
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("目录 · ${titles.size} 章", style = MaterialTheme.typography.labelSmall, color = yh.subtle)
            Text(bookTitle, style = MaterialTheme.typography.titleLarge, color = yh.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ProgressLine(fraction = (current + 1f) / titles.size.coerceAtLeast(1))
            Text(
                "读到第 ${current + 1} 章，共 ${titles.size} 章",
                style = MaterialTheme.typography.bodySmall,
                color = yh.muted,
            )
        }
        HorizontalDivider(color = yh.line)

        if (titles.size > TOC_FILTER_THRESHOLD) {
            UnderlineField(
                value = query,
                onValueChange = { query = it },
                placeholder = "输入章节号或标题跳转",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        // ---------- 章节列表 ----------
        Box(Modifier.weight(1f)) {
            if (entries.isEmpty()) {
                Text(
                    "没有匹配的章节",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    color = yh.muted,
                )
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.index }) { e ->
                    TocRow(e, isCurrent = e.index == current, isRead = e.index < current, onClick = { onPick(e.index) })
                }
            }
        }

        // ---------- 底部：上一章 / 下一章 ----------
        HorizontalDivider(color = yh.line)
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPrev, enabled = current > 0) { Text("‹ 上一章") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onNext, enabled = current < titles.size - 1) { Text("下一章 ›") }
        }
    }
}

@Composable
private fun TocRow(e: TocEntry, isCurrent: Boolean, isRead: Boolean, onClick: () -> Unit) {
    val yh = LocalYH.current
    Row(
        Modifier
            .fillMaxWidth()
            .semantics { selected = isCurrent }
            .then(if (isCurrent) Modifier.background(yh.bg) else Modifier)
            .drawBehind {
                if (isCurrent) drawRect(yh.accent, size = size.copy(width = 3.dp.toPx()))
            }
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${e.index + 1}",
            modifier = Modifier.width(32.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (isCurrent) yh.accent else yh.subtle,
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            e.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isCurrent -> yh.fg
                isRead -> yh.muted
                else -> yh.fg
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (isCurrent) {
            Spacer(Modifier.width(8.dp))
            Text("在读", style = MaterialTheme.typography.labelSmall, color = yh.accent)
        }
    }
}

/** 2dp 细进度线：底色弱细线，已读部分朱红 */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    val yh = LocalYH.current
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(yh.lineSoft)
            .drawBehind { drawRect(yh.accent, size = size.copy(width = size.width * f)) },
    )
}

/** 只有一条底线的输入框（Overture 风格：不用圆角描边框） */
@Composable
fun UnderlineField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val yh = LocalYH.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = yh.fg),
        cursorBrush = SolidColor(yh.cold),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(yh.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⌕", color = yh.subtle, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = yh.subtle, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Text(
                        "清除",
                        modifier = Modifier.clickable { onValueChange("") }.padding(start = 8.dp),
                        color = yh.muted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
    )
}
