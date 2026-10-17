package com.example.yhviewer

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 书架（v7 改版）
 *
 *   7 本书 · 12 份文档
 *   书架                                   ＋ 打开
 *   ⌕ 搜索书名、作者、文件名
 *   全部 19 │ 收藏 3 │ 工作 6 │ 待读 4 │ 未分组 9 │ ＋ 分组     ← 第一层：范围（固定标签 + 用户分组）
 *   网页 3  笔记 5  代码 4  数据 2  小说 3  电子书 2              ← 第二层：按内容自动分类，可选
 *   ── 继续阅读 ──  [大封面] …
 *   ── 书籍 ──      [封面] …     ← 行高约 108dp
 *   ── 文档 ──      [HTML] …     ← 紧凑行
 *
 * 长按任意一项进入多选：顶部变成"已选 N 项 / 全选 / 取消"，底部出现"移到分组 / 收藏 / 删除 / 详情"。
 * 列表顺序始终按最近打开（数据库排序），筛选和分组只在界面层做。
 */

// ======================= 筛选（纯逻辑，JVM 可测） =======================

/** 第一层筛选：范围。用字符串 key 存，方便 rememberSaveable 和测试 */
sealed interface ShelfScope {
    val key: String
    data object All : ShelfScope { override val key = "all" }
    data object Favorites : ShelfScope { override val key = "fav" }
    data object Ungrouped : ShelfScope { override val key = "none" }
    data class Group(val id: Long) : ShelfScope { override val key = "g:$id" }

    companion object {
        fun fromGroupId(id: Long?): ShelfScope = if (id == null) Ungrouped else Group(id)

        fun fromKey(key: String): ShelfScope = when {
            key == "fav" -> Favorites
            key == "none" -> Ungrouped
            key.startsWith("g:") -> key.removePrefix("g:").toLongOrNull()?.let(::Group) ?: All
            else -> All
        }
    }
}

internal val Book.isBook get() = type == DocType.TXT || type == DocType.EPUB

/** 内容分类；升级前导入、还没补分类的先按 DocType 估一个 */
internal val Book.contentKind: ContentKind
    get() = ContentKind.fromId(kind) ?: when (type) {
        DocType.EPUB -> ContentKind.EBOOK
        DocType.HTML -> ContentKind.WEB
        DocType.MARKDOWN -> ContentKind.NOTE
        DocType.CODE -> ContentKind.CODE
        DocType.TXT -> ContentKind.NOVEL
        else -> ContentKind.TEXT
    }

internal fun inScope(b: Book, scope: ShelfScope): Boolean = when (scope) {
    ShelfScope.All -> true
    ShelfScope.Favorites -> b.favorite
    ShelfScope.Ungrouped -> b.groupId == null
    is ShelfScope.Group -> b.groupId == scope.id
}

/** 当前范围里各内容分类的数量（只返回数量 > 0 的，按枚举顺序） */
internal fun kindCounts(list: List<Book>, scope: ShelfScope): List<Pair<ContentKind, Int>> {
    val counts = list.filter { inScope(it, scope) }.groupingBy { it.contentKind }.eachCount()
    return ContentKind.entries.mapNotNull { k -> counts[k]?.let { k to it } }
}

internal data class ShelfSections(val continueReading: Book?, val books: List<Book>, val docs: List<Book>) {
    val all get() = listOfNotNull(continueReading) + books + docs
    val isEmpty get() = continueReading == null && books.isEmpty() && docs.isEmpty()
}

internal fun sectionsFor(list: List<Book>, scope: ShelfScope, kind: ContentKind?, searching: Boolean): ShelfSections {
    val filtered = list.filter { inScope(it, scope) && (kind == null || it.contentKind == kind) }
    // "继续阅读"：最近打开、读到一半的书。只在"全部"且没选分类、没搜索时出现，避免结果被拆成两处
    val hero = if (!searching && scope == ShelfScope.All && kind == null) {
        filtered.firstOrNull { it.isBook && it.progress > 0f && it.progress < 0.995f }
    } else null
    return ShelfSections(
        continueReading = hero,
        books = filtered.filter { it.isBook && it.id != hero?.id },
        docs = filtered.filter { !it.isBook },
    )
}

