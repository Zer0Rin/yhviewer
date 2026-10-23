package com.example.yhviewer

import android.app.Activity
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import android.net.Uri
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.background
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
    if (state is ReaderState.Loading) {
        // v17：加载期间只显示主题底色，超过 300ms 才出现转圈。以前加载时显示的是单页文档的界面
        // （顶栏 + 转圈），书加载完突然换成全屏阅读页，布局会跳；加载很快时转圈也会一闪而过
        ReaderLoading(onBack = vm::backToShelf)
        return
    }
    if (doc is OpenedDoc.Txt || doc is OpenedDoc.Epub) {
        val palette = rememberReaderPalette(vm.theme)
        ReaderChromeTheme(palette) { BookReader(vm, doc, palette) }
    } else {
        PageReader(vm, state)
    }
}

@Composable
private fun ReaderLoading(onBack: () -> Unit) {
    val yh = LocalYH.current
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(Motion.SPINNER_DELAY_MS); slow = true }
    Box(Modifier.fillMaxSize().background(yh.bg).windowInsetsPadding(WindowInsets.statusBars)) {
        AnimatedVisibility(visible = slow, enter = fadeIn(Motion.enter()), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                TextButton(onClick = onBack, modifier = Modifier.padding(4.dp)) { Text("‹ 书架") }
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

private data class RunRequest(val code: String, val fileName: String, val lang: CodeRunner.Lang)

// ======================= 单页文档 =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageReader(vm: ShelfViewModel, state: ReaderState) {
    val doc = (state as? ReaderState.Ready)?.doc as? OpenedDoc.Single
    val type = doc?.doc?.type
    if (vm.editorSession != null && doc != null) {
        EditorScreen(vm, doc) {}
        return
    }
    var settingsOpen by remember { mutableStateOf(false) }
    var permissionsOpen by remember { mutableStateOf(false) }
    // v13：Aa 面板（字号等），拖动滑杆时顶栏隐去；Markdown 页面能做背景模糊
    var sliderDragging by remember { mutableStateOf(false) }
    val glass = remember { GlassHost() }
    val isMarkdown = type == DocType.MARKDOWN
    SideEffect { glass.supported = isMarkdown }
    // Retain the source across reader errors; renderer loss can affect both WebViews.
    var runRequest by remember(vm.documentSession) { mutableStateOf<RunRequest?>(null) }
    // v10：页内查找、书签、缺失资源提示
    val handle = remember { WebViewHandle() }
    val find = remember(vm.documentSession) { FindState() }
    var moreOpen by remember { mutableStateOf(false) }
    var bookmarksOpen by remember { mutableStateOf(false) }
    var missingDismissed by remember(vm.documentSession) { mutableStateOf(false) }
    val bookmarks by remember(doc?.bookId) {
        doc?.let { vm.bookmarks(it.bookId) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    BackHandler(enabled = find.open) { find.open = false; find.lastSearched = null; handle.clearFind() }

    GlassBridge(glass, handle)
    val chromeAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (sliderDragging) 0f else 1f,
        if (sliderDragging) Motion.exit() else Motion.enter(),
        label = "chrome",
    )
    CompositionLocalProvider(LocalGlassHost provides glass) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // 拖动滑杆时顶栏只做透明，不移除（移除会改变正文区域高度，正文会跳）
        topBar = { Column(Modifier.graphicsLayer { alpha = chromeAlpha }) {
            if (find.open) {
                FindBar(find, handle, onClose = { find.open = false; find.lastSearched = null; handle.clearFind() }, onBookSearch = null)
            } else TopAppBar(
                // 文件名不是书名：用界面字体，不用标题衬线
                title = { Text(doc?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall) },
                navigationIcon = { TextButton(onClick = vm::backToShelf) { Text("‹ 书架") } },
                actions = {
                    val runLang = doc?.let { CodeRunner.runnableLang(CodeLanguages.forFileName(it.name), AppCapabilities.pythonAvailable) }
                    if (doc != null && type == DocType.CODE && runLang != null) {
                        TextButton(onClick = { runRequest = RunRequest(doc.doc.text, doc.name, runLang) }) { Text("▶ 运行") }
                    }
                    if (doc != null && EditorRenderer.canEdit(doc.doc.text)) {
                        TextButton(onClick = { vm.startEditor(doc) }) { Text("编辑") }
                    }
                    if (doc != null && type == DocType.HTML) {
                        if (AppCapabilities.effective(doc.permissions).isRelaxed) Text("已放宽", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { permissionsOpen = true }) { Text("权限") }
                    }
                    if (doc != null) TextButton(onClick = { find.open = true }) { Text("搜索") }
                    if (doc != null) Box {
                        TextButton(onClick = { moreOpen = true }, modifier = Modifier.semantics { contentDescription = "更多" }) { Text("⋯") }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            DropdownMenuItem(text = { Text("书签（${bookmarks.size}）") }, onClick = { moreOpen = false; bookmarksOpen = true })
                            if (doc.doc.isSvg) {
                                DropdownMenuItem(
                                    text = { Text(if (vm.svgSource) "显示图片" else "查看源码") },
                                    onClick = { moreOpen = false; vm.toggleSvgSource() },
                                )
                            }
                        }
                    }
                    // v13：所有单页文档都有 Aa：Markdown 可调字号、行距、页边距、字体；代码可调字号；HTML 只调字号
                    if (doc != null) {
                        TextButton(onClick = { settingsOpen = true }, modifier = Modifier.semantics { contentDescription = "阅读主题" }) { Text("Aa") }
                    }
                },
            )
            // 顶栏和正文同色（都跟随阅读主题），用一条细线分开
            HorizontalDivider(color = LocalYH.current.lineSoft)
            if (vm.missingResources.isNotEmpty() && !missingDismissed) {
                ReaderNotice(
                    MissingResources.message(vm.missingResources, bundled = doc?.bundle != null),
                    onClose = { missingDismissed = true },
                )
            }
        } },
    ) { padding ->
        val m = Modifier.padding(padding).fillMaxSize()
        when (state) {
            ReaderState.Loading -> Box(m, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is ReaderState.Error -> CenterText("打开失败：${state.message}", m)
            is ReaderState.Ready -> if (doc != null) SingleContent(vm, doc, m, handle, find, onPageReady = glass::pageLoaded)
        }
    }
    if (settingsOpen && doc != null) {
        val scope = when {
            doc.doc.isSvg && !vm.svgSource -> SettingsScope.IMAGE
            type == DocType.HTML -> SettingsScope.HTML
            type == DocType.MARKDOWN -> SettingsScope.MARKDOWN
            else -> SettingsScope.CODE
        }
        ReaderSettingsSheet(
            theme = vm.theme,
            onTheme = vm::selectTheme,
            reading = vm.docReading,
            onReading = vm::updateDocReading,
            onPreview = vm::previewDocReading,
            onDraggingChange = { sliderDragging = it },
            onDismiss = { settingsOpen = false; sliderDragging = false },
            scope = scope,
            wrap = vm.wrap.takeIf { scope == SettingsScope.CODE },
            onWrap = vm::toggleWrap,
        )
    }
    }
    runRequest?.let { request ->
        RunSheet(request.code, request.fileName, vm.theme, lang = request.lang, onDismiss = { runRequest = null })
    }
    if (bookmarksOpen && doc != null) {
        BookmarkSheet(
            bookmarks = bookmarks,
            onAdd = { done -> vm.addBookmark(null, done) },
            onPick = { bookmarksOpen = false; vm.jumpTo(0, it.ratio) },
            onDelete = { vm.deleteBookmark(it.id) },
            onDismiss = { bookmarksOpen = false },
        )
    }
    if (permissionsOpen && doc != null && type == DocType.HTML) {
        PermissionSheet(doc.permissions, { vm.setPermissions(doc.bookId, it) }, { permissionsOpen = false })
    }
}

