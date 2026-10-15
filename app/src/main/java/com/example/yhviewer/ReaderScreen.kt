package com.example.yhviewer

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.ViewCompat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 阅读页分两种布局：
 *  - 单页文档（HTML / MD / 代码）：普通的顶栏 + 内容，和之前一样
 *  - 书（TXT / EPUB）：沉浸式。正文铺满屏幕，点中间才浮出顶栏和底栏，系统状态栏也一起隐藏
 */
@Composable
fun ReaderScreen(vm: ShelfViewModel) {
    BackHandler { vm.backToShelf() } // 先注册 = 优先级低：网页内能后退时 HtmlView 里的那个先处理

    val state = vm.reader
    val doc = (state as? ReaderState.Ready)?.doc
    if (doc is OpenedDoc.Txt || doc is OpenedDoc.Epub) {
        BookReader(vm, doc)
    } else {
        PageReader(vm, state)
    }
}

// ======================= 单页文档 =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageReader(vm: ShelfViewModel, state: ReaderState) {
    val doc = (state as? ReaderState.Ready)?.doc as? OpenedDoc.Single
    val type = doc?.doc?.type
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(doc?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = vm::backToShelf) { Text("‹ 书架") } },
                actions = {
                    if (doc != null && type != DocType.HTML) { // 原样 HTML 不套主题
                        Box {
                            TextButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = "阅读主题" }) { Text("Aa") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                ReaderTheme.entries.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text((if (t == vm.theme) "✓ " else "    ") + t.label) },
                                        onClick = { vm.selectTheme(t); menuOpen = false },
                                    )
                                }
                                if (type == DocType.CODE || type == DocType.UNKNOWN) {
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
    ) { padding ->
        val m = Modifier.padding(padding).fillMaxSize()
        when (state) {
            ReaderState.Loading -> Box(m, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is ReaderState.Error -> CenterText("打开失败：${state.message}", m)
            is ReaderState.Ready -> if (doc != null) SingleContent(vm, doc, m)
        }
    }
}

@Composable
private fun SingleContent(vm: ShelfViewModel, doc: OpenedDoc.Single, modifier: Modifier) {
    val context = LocalContext.current
    val session = vm.documentSession
    key(vm.documentSession, doc.bookId) {
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
}

// ======================= 书（TXT / EPUB） =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookReader(vm: ShelfViewModel, doc: OpenedDoc) {
    val titles = chapterTitles(doc)
    var barsVisible by remember { mutableStateOf(true) }
    var settingsOpen by remember { mutableStateOf(false) }
    var tocOpen by remember { mutableStateOf(false) }

    ImmersiveSystemBars(hidden = !barsVisible)
    KeepScreenOn(vm.reading.keepScreenOn)

    val gestures = ReaderGestures(
        tapToTurn = vm.reading.tapToTurn,
        onToggleBars = { barsVisible = !barsVisible },
        onReachEnd = vm::nextChapter,
        onReachStart = vm::prevChapterFromStart,
    )

    Box(Modifier.fillMaxSize()) {
        // 正文铺满全屏；只避开刘海挖孔。不随状态栏显隐改变内边距，否则正文会跳动
        BookContent(
            vm = vm,
            doc = doc,
            gestures = gestures,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout),
        )

        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = { Text(titles.getOrNull(vm.chapter) ?: doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = vm::backToShelf) { Text("‹ 书架") } },
                actions = {
                    if (titles.size > 1) TextButton(onClick = { tocOpen = true }) { Text("目录") }
                    TextButton(onClick = { settingsOpen = true }, modifier = Modifier.semantics { contentDescription = "阅读设置" }) { Text("Aa") }
                },
            )
        }

        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ChapterBar(
                index = vm.chapter,
                count = titles.size,
                onPrev = vm::prevChapter,
                onNext = vm::nextChapter,
            )
        }
    }

    if (settingsOpen) {
        ReaderSettingsSheet(
            theme = vm.theme,
            onTheme = vm::selectTheme,
            reading = vm.reading,
            onReading = vm::updateReading,
            chapter = vm.chapter,
            chapterCount = titles.size,
            onChapter = { vm.goToChapter(it) },
            onDismiss = { settingsOpen = false },
        )
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

@Composable
private fun BookContent(vm: ShelfViewModel, doc: OpenedDoc, gestures: ReaderGestures, modifier: Modifier) {
    val context = LocalContext.current
    val session = vm.documentSession
    val index = vm.chapter
    when (doc) {
        // TXT：主题和排版都通过 JS 实时切换，不重新加载
        is OpenedDoc.Txt -> key(vm.documentSession, doc.bookId, index) {
            val html = remember(doc, index) {
                TxtRenderer.render(
                    doc.chapters[index], index, doc.chapters.size,
                    Templates.get(context, Templates.TXT), vm.theme, vm.reading,
                )
            }
            HtmlView(
                source = PageSource.Html(html),
                modifier = modifier,
                theme = vm.theme,
                reading = vm.reading.toCss(),
                initialRatio = vm.initialRatio(),
                onRatioChanged = { vm.onScroll(session, index, it) },
                gestures = gestures,
            )
        }

        // EPUB：章节禁用 JS，主题或排版变化时以当前位置重新加载本章（key 里带上两者）
        is OpenedDoc.Epub -> key(vm.documentSession, doc.bookId, index, vm.theme, vm.reading.toCss()) {
            HtmlView(
                source = PageSource.BookFile(doc.rootDir, doc.chapters[index].path, vm.chapterAnchor),
                modifier = modifier,
                initialRatio = vm.initialRatio(),
                onRatioChanged = { vm.onScroll(session, index, it) },
                onInternalLink = vm::onBookLink,
                bookTheme = { vm.theme },
                bookReadingCss = { vm.reading.toCss() },
                gestures = gestures,
            )
        }

        is OpenedDoc.Single -> Unit
    }
}

/** 隐藏 / 恢复系统状态栏和导航栏；离开阅读页时一定恢复 */
@Composable
private fun ImmersiveSystemBars(hidden: Boolean) {
    val view = LocalView.current
    val window = (LocalContext.current as? Activity)?.window ?: return
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(window, view) {
        val insets = ViewCompat.getRootWindowInsets(view)
        val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        val behavior = controller.systemBarsBehavior
        onDispose {
            controller.systemBarsBehavior = behavior
            if (statusVisible) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
            if (navVisible) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
    LaunchedEffect(hidden, controller) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }
    LaunchedEffect(view, on) { view.keepScreenOn = on }
}

private fun chapterTitles(doc: OpenedDoc): List<String> = when (doc) {
    is OpenedDoc.Txt -> doc.chapters.map { it.title }
    is OpenedDoc.Epub -> doc.chapters.map { it.title }
    is OpenedDoc.Single -> emptyList()
}

@Composable
private fun ChapterBar(index: Int, count: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    BottomAppBar { // 自带导航栏 inset 处理
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrev, enabled = index > 0) { Text("‹ 上一章") }
            Text(
                if (count > 1) "${index + 1} / $count" else "",
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
