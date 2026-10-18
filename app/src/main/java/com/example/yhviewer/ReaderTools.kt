package com.example.yhviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/*
 * v10：阅读页的工具：页内查找栏、全书搜索结果、书签列表、页面弹窗、缺失资源提示。
 */

// ======================= 页内查找 =======================

@Stable
class FindState {
    var open by mutableStateOf(false)
    var query by mutableStateOf("")
    var current by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    /** 已经由别处（搜索结果跳转）查过的词：FindBar 不再重复查找，以免覆盖已定位的第 n 个结果 */
    var lastSearched by mutableStateOf<String?>(null)
    var pageReady by mutableStateOf(false)
        private set
    fun pageLoading() {
        pageReady = false
        current = 0
        total = 0
        lastSearched = null
    }
    fun readyRequest(pending: Pair<String, Int>?): Pair<String, Int>? {
        pageReady = true
        if (pending != null) {
            open = true
            query = pending.first
        }
        val request = pending ?: query.takeIf { open && it.isNotBlank() }?.let { it to 0 }
        lastSearched = request?.first
        return request
    }

    fun replay(handle: WebViewHandle, pending: Pair<String, Int>? = null) {
        val (q, nth) = readyRequest(pending) ?: return
        var applied = false
        handle.find(q) { cur, count ->
            current = cur
            total = count
            if (!applied && count > 0) {
                applied = true
                repeat(nth.coerceIn(0, count - 1)) { handle.findNext(true) }
            }
        }
    }
}

/**
 * 顶栏位置的查找栏：输入即查（停顿 250ms），↑ ↓ 在结果间跳转。
 * onBookSearch 不为 null 时（TXT / EPUB）多一个"全书"按钮。
 */
@Composable
fun FindBar(
    state: FindState,
    handle: WebViewHandle,
    onClose: () -> Unit,
    onBookSearch: ((String) -> Unit)?,
) {
    val yh = LocalYH.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(state.query, state.pageReady) {
        if (!state.pageReady || state.query == state.lastSearched) return@LaunchedEffect
        delay(250)
        state.lastSearched = state.query
        handle.find(state.query) { cur, total -> state.current = cur; state.total = total }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(yh.bg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClose) { Text("关闭") }
        BasicTextField(
            value = state.query,
            onValueChange = { state.query = it.take(200) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = yh.fg),
            cursorBrush = SolidColor(yh.cold),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { handle.findNext(true) }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .semantics { contentDescription = "查找内容" }
                .dotRule(color = yh.line, focusedColor = yh.accent, focused = true)
                .padding(vertical = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (state.query.isEmpty()) Text("在本页查找", color = yh.subtle, style = MaterialTheme.typography.bodyMedium)
                    inner()
                }
            },
        )
        Text(
            if (state.query.isBlank()) "" else if (state.total == 0) "无结果" else "${state.current}/${state.total}",
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (state.query.isNotBlank() && state.total == 0) yh.accent else yh.muted,
        )
        TextButton(onClick = { handle.findNext(false) }, enabled = state.total > 0) { Text("↑") }
        TextButton(onClick = { handle.findNext(true) }, enabled = state.total > 0) { Text("↓") }
        if (onBookSearch != null) {
            TextButton(onClick = { onBookSearch(state.query) }, enabled = state.query.isNotBlank()) { Text("全书") }
        }
    }
}

// ======================= 全书搜索结果 =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSearchSheet(
    query: String,
    titles: List<String>,
    search: suspend (String) -> List<SearchHit>,
    onPick: (SearchHit, Int) -> Unit,   // 命中，以及它是本章第几个命中（从 0 开始）
    onDismiss: () -> Unit,
) {
    val yh = LocalYH.current
    var hits by remember(query) { mutableStateOf<List<SearchHit>?>(null) }
    LaunchedEffect(query) { hits = search(query) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = yh.bg,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            val list = hits
            Text(
                when {
                    list == null -> "正在搜索「$query」…"
                    list.isEmpty() -> "全书没有找到「$query」"
                    list.size >= BookSearch.MAX_HITS -> "「$query」超过 ${BookSearch.MAX_HITS} 处，只列出前 ${BookSearch.MAX_HITS} 处"
                    else -> "「$query」共 ${list.size} 处"
                },
                style = MaterialTheme.typography.labelSmall,
                color = yh.subtle,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                // 每条命中是本章第几个：用于跳过去后定位到同一处
                val nth = remember(list) {
                    val counts = HashMap<Int, Int>()
                    list.map { h -> (counts[h.chapter] ?: 0).also { counts[h.chapter] = it + 1 } }
                }
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    items(list.size) { i ->
                        val h = list[i]
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(h, nth[i]) }
                                .padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                titles.getOrNull(h.chapter) ?: "第 ${h.chapter + 1} 章",
                                style = MaterialTheme.typography.labelMedium,
                                color = yh.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                buildAnnotatedString {
                                    val s = h.snippet
                                    val a = h.matchStart.coerceIn(0, s.length)
                                    val b = (h.matchStart + h.matchLength).coerceIn(a, s.length)
                                    append(s.substring(0, a))
                                    withStyle(SpanStyle(color = yh.accent, fontWeight = FontWeight.Bold)) { append(s.substring(a, b)) }
                                    append(s.substring(b))
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = yh.fg,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        HorizontalDivider(color = yh.lineSoft)
                    }
                }
            }
        }
    }
}