@Composable
private fun SingleContent(
    vm: ShelfViewModel,
    doc: OpenedDoc.Single,
    modifier: Modifier,
    handle: WebViewHandle,
    find: FindState,
    onPageReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val session = vm.documentSession
    val isHtml = doc.doc.type == DocType.HTML
    key(vm.documentSession, doc.bookId, doc.permissions, vm.jumpTick, vm.svgSource) {
        val isMarkdown = doc.doc.type == DocType.MARKDOWN
        val html = remember(doc, vm.svgSource) {
            doc.doc.toDisplayHtml(context, vm.theme, vm.wrap, vm.svgSource, vm.docReading.toCss().takeIf { isMarkdown }.orEmpty())
        }
        // v10：文件夹导入的 HTML / Markdown 挂在 /bundle/ 下，相对路径的资源可用
        val source = doc.bundle?.let { PageSource.BundleFile(it.rootDir, it.relPath, html) } ?: PageSource.Html(html)
        HtmlView(
            source = source,
            modifier = modifier,
            theme = vm.theme.takeIf { !isHtml },
            wrap = vm.wrap.takeIf { doc.doc.type in setOf(DocType.CODE, DocType.UNKNOWN) && !doc.doc.isSvg },
            initialRatio = vm.initialRatio(),
            onRatioChanged = { vm.onScroll(session, 0, it) },
            onRendererGone = { vm.onRendererGone(session, 0) },
            permissions = if (isHtml) doc.permissions else DocPermissions.STANDARD,
            storageProfileId = doc.bookId.takeIf { isHtml },
            handle = handle,
            onPageLoading = { find.pageLoading(); handle.clearFind() },
            onPageReady = { find.replay(handle); onPageReady() },
            // v13：Markdown 用 CSS 变量（字号、行距、页边距、字体，实时生效）；HTML 和代码用 WebView 文字缩放（只改字号，不动网页布局）
            reading = vm.liveDocReading.toCss().takeIf { isMarkdown },
            textZoom = vm.liveDocReading.fontScale.takeIf { !isMarkdown && !(doc.doc.isSvg && !vm.svgSource) },
            onMissingLocal = { if (session == vm.documentSession) vm.reportMissing(it) },
            dialogSource = doc.name,
            // v17：我们自己的模板用主题底色，新建 WebView 时不白闪；原样 HTML 保持白底
            background = LocalYH.current.bg.toArgb().takeIf { !isHtml },
        )
    }
}

