package com.example.yhviewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(vm: ShelfViewModel) {
    val state = vm.reader
    val doc = (state as? ReaderState.Ready)?.doc
    var menuOpen by remember(doc) { mutableStateOf(false) }
    var tocOpen by remember(doc) { mutableStateOf(false) }

    // 先注册的 BackHandler 优先级低：网页内能后退时 HtmlView 里的那个先处理
    BackHandler { vm.backToShelf() }

    val titles = doc?.let(::chapterTitles).orEmpty()
    val title = titles.getOrNull(vm.chapter) ?: doc?.name.orEmpty()
    val singleType = (doc as? OpenedDoc.Single)?.doc?.type
    val themable = doc != null && singleType != DocType.HTML   // 原样 HTML 不套主题
    val isCode = singleType == DocType.CODE || singleType == DocType.UNKNOWN

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = vm::backToShelf) { Text("‹ 书架") } },
                actions = {
                    if (titles.size > 1) TextButton(onClick = { tocOpen = true }) { Text("目录") }
                    if (themable) {
                        Box {
                            TextButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = "阅读主题" }) { Text("Aa") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                ReaderTheme.entries.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text((if (t == vm.theme) "✓ " else "    ") + t.label) },
                                        onClick = { vm.selectTheme(t); menuOpen = false },
                                    )
                                }
                                if (isCode) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text((if (vm.wrap) "✓ " else "    ") + "自动换行") },
                                        onClick = { vm.toggleWrap(); menuOpen = false },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (titles.size > 1) {
                ChapterBar(
                    index = vm.chapter,
                    count = titles.size,
                    onPrev = vm::prevChapter,
                    onNext = vm::nextChapter,
                )
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding).fillMaxSize()
        when (state) {
            ReaderState.Loading -> Box(m, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is ReaderState.Error -> CenterText("打开失败：${state.message}", m)
            is ReaderState.Ready -> ReaderContent(vm, state.doc, m)
        }
    }

    if (tocOpen && titles.isNotEmpty()) {
        TocDialog(
            titles = titles,
            current = vm.chapter,
            onPick = { vm.goToChapter(it); tocOpen = false },
            onDismiss = { tocOpen = false },
        )
    }
}

private fun chapterTitles(doc: OpenedDoc): List<String> = when (doc) {
    is OpenedDoc.Txt -> doc.chapters.map { it.title }
    is OpenedDoc.Epub -> doc.chapters.map { it.title }
    is OpenedDoc.Single -> emptyList()
}

@Composable
private fun ReaderContent(vm: ShelfViewModel, doc: OpenedDoc, modifier: Modifier) {
    val context = LocalContext.current
    val session = vm.documentSession
    when (doc) {
        is OpenedDoc.Single -> key(session, doc.bookId) {
            val html = remember(doc) { doc.doc.toDisplayHtml(context, vm.theme, vm.wrap) }
            HtmlView(
                source = PageSource.Html(html),
                modifier = modifier,
                theme = vm.theme.takeIf { doc.doc.type != DocType.HTML },
                wrap = vm.wrap.takeIf { doc.doc.type in setOf(DocType.CODE, DocType.UNKNOWN) },
                initialRatio = vm.initialRatio(),
                onRatioChanged = { vm.onScroll(session, 0, it) },
            )
        }

        // 一章一个 WebView：几十 MB 的小说一次性塞进 DOM 会很卡。主题通过 JS 实时切换，不重新加载
        is OpenedDoc.Txt -> {
            val index = vm.chapter
            key(session, doc.bookId, index) {
                val html = remember(doc, index) {
                    TxtRenderer.render(doc.chapters[index], index, doc.chapters.size, Templates.get(context, Templates.TXT), vm.theme)
                }
                HtmlView(
                    source = PageSource.Html(html),
                    modifier = modifier,
                    theme = vm.theme,
                    initialRatio = vm.initialRatio(),
                    onRatioChanged = { vm.onScroll(session, index, it) },
                )
            }
        }

        // EPUB 章节不允许 JS，换主题时以当前位置重新加载这一章（key 里带上 theme）
        is OpenedDoc.Epub -> {
            val index = vm.chapter
            key(session, doc.bookId, index, vm.theme) {
                HtmlView(
                    source = PageSource.BookFile(doc.rootDir, doc.chapters[index].path, vm.chapterAnchor),
                    modifier = modifier,
                    initialRatio = vm.initialRatio(),
                    onRatioChanged = { vm.onScroll(session, index, it) },
                    onInternalLink = vm::onBookLink,
                    bookTheme = { vm.theme },
                )
            }
        }
    }
}

@Composable
private fun ChapterBar(index: Int, count: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    BottomAppBar { // 自带导航栏 inset 处理
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrev, enabled = index > 0) { Text("‹ 上一章") }
            Text(
                "${index + 1} / $count",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
            )
            TextButton(onClick = onNext, enabled = index < count - 1) { Text("下一章 ›") }
        }
    }
}

@Composable
private fun TocDialog(titles: List<String>, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    // 打开时直接定位到当前章节附近
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("目录（${titles.size} 章）") },
        text = {
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 480.dp)) {
                itemsIndexed(titles) { i, t ->
                    Text(
                        t,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(i) }
                            .padding(vertical = 10.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
