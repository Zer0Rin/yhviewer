package com.example.yhviewer

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// 若编译器提示 combinedClickable 需要 opt-in，在函数上加 @OptIn(ExperimentalFoundationApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(vm: ShelfViewModel, onOpenFile: () -> Unit) {
    val books by vm.books.collectAsState()
    var pendingDelete by remember { mutableStateOf<Book?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("书架") },
                actions = { TextButton(onClick = onOpenFile) { Text("打开") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::updateQuery,
                placeholder = { Text("搜索文件名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp)) {
                FilterChip(
                    selected = vm.favoritesOnly,
                    onClick = vm::toggleFavoritesOnly,
                    label = { Text("只看收藏") },
                )
            }

            val list = books
            val filtering = vm.query.isNotBlank() || vm.favoritesOnly
            when {
                list == null -> Unit // 第一次查询还没回来，什么都不画，避免空状态闪烁
                list.isEmpty() && !filtering -> CenterText(
                    "书架还是空的。\n在文件管理器里用本应用打开 HTML / Markdown，\n或点右上角「打开」。",
                    Modifier.fillMaxSize(),
                )
                list.isEmpty() -> CenterText("没有匹配的文档", Modifier.fillMaxSize())
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { book -> // key：列表重排时 Compose 能认出"还是那一行"
                        BookRow(
                            book = book,
                            onClick = { vm.openBook(book.id) },
                            onLongClick = { pendingDelete = book },
                            onToggleFavorite = { vm.toggleFavorite(book) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("从书架移除？") },
            text = { Text("「${book.name}」的本地副本会被删除，原文件不受影响。") },
            confirmButton = {
                TextButton(onClick = { vm.delete(book); pendingDelete = null }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun BookRow(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeBadge(book.type)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                book.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${DateUtils.getRelativeTimeSpanString(book.lastOpenedAt)} · " +
                    Formatter.formatShortFileSize(context, book.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 用文字星号代替图标：新版 Compose BOM 不再默认带 material-icons 依赖
        TextButton(
            onClick = onToggleFavorite,
            modifier = Modifier.semantics {
                contentDescription = if (book.favorite) "取消收藏" else "收藏"
            },
        ) {
            Text(if (book.favorite) "★" else "☆", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun TypeBadge(type: DocType) {
    val label = when (type) {
        DocType.HTML -> "HTML"
        DocType.MARKDOWN -> "MD"
        DocType.UNKNOWN -> "TXT"
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