// ======================= 书（TXT / EPUB） =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookReader(vm: ShelfViewModel, doc: OpenedDoc, palette: ReaderPalette) {
    // v17：按书缓存。以前每次重组都重新生成（1415 章就是 1415 项），连续滚动换章时每次都要重建
    val titles = remember(doc) { chapterTitles(doc) }
    var barsVisible by remember { mutableStateOf(true) }
    var settingsOpen by remember { mutableStateOf(false) }
    // v13：拖动 Aa 里的滑杆时隐去顶栏和底部胶囊；液态玻璃（Glass.kt）
    var sliderDragging by remember { mutableStateOf(false) }
    val glass = remember { GlassHost() }
    // EPUB 单章模式禁用 JS，页面做不了背景模糊
    val glassSupported = vm.reading.continuous || doc is OpenedDoc.Txt
    SideEffect { glass.supported = glassSupported }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    // v10：页内查找、全书搜索、书签
    val handle = remember { WebViewHandle() }
    val find = remember(vm.documentSession) { FindState() }
    var bookQuery by remember { mutableStateOf<String?>(null) }
    var notice by remember(vm.documentSession) {
        mutableStateOf(if ((doc as? OpenedDoc.Epub)?.vertical == true) "这本书原本是竖排，已转为横排阅读。" else null)
    }
    val bookmarks by remember(doc.bookId) { vm.bookmarks(doc.bookId) }.collectAsState(initial = emptyList())
    BackHandler(enabled = find.open) { find.open = false; find.lastSearched = null; handle.clearFind() }
    LaunchedEffect(notice) { if (notice != null) { kotlinx.coroutines.delay(4_000); notice = null } }
    // 搜索结果跳转：新章节加载完后高亮，并跳到本章第 n 个命中
    val onPageReady: () -> Unit = {
        val pending = vm.pendingFind
        vm.consumePendingFind()
        find.replay(handle, pending)
        glass.pageLoaded()
    }
    val onPageLoading: () -> Unit = { find.pageLoading(); handle.clearFind() }
    GlassBridge(glass, handle)

    ImmersiveSystemBars(hidden = !barsVisible, darkIcons = !palette.dark)
    KeepScreenOn(vm.reading.keepScreenOn)

    // v11：连续滚动时滑到底不再翻章（下一章已经接在后面）
    val flow = vm.reading.continuous
    val gestures = ReaderGestures(
        tapToTurn = vm.reading.tapToTurn,
        onToggleBars = {
            if (barsVisible) {
                // Clear both webpage blur layers at the same time the toolbars fade out.
                glass.set("top-bar", null)
                glass.set("chapter-bar", null)
            }
            barsVisible = !barsVisible
        },
        onReachEnd = if (flow) ({}) else vm::nextChapter,
        onReachStart = if (flow) ({}) else vm::prevChapterFromStart,
    )

    CompositionLocalProvider(LocalGlassHost provides glass) {
    // v7: keep edge gestures for page turning/system back until explicitly opened.
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ChapterDrawerContent(
                drawerState = drawerState,
                bookTitle = doc.name,
                titles = titles,
                current = vm.chapter,
                onPick = { vm.goToChapter(it); scope.launch { drawerState.close() } },
                onPrev = vm::prevChapter,
                onNext = vm::nextChapter,
                bookmarks = bookmarks,
                onBookmark = { b -> vm.jumpTo(b.chapter, b.ratio); scope.launch { drawerState.close() } },
                onDeleteBookmark = { vm.deleteBookmark(it.id) },
            )
        },
    ) {
        Box(Modifier.fillMaxSize().background(Color(palette.bg)).testTag("BookReaderBackground")) {
            // 正文铺满全屏；只避开刘海挖孔。不随状态栏显隐改变内边距，否则正文会跳动
            BookContent(
                vm = vm,
                doc = doc,
                gestures = gestures,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout),
                handle = handle,
                onPageReady = onPageReady,
                onPageLoading = onPageLoading,
                find = find,
            )

            // v16：顶栏也是液态玻璃，和底部胶囊一样只淡入淡出，页面里的模糊区域能和它对齐
            AnimatedVisibility(
                visible = (barsVisible || find.open) && !sliderDragging,
                enter = fadeIn(Motion.enter()),
                exit = fadeOut(Motion.exit()),
                modifier = Modifier.align(Alignment.TopCenter),
            ) { Column {
                if (find.open) FindBar(
                    find, handle,
                    onClose = { find.open = false; find.lastSearched = null; handle.clearFind() },
                    onBookSearch = { q -> bookQuery = q.trim() },
                ) else TopAppBar(
                    title = {
                        Text(
                            titles.getOrNull(vm.chapter) ?: doc.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium, // 章节名用标题衬线，和正文一致
                        )
                    },
                    navigationIcon = { TextButton(onClick = vm::backToShelf) { Text("‹ 书架") } },
                    // 顶栏铺到状态栏下面：整块是玻璃（直角，网页里的模糊区域也是直角）
                    modifier = Modifier
                        .testTag("book-top-bar-glass")
                        .glassRegion("top-bar", visible = barsVisible && !sliderDragging && !find.open, radius = 0.dp)
                        .liquidGlass(RectangleShape, blurBehind = glassSupported),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                    actions = {
                        TextButton(onClick = { scope.launch { drawerState.open() } }) { Text("目录") }
                        TextButton(onClick = { find.open = true }) { Text("搜索") }
                        TextButton(onClick = { settingsOpen = true }, modifier = Modifier.semantics { contentDescription = "阅读设置" }) { Text("Aa") }
                    },
                )
                notice?.let { ReaderNotice(it, onClose = { notice = null }) }
            } }

            // v13：底部胶囊是液态玻璃，只淡入淡出（不滑动），页面里的模糊区域能和它对齐
            AnimatedVisibility(
                visible = barsVisible && !sliderDragging,
                enter = fadeIn(Motion.enter()),
                exit = fadeOut(Motion.exit()),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ChapterBar(
                    index = vm.chapter,
                    count = titles.size,
                    glassVisible = barsVisible && !sliderDragging,
                    onPrev = vm::prevChapter,
                    onNext = vm::nextChapter,
                )
            }
        }

    }

    bookQuery?.let { q ->
        BookSearchSheet(
            query = q,
            titles = titles,
            search = vm::searchBook,
            onPick = { hit, nth -> bookQuery = null; vm.openHit(hit, q, nth) },
            onDismiss = { bookQuery = null },
        )
    }

    if (settingsOpen) {
        ReaderSettingsSheet(
            theme = vm.theme,
            onTheme = vm::selectTheme,
            reading = vm.reading,
            onReading = vm::updateReading,
            onPreview = vm::previewReading,
            onDraggingChange = { sliderDragging = it },
            onDismiss = { settingsOpen = false; sliderDragging = false },
            onBookmark = {
                settingsOpen = false
                val title = titles.getOrNull(vm.chapter)
                val session = vm.documentSession
                vm.addBookmark(title) { result ->
                    if (session == vm.documentSession) notice = BookmarkFeedback.message(result, "已加书签：${title ?: doc.name}")
                }
            },
        )
    }
    }
}