internal fun pruneShelfSelection(selectedIds: List<Long>, sections: ShelfSections?): List<Long> {
    if (sections == null) return selectedIds
    val visibleIds = sections.all.mapTo(HashSet()) { it.id }
    return selectedIds.filter { it in visibleIds }
}

// ======================= 书架 =======================

@Composable
fun ShelfScreen(vm: ShelfViewModel, onOpenFile: () -> Unit) {
    val yh = LocalYH.current
    val books by vm.books.collectAsState()
    val loadedGroups by vm.groups.collectAsState()
    val groups = loadedGroups.orEmpty()

    var scopeKey by rememberSaveable { mutableStateOf(ShelfScope.All.key) }
    var kindId by rememberSaveable { mutableStateOf<String?>(null) }
    // 多选：选中的 id。空集合 = 不在多选模式
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var dialog by remember { mutableStateOf<ShelfDialog?>(null) }
    var operationError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val list = books
    val scope = ShelfScope.fromKey(scopeKey)
    val kind = ContentKind.fromId(kindId)
    val searching = vm.query.isNotBlank()
    val selecting = selectedIds.isNotEmpty()

    // 分组被删、文档被删后，清掉失效的筛选和选中项
    LaunchedEffect(loadedGroups, scope) {
        if (loadedGroups != null && scope is ShelfScope.Group && groups.none { it.id == scope.id }) scopeKey = ShelfScope.All.key
    }
    BackHandler(enabled = selecting) { selectedIds = emptyList() }

    val sections = remember(list, scopeKey, kindId, searching) {
        list?.let { sectionsFor(it, scope, kind, searching) }
    }
    LaunchedEffect(sections) {
        selectedIds = pruneShelfSelection(selectedIds, sections)
    }
    // 书籍区的排列：封面墙（默认）或列表。存在本地偏好里，下次打开保持
    val context = LocalContext.current
    val shelfPrefs = remember { context.getSharedPreferences("shelf_prefs", android.content.Context.MODE_PRIVATE) }
    var booksAsGrid by remember { mutableStateOf(shelfPrefs.getBoolean(KEY_BOOKS_GRID, true)) }

    fun toggle(b: Book) {
        selectedIds = if (b.id in selectedIds) selectedIds - b.id else selectedIds + b.id
    }
    fun onClick(b: Book) = if (selecting) toggle(b) else vm.openBook(b.id)
    fun onLongClick(b: Book) = toggle(b) // 长按 = 进入多选并选中这一项
    fun selectedOf(b: Book): Boolean? = if (selecting) b.id in selectedIds else null

    Box(Modifier.fillMaxSize().background(yh.bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            if (selecting) {
                SelectionHeader(
                    count = selectedIds.size,
                    allSelected = sections != null && sections.all.all { it.id in selectedIds },
                    onSelectAll = { selectedIds = sections?.all.orEmpty().map { it.id } },
                    onCancel = { selectedIds = emptyList() },
                )
            } else {
                ShelfHeader(list, onOpenFile)
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UnderlineField(value = vm.query, onValueChange = vm::updateQuery, placeholder = "搜索书名、作者、文件名")
            }
            ScopeTabs(
                list = list.orEmpty(),
                groups = groups,
                selected = scope,
                onSelect = { scopeKey = it.key; kindId = null },
                onNewGroup = { dialog = ShelfDialog.NewGroup(moveIds = emptyList()) },
                onRenameGroup = { dialog = ShelfDialog.RenameGroup(it) },
                onDeleteGroup = { g -> dialog = ShelfDialog.DeleteGroup(g) },
            )
            KindChips(kindCounts(list.orEmpty(), scope), selected = kind, onSelect = { kindId = it?.id })

            when {
                list == null || sections == null -> Unit // 第一次查询还没回来，什么都不画，避免空状态闪烁
                list.isEmpty() && !searching -> EmptyShelf(onOpenFile)
                sections.isEmpty -> CenterText(
                    when {
                        searching -> "没有匹配「${vm.query.trim()}」的内容"
                        scope is ShelfScope.Group -> "这个分组还是空的。\n长按书架上的文档，选「移到分组」。"
                        else -> "这里还没有内容"
                    },
                    Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = if (selecting) 96.dp else 32.dp),
                ) {
                    sections.continueReading?.let { hero ->
                        sectionLabel("继续阅读", relativeTime(hero.lastOpenedAt).toString())
                        item(key = "hero") {
                            ContinueCard(vm, hero, selectedOf(hero), onClick = { onClick(hero) }, onLongClick = { onLongClick(hero) })
                        }
                    }
                    if (sections.books.isNotEmpty()) {
                        item(key = "label-书籍") {
                            BooksLabel(
                                count = sections.books.size + (if (sections.continueReading != null) 1 else 0),
                                grid = booksAsGrid,
                                onGrid = { booksAsGrid = it; shelfPrefs.edit().putBoolean(KEY_BOOKS_GRID, it).apply() },
                            )
                        }
                        if (booksAsGrid) {
                            // 封面墙：每行 3 本。按行分组放进 LazyColumn，避免在 LazyColumn 里嵌套 LazyGrid
                            items(sections.books.chunked(GRID_COLUMNS), key = { row -> "row-${row.first().id}" }) { row ->
                                BookGridRow(vm, row, { selectedOf(it) }, { onClick(it) }, { onLongClick(it) })
                            }
                        } else {
                            items(sections.books, key = { it.id }) { b ->
                                BookShelfRow(vm, b, selectedOf(b), onClick = { onClick(b) }, onLongClick = { onLongClick(b) })
                            }
                        }
                    }
                    if (sections.docs.isNotEmpty()) {
                        sectionLabel("文档", "${sections.docs.size} 份")
                        items(sections.docs, key = { it.id }) { b ->
                            DocShelfRow(b, selectedOf(b), onClick = { onClick(b) }, onLongClick = { onLongClick(b) })
                        }
                    }
                    item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
                }
            }
        }

        // 多选时的底部操作栏
        AnimatedVisibility(
            visible = selecting,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val chosen = list.orEmpty().filter { it.id in selectedIds }
            SelectionBar(
                allFavorite = chosen.isNotEmpty() && chosen.all { it.favorite },
                single = chosen.size == 1,
                onMove = { dialog = ShelfDialog.Move(selectedIds) },
                onFavorite = {
                    val ids = selectedIds.toList()
                    val favorite = !chosen.all { it.favorite }
                    coroutineScope.launch {
                        vm.setFavorite(ids, favorite).onFailure { operationError = it.message ?: "收藏失败" }
                    }
                },
                onDelete = { dialog = ShelfDialog.DeleteBooks(chosen) },
                onDetails = { chosen.singleOrNull()?.let { dialog = ShelfDialog.Details(it.id) } },
            )
        }
    }

    operationError?.let { message ->
        AlertDialog(onDismissRequest = { operationError = null }, title = { Text("操作失败") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { operationError = null }) { Text("确定") } })
    }
    ShelfDialogs(
        dialog = dialog,
        vm = vm,
        books = list.orEmpty(),
        groups = groups,
        onDismiss = { dialog = null },
        onDone = { clearSelection -> dialog = null; if (clearSelection) selectedIds = emptyList() },
    )
}

