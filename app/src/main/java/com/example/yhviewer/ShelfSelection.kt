package com.example.yhviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 书架上会弹出的对话框 / 面板。同一时间只有一个 */
sealed interface ShelfDialog {
    /** moveIds 非空：建好后把这些文档移进去（"移到分组 → 新建分组"） */
    data class NewGroup(val moveIds: List<Long>) : ShelfDialog
    data class RenameGroup(val group: ShelfGroup) : ShelfDialog
    data class DeleteGroup(val group: ShelfGroup) : ShelfDialog
    data class Move(val ids: List<Long>) : ShelfDialog
    data class DeleteBooks(val books: List<Book>) : ShelfDialog
    data class Details(val bookId: Long) : ShelfDialog
}

/**
 * @param onDone 操作完成。参数 true = 同时退出多选（移动、删除之后）
 */
@Composable
fun ShelfDialogs(
    dialog: ShelfDialog?,
    vm: ShelfViewModel,
    books: List<Book>,
    groups: List<ShelfGroup>,
    onDismiss: () -> Unit,
    onDone: (clearSelection: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var operationError by remember(dialog) { mutableStateOf<String?>(null) }
    fun runOperation(clearSelection: Boolean, block: suspend () -> Result<Unit>) {
        scope.launch {
            block().fold(onSuccess = { onDone(clearSelection) }, onFailure = { operationError = it.message ?: "操作失败" })
        }
    }
    operationError?.let { message ->
        AlertDialog(
            onDismissRequest = { operationError = null },
            title = { Text("操作失败") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { operationError = null }) { Text("确定") } },
        )
    }
    when (dialog) {
        null -> Unit

        is ShelfDialog.NewGroup -> GroupNameDialog(
            title = if (dialog.moveIds.isEmpty()) "新建分组" else "新建分组并移入 ${dialog.moveIds.size} 项",
            initial = "",
            groups = groups,
            renamingId = null,
            onDismiss = onDismiss,
            onConfirm = { name, showError ->
                scope.launch {
                    vm.createGroup(name, moveIds = dialog.moveIds).fold(
                        onSuccess = { onDone(dialog.moveIds.isNotEmpty()) },
                        onFailure = { showError(it.message ?: "创建失败") },
                    )
                }
            },
        )

        is ShelfDialog.RenameGroup -> GroupNameDialog(
            title = "重命名分组",
            initial = dialog.group.name,
            groups = groups,
            renamingId = dialog.group.id,
            onDismiss = onDismiss,
            onConfirm = { name, showError ->
                scope.launch {
                    vm.renameGroup(dialog.group.id, name).fold(
                        onSuccess = { onDone(false) },
                        onFailure = { showError(it.message ?: "重命名失败") },
                    )
                }
            },
        )

        is ShelfDialog.DeleteGroup -> {
            var memberCount by remember(dialog.group.id) { mutableStateOf<Int?>(null) }
            LaunchedEffect(dialog.group.id) {
                memberCount = vm.groupMemberCount(dialog.group.id).getOrNull()
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("删除分组「${dialog.group.name}」？") },
                text = {
                    Text(
                        when (val count = memberCount) {
                            null -> "分组内的文档会回到「未分组」，文件和阅读进度都不会删除。"
                            0 -> "这个分组是空的。"
                            else -> "里面的 $count 项会回到「未分组」，文件和阅读进度都不会删除。"
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = { runOperation(false) { vm.deleteGroup(dialog.group.id) } }) {
                        Text("删除分组", color = LocalYH.current.accent)
                    }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
            )

        }

        is ShelfDialog.Move -> {
            // 所选文档都在同一个分组时，标出"当前所在"
            val memberGroups = books.filter { it.id in dialog.ids }.map { it.groupId }.distinct()
            MoveToGroupSheet(
                count = dialog.ids.size,
                groups = groups,
                current = if (memberGroups.size == 1) ShelfScope.fromGroupId(memberGroups[0]) else null,
                onPick = { groupId -> runOperation(true) { vm.moveToGroup(dialog.ids, groupId) } },
                onCreateAndMove = { name, showError ->
                    scope.launch {
                        vm.createGroup(name, moveIds = dialog.ids).fold(
                            onSuccess = { onDone(true) },
                            onFailure = { showError(it.message ?: "创建失败") },
                        )
                    }
                },
                onDismiss = onDismiss,
            )
        }

        is ShelfDialog.DeleteBooks -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (dialog.books.size == 1) "从书架移除？" else "从书架移除 ${dialog.books.size} 项？") },
            text = {
                Text(
                    (if (dialog.books.size == 1) "「${dialog.books[0].name}」" else "所选文档") +
                        "的本地副本和阅读进度会被删除，原文件不受影响。"
                )
            },
            confirmButton = {
                TextButton(onClick = { runOperation(true) { vm.deleteMany(dialog.books) } }) {
                    Text("移除", color = LocalYH.current.accent)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )

        is ShelfDialog.Details -> {
            // 从最新列表里取，收藏和权限改完能立刻反映在面板上；书已被删除就关掉
            val book = books.firstOrNull { it.id == dialog.bookId }
            if (book == null) LaunchedEffect(Unit) { onDismiss() } else DocumentSheet(
                book = book,
                onToggleFavorite = { vm.toggleFavorite(book) },
                onDelete = { runOperation(true) { vm.deleteMany(listOf(book)) } },
                onPermissions = { vm.setPermissions(book.id, it) },
                onDismiss = onDismiss,
            )
        }
    }
}