@Composable
private fun BookContent(
    vm: ShelfViewModel,
    doc: OpenedDoc,
    gestures: ReaderGestures,
    modifier: Modifier,
    handle: WebViewHandle,
    onPageReady: () -> Unit,
    onPageLoading: () -> Unit,
    find: FindState,
) {
    // v11：默认章节连续滚动；Aa 里关掉则保持一章一页
    if (vm.reading.continuous && doc !is OpenedDoc.Single) {
        FlowContent(vm, doc, gestures, modifier, handle, onPageReady, onPageLoading, find)
        return
    }
    val context = LocalContext.current
    val session = vm.documentSession
    val index = vm.chapter
    when (doc) {
        // TXT：主题和排版都通过 JS 实时切换，不重新加载
        is OpenedDoc.Txt -> key(vm.documentSession, doc.bookId, index, vm.jumpTick) {
            // v11：章节正文按需从副本读取（见 TxtBook），不在主线程读文件
            val html by produceState<String?>(null, doc, index) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        TxtRenderer.render(
                            doc.book.chapter(index), index, doc.chapterCount,
                            Templates.get(context, Templates.TXT), vm.theme, vm.reading,
                        )
                    }.getOrElse { "" }
                }
            }
            html?.let { page -> HtmlView(
                source = PageSource.Html(page),
                modifier = modifier,
                theme = vm.theme,
                reading = vm.liveReading.toCss(),
                initialRatio = vm.initialRatio(),
                onRatioChanged = { vm.onScroll(session, index, it) },
                onRendererGone = { vm.onRendererGone(session, index) },
                gestures = gestures,
                handle = handle,
                background = LocalYH.current.bg.toArgb(),
                onPageReady = onPageReady,
                onPageLoading = onPageLoading,
            ) }
        }

        // EPUB：章节禁用 JS，主题或排版变化时以当前位置重新加载本章（key 里带上两者）
        is OpenedDoc.Epub -> key(vm.documentSession, doc.bookId, index, vm.theme, vm.reading.toCss(), vm.jumpTick) {
            HtmlView(
                source = PageSource.BookFile(doc.rootDir, doc.chapters[index].path, vm.chapterAnchor),
                modifier = modifier,
                initialRatio = vm.initialRatio(),
                onRatioChanged = { vm.onScroll(session, index, it) },
                onRendererGone = { vm.onRendererGone(session, index) },
                onInternalLink = vm::onBookLink,
                bookTheme = { vm.theme },
                // v10：竖排书转为横排（阅读器按上下滚动翻页设计）
                bookReadingCss = { vm.reading.toCss() + if (doc.vertical) EpubInjector.HORIZONTAL_CSS else "" },
                gestures = gestures,
                handle = handle,
                background = LocalYH.current.bg.toArgb(),
                onPageReady = onPageReady,
                onPageLoading = onPageLoading,
            )
        }

        is OpenedDoc.Single -> Unit
    }
}