// ======================= 头部 =======================

@Composable
private fun ShelfHeader(list: List<Book>?, onOpenFile: () -> Unit) {
    val yh = LocalYH.current
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (list != null && list.isNotEmpty()) {
                val nBooks = list.count { it.isBook }
                val reading = list.count { it.isBook && it.progress > 0f && it.progress < 0.995f }
                Text(
                    "$nBooks 本书 · ${list.size - nBooks} 份文档" + (if (reading > 0) " · $reading 本在读" else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = yh.subtle,
                )
            }
            Text("书架", style = MaterialTheme.typography.headlineLarge, color = yh.fg)
        }
        OutlineAction("＋ 打开", onOpenFile)
    }
}

@Composable
private fun SelectionHeader(count: Int, allSelected: Boolean, onSelectAll: () -> Unit, onCancel: () -> Unit) {
    val yh = LocalYH.current
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("多选", style = MaterialTheme.typography.labelSmall, color = yh.accent)
            // 数字用无衬线：衬线体的 "1" 在大字号下容易看成 "l"
            Text("已选 $count 项", style = MaterialTheme.typography.headlineLarge.copy(fontFamily = YHFonts.sans), color = yh.fg)
        }
        if (!allSelected) PlainAction("全选", onSelectAll)
        PlainAction("取消", onCancel)
    }
}