// ======================= 书签 =======================

/** 书签列表（目录抽屉的"书签"页、单页文档的书签面板共用） */
@Composable
fun BookmarkList(
    bookmarks: List<Bookmark>,
    current: Int,
    onPick: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    val yh = LocalYH.current
    if (bookmarks.isEmpty()) {
        Text(
            "还没有书签。在阅读设置或书签面板中添加当前位置。",
            modifier = modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = yh.muted,
        )
        return
    }
    LazyColumn(modifier) {
        items(bookmarks, key = { it.id }) { b ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(b) }
                    .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    b.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (b.chapter == current) FontWeight.Bold else FontWeight.Normal,
                    color = yh.fg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { onDelete(b) }) { Text("删除", color = yh.muted) }
            }
            HorizontalDivider(color = yh.lineSoft)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkSheet(
    bookmarks: List<Bookmark>,
    onAdd: ((Result<Boolean>) -> Unit) -> Unit,
    onPick: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
    onDismiss: () -> Unit,
) {
    val yh = LocalYH.current
    var note by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = yh.bg) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("书签 · ${bookmarks.size}", style = MaterialTheme.typography.labelSmall, color = yh.subtle, modifier = Modifier.weight(1f))
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = yh.muted) }
                TextButton(enabled = !adding, onClick = {
                    adding = true
                    note = null
                    onAdd { result ->
                        adding = false
                        note = BookmarkFeedback.message(result)
                    }
                }) { Text(if (adding) "添加中…" else "＋ 当前位置") }
            }
            BookmarkList(bookmarks, current = -1, onPick = onPick, onDelete = onDelete, modifier = Modifier.heightIn(max = 480.dp))
        }
    }
}

// ======================= 页面弹窗 =======================

/**
 * 页面的 alert / confirm / prompt。标题写明来源，不会被当成系统或 YHViewer 自己的提示；
 * 同一个页面弹到第 3 次时提供"不再显示这个页面的弹窗"。
 */
@Composable
fun JsDialog(source: String, request: JsDialogRequest, offerBlock: Boolean, onDone: (block: Boolean) -> Unit) {
    var text by remember(request) { mutableStateOf((request as? JsDialogRequest.Prompt)?.default.orEmpty()) }
    var block by remember(request) { mutableStateOf(false) }
    fun finish(ok: Boolean) {
        when (request) {
            is JsDialogRequest.Alert -> request.result.confirm()
            is JsDialogRequest.Confirm -> if (ok) request.result.confirm() else request.result.cancel()
            is JsDialogRequest.Prompt -> if (ok) request.result.confirm(text) else request.result.cancel()
        }
        onDone(block)
    }
    AlertDialog(
        onDismissRequest = { finish(false) },
        title = { Text("「${source.take(40)}」中的网页显示", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(request.message, maxLines = 12, overflow = TextOverflow.Ellipsis)
                if (request is JsDialogRequest.Prompt) {
                    UnderlineField(value = text, onValueChange = { text = it.take(2_000) }, placeholder = "输入内容")
                }
                if (offerBlock) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = block, onCheckedChange = { block = it })
                        Text("不再显示这个页面的弹窗", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { finish(true) }) { Text("确定") } },
        dismissButton = if (request is JsDialogRequest.Alert) null else ({ TextButton(onClick = { finish(false) }) { Text("取消") } }),
    )
}

// ======================= 提示条 =======================

/** 页面顶部的一条提示，可关闭（缺失资源、竖排转横排） */
@Composable
fun ReaderNotice(text: String, action: String? = null, onAction: () -> Unit = {}, onClose: () -> Unit) {
    val yh = LocalYH.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(yh.surface)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = yh.fg)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
        TextButton(onClick = onClose) { Text("关闭", color = yh.muted) }
    }
}

/** 缺失资源提示文案（纯函数，JVM 可测） */
object MissingResources {
    /** /doc/assets/layout.css → assets/layout.css；/bundle/a/b.png → a/b.png */
    fun display(paths: List<String>): List<String> =
        paths.map { it.removePrefix("/doc/").removePrefix("/bundle/") }.filter { it.isNotBlank() }.distinct().sorted()

    fun message(paths: List<String>, bundled: Boolean): String {
        val shown = display(paths)
        val head = shown.take(2).joinToString("、")
        val more = if (shown.size > 2) " 等 ${shown.size} 个文件" else ""
        return if (bundled) "页面引用的 $head$more 不在导入的文件夹里。"
        else "页面引用了同文件夹里的 $head$more，单个文件导入时它们没有被带进来。在书架用「导入文件夹」导入整个文件夹即可显示。"
    }
}