// ======================= 多选底栏 =======================

@Composable
fun SelectionBar(
    allFavorite: Boolean,
    single: Boolean,
    onMove: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
) {
    val yh = LocalYH.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(yh.surface)
            .drawBehind { drawLine(yh.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlainAction("移到分组", onMove)
        PlainAction(if (allFavorite) "取消收藏" else "收藏", onFavorite)
        PlainAction("详情", onDetails, enabled = single)
        PlainAction("删除", onDelete, color = yh.accent)
    }
}

// ======================= 移到分组 =======================

/**
 * @param current 所选文档共同所在的位置（ShelfScope.Ungrouped 或 Group）；分散在不同分组时为 null
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveToGroupSheet(
    count: Int,
    groups: List<ShelfGroup>,
    current: ShelfScope?,
    onPick: (Long?) -> Unit,
    onCreateAndMove: (name: String, showError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val yh = LocalYH.current
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        // 面板切换成新建分组对话框：建好后把所选文档移进去
        GroupNameDialog(
            title = "新建分组并移入 $count 项",
            initial = "",
            groups = groups,
            renamingId = null,
            onDismiss = { creating = false },
            onConfirm = onCreateAndMove,
        )
        return
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = yh.bg) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 8.dp)) {
            Text(
                "移动 $count 项到",
                style = MaterialTheme.typography.labelSmall,
                color = yh.subtle,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item { MoveRow("未分组", isCurrent = current == ShelfScope.Ungrouped) { onPick(null) } }
                items(groups, key = { it.id }) { g ->
                    MoveRow(g.name, isCurrent = current == ShelfScope.Group(g.id)) { onPick(g.id) }
                }
            }
            HorizontalDivider(color = yh.lineSoft)
            Text(
                "＋ 新建分组并移入",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { creating = true }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.labelLarge,
                color = yh.accent,
            )
        }
    }
}

@Composable
private fun MoveRow(name: String, isCurrent: Boolean, onClick: () -> Unit) {
    val yh = LocalYH.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !isCurrent, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isCurrent) yh.muted else yh.fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isCurrent) Text("当前所在", style = MaterialTheme.typography.labelSmall, color = yh.subtle)
    }
}

// ======================= 分组名 =======================

/**
 * 输入分组名。边输边校验（空、过长、保留名、重名），校验不过时"确定"不可点；
 * 提交后仓库层再校验一次，失败信息通过 showError 显示在输入框下面。
 */
@Composable
private fun GroupNameDialog(
    title: String,
    initial: String,
    groups: List<ShelfGroup>,
    renamingId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, showError: (String) -> Unit) -> Unit,
) {
    val yh = LocalYH.current
    var name by rememberSaveable { mutableStateOf(initial) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val localError = GroupNames.validate(name, groups, renamingId)
    // 刚打开、还没输入时不显示"不能为空"
    val shownError = serverError ?: localError.takeIf { name.isNotEmpty() }
    val unchanged = renamingId != null && GroupNames.normalize(name) == initial

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                UnderlineField(
                    value = name,
                    onValueChange = { name = it.take(GroupNames.MAX_LENGTH + 5); serverError = null },
                    placeholder = "例如：工作、待读、Kotlin",
                )
                Row {
                    Text(
                        shownError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = yh.accent,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${GroupNames.normalize(name).length} / ${GroupNames.MAX_LENGTH}",
                        style = MaterialTheme.typography.bodySmall,
                        color = yh.subtle,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = localError == null && !unchanged && !submitting,
                onClick = { submitting = true; onConfirm(name) { serverError = it; submitting = false } },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