/** 强调色描边的胶囊按钮：书架上唯一的主要动作（控件圆润，见 YHTheme 的形状规则） */
@Composable
internal fun OutlineAction(label: String, onClick: () -> Unit) {
    val yh = LocalYH.current
    val interaction = remember { MutableInteractionSource() }
    Text(
        label,
        modifier = Modifier
            .pressScale(interaction)
            .clip(PillShape)
            .border(1.dp, yh.accent, PillShape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
        color = yh.accent,
    )
}

@Composable
internal fun PlainAction(label: String, onClick: () -> Unit, color: Color = LocalYH.current.fg, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        color = if (enabled) color else color.copy(alpha = 0.38f),
    )
}

// ======================= 第一层：范围标签（固定 + 分组） =======================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScopeTabs(
    list: List<Book>,
    groups: List<ShelfGroup>,
    selected: ShelfScope,
    onSelect: (ShelfScope) -> Unit,
    onNewGroup: () -> Unit,
    onRenameGroup: (ShelfGroup) -> Unit,
    onDeleteGroup: (ShelfGroup) -> Unit,
) {
    val yh = LocalYH.current
    val byGroup = remember(list) { list.groupingBy { it.groupId }.eachCount() }
    LazyRow(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .drawBehind { drawLine(yh.lineSoft, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) },
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "all") { ScopeTab("全部", list.size, selected == ShelfScope.All, { onSelect(ShelfScope.All) }) }
        item(key = "fav") { ScopeTab("收藏", list.count { it.favorite }, selected == ShelfScope.Favorites, { onSelect(ShelfScope.Favorites) }) }
        items(groups, key = { "g${it.id}" }) { g ->
            var menu by remember { mutableStateOf(false) }
            Box {
                ScopeTab(
                    g.name, byGroup[g.id] ?: 0, selected == ShelfScope.Group(g.id),
                    onClick = { onSelect(ShelfScope.Group(g.id)) },
                    onLongClick = { menu = true },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; onRenameGroup(g) })
                    DropdownMenuItem(text = { Text("删除分组", color = yh.accent) }, onClick = { menu = false; onDeleteGroup(g) })
                }
            }
        }
        // 有分组之后才显示"未分组"，否则它和"全部"完全一样
        if (groups.isNotEmpty()) {
            item(key = "none") { ScopeTab("未分组", byGroup[null] ?: 0, selected == ShelfScope.Ungrouped, { onSelect(ShelfScope.Ungrouped) }) }
        }
        item(key = "new") {
            Text(
                "＋ 分组",
                modifier = Modifier
                    .clickable(role = Role.Button, onClickLabel = "新建分组", onClick = onNewGroup)
                    .padding(top = 4.dp, bottom = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = yh.accent,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScopeTab(label: String, count: Int, on: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val yh = LocalYH.current
    Row(
        Modifier
            .semantics { contentDescription = "$label，$count 项"; this.selected = on }
            .combinedClickable(role = Role.Tab, onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClick?.let { "管理分组" })
            .drawBehind {
                // 选中标记：文字下方一段 16×2dp 的短线（Lattice 的标签指示），不铺满整个标签
                if (on) drawRect(
                    yh.accent,
                    topLeft = Offset(0f, size.height - 2.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(16.dp.toPx(), 2.dp.toPx()),
                )
            }
            .padding(top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) yh.fg else yh.muted, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Text("$count", style = MaterialTheme.typography.bodySmall, color = yh.subtle)
    }
}

// ======================= 第二层：按内容分类 =======================

@Composable
private fun KindChips(counts: List<Pair<ContentKind, Int>>, selected: ContentKind?, onSelect: (ContentKind?) -> Unit) {
    if (counts.size < 2 && selected == null) return // 只有一种内容时没有筛选的意义
    val yh = LocalYH.current
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (if (selected != null && counts.none { it.first == selected }) counts + (selected to 0) else counts).forEach { (k, n) ->
            val on = k == selected
            Row(
                Modifier
                    .semantics { this.selected = on } // 参数名 selected 会遮住语义属性，必须写 this.
                    .clip(PillShape)
                    .background(if (on) yh.accent.copy(alpha = 0.12f) else Color.Transparent)
                    .border(1.dp, if (on) yh.accent else yh.lineSoft, PillShape)
                    .clickable(role = Role.Checkbox) { onSelect(if (on) null else k) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(k.label, style = MaterialTheme.typography.labelMedium, color = if (on) yh.accent else yh.muted)
                Spacer(Modifier.width(4.dp))
                Text("$n", style = MaterialTheme.typography.bodySmall, color = yh.subtle)
            }
        }
    }
}

private fun LazyListScope.sectionLabel(title: String, trailing: String) {
    item(key = "label-$title") {
        val yh = LocalYH.current
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = yh.subtle, modifier = Modifier.weight(1f))
            Text(trailing, style = MaterialTheme.typography.labelSmall, color = yh.subtle)
        }
    }
}

// ======================= 书 =======================

/** 多选时的行背景：选中的行用表面色铺底，配合封面 / 方框上的勾 */
private fun Modifier.selectionBackground(selected: Boolean?, color: Color) =
    if (selected == true) background(color) else this

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(vm: ShelfViewModel, book: Book, selected: Boolean?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val yh = LocalYH.current
    val (title, author) = BookTitles.display(book)
    val interaction = remember { MutableInteractionSource() }
    // "继续阅读"是书架上唯一一块铺底色的区域：表面色 + 直角，让它和下面的列表拉开层次
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected == true) yh.lineSoft else yh.surface)
            .semantics { if (selected != null) this.selected = selected }
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "多选",
            )
            .padding(16.dp),
    ) {
        Box(Modifier.pressScale(interaction)) { BookCover(vm, book, width = 100.dp, selected = selected) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).heightIn(min = 150.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = yh.fg, maxLines = 3, overflow = TextOverflow.Ellipsis)
            author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = yh.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(
                "读到第 ${Locator.decode(book.locator).chapter.toLong() + 1} 章",
                style = MaterialTheme.typography.bodySmall,
                color = yh.muted,
            )
            Spacer(Modifier.weight(1f))
            DotProgress(book.progress)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "读到 ${percent(book.progress)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = yh.subtle,
                    modifier = Modifier.weight(1f),
                )
                if (book.favorite) Text("★", color = yh.accent, style = MaterialTheme.typography.bodySmall)
            }
            if (selected == null) {
                Text(
                    "继续阅读 ›",
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(PillShape)
                        .background(yh.accent)
                        .clickable(role = Role.Button, onClick = onClick)
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = yh.onAccent,
                )
            }
        }
    }
}