/**
 * v11：章节连续滚动（TXT 和 EPUB 共用，见 assets/book/flow.html）。
 *
 * 只有主动跳转（目录、上一章/下一章、书签、搜索结果、章节内链接）才重建页面；滚动中换章只更新
 * vm.chapter（顶栏标题、底部章节号、目录高亮跟着变），不重建。主题和排版通过 setTheme / setReading 实时生效。
 * 没有 JS 桥：滚动时用 evaluateJavascript 读 YH.state()。
 */
@Composable
private fun FlowContent(
    vm: ShelfViewModel,
    doc: OpenedDoc,
    gestures: ReaderGestures,
    modifier: Modifier,
    handle: WebViewHandle,
    onPageReady: () -> Unit,
    onPageLoading: () -> Unit,
    find: FindState,
) {
    val context = LocalContext.current
    val session = vm.documentSession
    val jump = vm.jumpTick
    key(session, doc.bookId, jump) {
        val readingCss = vm.liveReading.toCss() + if ((doc as? OpenedDoc.Epub)?.vertical == true) EpubInjector.HORIZONTAL_CSS else ""
        val source = remember {
            val urls = (doc as? OpenedDoc.Epub)?.chapters?.map { ch -> FlowShell.bookPath(ch.path) { Uri.encode(it) } }
            val shell = FlowShell.render(
                Templates.get(context, FlowShell.TEMPLATE), vm.theme, doc.chapterCount,
                vm.chapter.coerceIn(0, doc.chapterCount - 1), vm.initialRatio(), vm.chapterAnchor, urls,
                hold = find.open || vm.pendingFind != null,
            )
            when (doc) {
                is OpenedDoc.Epub -> PageSource.Flow(shell, epubRoot = doc.rootDir)
                is OpenedDoc.Txt -> PageSource.Flow(shell, txt = doc.book, txtTemplate = Templates.get(context, FlowShell.TXT_CHAPTER))
                is OpenedDoc.Single -> error("not a book")
            }
        }
        // 滚动事件只当作"该读状态了"的信号，合并成一个
        val ticks = remember { Channel<Unit>(Channel.CONFLATED) }
        val latestOnPageReady by rememberUpdatedState(onPageReady)
        HtmlView(
            source = source,
            modifier = modifier,
            theme = vm.theme,
            reading = readingCss,
            initialRatio = 0f,
            onRatioChanged = { ticks.trySend(Unit) },
            onRendererGone = { vm.onRendererGone(session, vm.chapter) },
            onInternalLink = vm::onFlowLink,
            bookTheme = { vm.theme },
            bookReadingCss = { readingCss },
            gestures = gestures,
            handle = handle,
            background = LocalYH.current.bg.toArgb(),
            onPageReady = { ticks.trySend(Unit) },
            onPageLoading = onPageLoading,
        )
        LaunchedEffect(Unit) {
            var announced = false
            var lastReported: Pair<Int, Float>? = null
            var appliedHold: Boolean? = null
            while (true) {
                if (announced) {
                    // Native taps inside an iframe need not produce a parent scroll or
                    // shouldOverrideUrlLoading callback. Keep a bounded idle poll alive.
                    withTimeoutOrNull(FLOW_LINK_POLL_MS) { ticks.receive() }
                    delay(FLOW_POLL_DEBOUNCE_MS)
                } else {
                    // 等第一章加载完、恢复好位置
                    withTimeoutOrNull(FLOW_READY_POLL_MS) { ticks.receive() }
                }
                if (announced && handle.webView == null) break
                val link = JsonStrings.decodeStringOrNull(handle.evaluate("window.YH ? YH.takeLink() : ''"))
                if (!link.isNullOrEmpty()) android.util.Log.d("YHFlow", "link route=$link")
                if (!link.isNullOrEmpty() && session == vm.documentSession && jump == vm.jumpTick) {
                    val uri = Uri.parse(link)
                    val path = uri.path.orEmpty()
                    if (path.startsWith("/book/")) {
                        vm.onFlowLink(path.removePrefix("/book/"), uri.fragment)
                    }
                }
                if (session != vm.documentSession || jump != vm.jumpTick) continue
                val state = FlowShell.parseState(handle.evaluate("window.YH ? YH.state() : ''"))
                if (state == null) { appliedHold = null; continue }
                val effectiveHold = find.open || vm.pendingFind != null
                if (appliedHold != effectiveHold) {
                    // state() confirms the first chapter is ready. Synchronize
                    // hold before replaying native Find or allowing shell fill.
                    val applied = handle.evaluate("window.YH && YH.state() ? (YH.hold($effectiveHold), true) : false")
                    if (applied != "true") continue
                    appliedHold = effectiveHold
                }
                if (!announced) {
                    announced = true
                    latestOnPageReady() // 搜索结果跳转后的高亮在这里重放：此时只有目标章已加载
                }
                // Idle link polling must not restart the 800 ms position-save
                // debounce when the chapter and ratio have not changed.
                if (state != lastReported) {
                    lastReported = state
                    // v1.0：真机上出现过"画面换章了、顶栏没变"，留一行日志方便对照（只在位置变化时打印）
                    android.util.Log.d("YHFlow", "state chapter=${state.first} ratio=${state.second} vmChapter=${vm.chapter}")
                    vm.onFlowPosition(session, state.first, state.second, jump)
                }
            }
        }
        // Wake the synchronized hold loop on Find and pending-hit changes.
        LaunchedEffect(find.open, vm.pendingFind) { ticks.trySend(Unit) }
    }
}