/** "书籍 · N 本" 标签，右侧切换封面墙 / 列表 */
@Composable
private fun BooksLabel(count: Int, grid: Boolean, onGrid: (Boolean) -> Unit) {
    val yh = LocalYH.current
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("书籍 · $count 本", style = MaterialTheme.typography.labelSmall, color = yh.subtle, modifier = Modifier.weight(1f))
        listOf(true to "封面", false to "列表").forEach { (value, label) ->
            Text(
                label,
                modifier = Modifier
                    .semantics { this.selected = value == grid }
                    .clickable(role = Role.Tab) { onGrid(value) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (value == grid) yh.fg else yh.subtle,
            )
        }
    }
}

/** 封面墙的一行：固定 GRID_COLUMNS 格，最后一行不满时用空位补齐，封面宽度保持一致 */
@Composable
private fun BookGridRow(
    vm: ShelfViewModel,
    row: List<Book>,
    selectedOf: (Book) -> Boolean?,
    onClick: (Book) -> Unit,
    onLongClick: (Book) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        row.forEach { b ->
            BookGridCell(vm, b, selectedOf(b), { onClick(b) }, { onLongClick(b) }, Modifier.weight(1f))
        }
        repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGridCell(
    vm: ShelfViewModel,
    book: Book,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
) {
    val yh = LocalYH.current
    val (title, author) = BookTitles.display(book)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .semantics(mergeDescendants = true) { if (selected != null) this.selected = selected }
            .combinedClickable(
                interactionSource = interaction,
                indication = null, // 封面自己有按压缩放，不再叠加水波纹
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "多选",
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().pressScale(interaction)) {
            BookCover(vm, book, width = maxWidth, selected = selected)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
            color = yh.fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(author ?: percent(book.progress), style = MaterialTheme.typography.bodySmall, color = yh.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        DotProgress(book.progress, dotSize = 2.5.dp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookShelfRow(vm: ShelfViewModel, book: Book, selected: Boolean?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val yh = LocalYH.current
    val (title, author) = BookTitles.display(book)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .selectionBackground(selected, yh.surface)
                .semantics { if (selected != null) this.selected = selected }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "多选")
                .padding(vertical = 12.dp),
        ) {
            BookCover(vm, book, width = 56.dp, selected = selected)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).height(84.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = yh.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(author ?: "未知作者", style = MaterialTheme.typography.bodySmall, color = yh.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                ProgressLine(book.progress)
                Row(Modifier.padding(top = 4.dp)) {
                    Text(
                        "${percent(book.progress)} · ${relativeTime(book.lastOpenedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = yh.subtle,
                        modifier = Modifier.weight(1f),
                    )
                    if (book.favorite) Text("★", color = yh.accent, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        HorizontalDivider(color = yh.lineSoft)
    }
}

private fun percent(p: Float) = if (p <= 0f) "未开始" else "${(p * 100).toInt().coerceIn(1, 100)}%"

/** 2:3 的直角封面，带轻微投影。有 EPUB 封面图就用图，否则画排版封面。多选时右上角显示勾选框 */
@Composable
fun BookCover(vm: ShelfViewModel, book: Book, width: Dp, selected: Boolean? = null) {
    val yh = LocalYH.current
    val image by rememberCover(vm.covers, book)
    Box(Modifier.width(width).aspectRatio(2f / 3f)) {
        Box(
            Modifier
                .fillMaxSize()
                .shadow(elevation = if (width > 64.dp) 10.dp else 6.dp, shape = RectangleShape, ambientColor = yh.shadow, spotColor = yh.shadow)
                .semantics { contentDescription = "${book.name} 的封面" },
        ) {
            val img = image
            if (img != null) {
                Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                TypographicCover(book, width)
            }
        }
        if (selected != null) {
            CheckSquare(selected, Modifier.align(Alignment.TopEnd).padding(4.dp), onImage = true)
        }
    }
}

/** 勾选框：选中 = 强调色实心 + 勾；未选 = 细线空框（在封面上时加半透明底，保证看得清） */
@Composable
internal fun CheckSquare(checked: Boolean, modifier: Modifier = Modifier, onImage: Boolean = false) {
    val yh = LocalYH.current
    Box(
        modifier
            .size(20.dp)
            .background(if (checked) yh.accent else if (onImage) Color(0x66000000) else Color.Transparent)
            .border(1.dp, if (checked) yh.accent else if (onImage) Color.White else yh.line, RectangleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = yh.onAccent, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TypographicCover(book: Book, width: Dp) {
    val (title, author) = BookTitles.display(book)
    val palette = CoverPalette.forTitle(title)
    val fg = Color(palette.foreground)
    val big = width > 64.dp
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(palette.background))
            .padding(horizontal = if (big) 10.dp else 6.dp, vertical = if (big) 10.dp else 7.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            if (book.type == DocType.EPUB) "EPUB" else "TXT",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (big) 9.sp else 7.sp),
            color = fg.copy(alpha = 0.75f),
        )
        Column(verticalArrangement = Arrangement.spacedBy(if (big) 6.dp else 4.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(fg.copy(alpha = 0.5f)))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = if (big) 16.sp else 10.sp,
                    lineHeight = if (big) 19.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = fg,
                maxLines = if (big) 4 else 3,
                overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(fg.copy(alpha = 0.5f)))
        }
        Text(
            author.orEmpty(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (big) 9.sp else 7.sp),
            color = fg.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ======================= 文档 =======================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocShelfRow(book: Book, selected: Boolean?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val yh = LocalYH.current
    val context = LocalContext.current
    val relaxed = book.type == DocType.HTML &&
        AppCapabilities.effective(DocPermissions.fromBits(book.permissions)).isRelaxed
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .selectionBackground(selected, yh.surface)
                .semantics { if (selected != null) this.selected = selected }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "多选")
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 多选时类型方框换成勾选框，位置不变，列表不跳动
            if (selected != null) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { CheckSquare(selected) }
            } else {
                TypeGlyph(book)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(book.name, style = MaterialTheme.typography.labelLarge, color = yh.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row {
                    Text(
                        "${book.contentKind.label} · ${relativeTime(book.lastOpenedAt)} · " +
                            Formatter.formatShortFileSize(context, book.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = yh.subtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (relaxed) {
                        Text(" · 已放宽", style = MaterialTheme.typography.bodySmall, color = yh.accent, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            if (book.favorite) Text("★", color = yh.accent, modifier = Modifier.padding(start = 8.dp))
        }
        HorizontalDivider(color = yh.lineSoft)
    }
}

/** 文档类型：细线方框 + 扩展名（HTML / MD / KT / PY …） */
@Composable
private fun TypeGlyph(book: Book) {
    val yh = LocalYH.current
    val ext = book.name.substringAfterLast('.', "").uppercase().take(4)
    val label = ext.ifEmpty {
        when (book.type) {
            DocType.HTML -> "HTML"
            DocType.MARKDOWN -> "MD"
            else -> "TXT"
        }
    }
    Box(Modifier.size(36.dp).border(1.dp, yh.line, RectangleShape), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.5.sp), color = yh.muted)
    }
}

// ======================= 空书架 =======================

@Composable
private fun EmptyShelf(onOpenFile: () -> Unit) {
    val yh = LocalYH.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("书架还是空的", style = MaterialTheme.typography.headlineSmall, color = yh.fg)
        Spacer(Modifier.height(8.dp))
        Text(
            "在文件管理器里用 YHViewer 打开 HTML、Markdown、代码、TXT 或 EPUB，或者从这里选一个文件。所有文件都在沙箱里打开。",
            style = MaterialTheme.typography.bodyMedium,
            color = yh.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        OutlineAction("＋ 打开文件", onOpenFile)
    }
}

private const val GRID_COLUMNS = 3
private const val KEY_BOOKS_GRID = "books_grid"