private const val FLOW_POLL_DEBOUNCE_MS = 120L
private const val FLOW_LINK_POLL_MS = 250L
private const val FLOW_READY_POLL_MS = 150L

/** 隐藏 / 恢复系统状态栏和导航栏；离开阅读页时一定恢复 */
@Composable
private fun ImmersiveSystemBars(hidden: Boolean, darkIcons: Boolean) {
    val view = LocalView.current
    val window = (LocalContext.current as? Activity)?.window ?: return
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
    val currentDarkIcons by androidx.compose.runtime.rememberUpdatedState(darkIcons)
    DisposableEffect(window, view) {
        val insets = ViewCompat.getRootWindowInsets(view)
        val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        val behavior = controller.systemBarsBehavior
        onDispose {
            controller.systemBarsBehavior = behavior
            controller.isAppearanceLightStatusBars = currentDarkIcons
            controller.isAppearanceLightNavigationBars = currentDarkIcons
            if (statusVisible) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
            if (navVisible) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
    LaunchedEffect(hidden, darkIcons, controller) {
        controller.isAppearanceLightStatusBars = darkIcons
        controller.isAppearanceLightNavigationBars = darkIcons
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
    is OpenedDoc.Txt -> doc.titles
    is OpenedDoc.Epub -> doc.chapters.map { it.title }
    is OpenedDoc.Single -> emptyList()
}

@Composable
private fun ChapterBar(index: Int, count: Int, glassVisible: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    // 浮动胶囊（Lens）：不再是贴底的整条工具栏，正文在胶囊两侧和下方仍然可见
    FloatingCapsule(glassKey = "chapter-bar", glassVisible = glassVisible) {
        TextButton(onClick = onPrev, enabled = index > 0) { Text("‹ 上一章") }
        Text(
            if (count > 1) "${index + 1} / $count" else "",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            color = LocalYH.current.muted,
        )
        TextButton(onClick = onNext, enabled = index < count - 1) { Text("下一章 ›") }
    }
}
