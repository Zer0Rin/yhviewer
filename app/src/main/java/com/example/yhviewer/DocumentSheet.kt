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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 书架长按：收藏、移除，HTML 文档还可以设置权限 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentSheet(
    book: Book,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onPermissions: (DocPermissions) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(book.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)

            if (!confirmDelete) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onToggleFavorite) { Text(if (book.favorite) "★ 取消收藏" else "☆ 收藏") }
                    TextButton(onClick = { confirmDelete = true }) { Text("从书架移除") }
                }
            } else {
                // 确认就地展开，不再弹第二个对话框
                Text("移除「${book.name}」？本地副本和它保存的网页数据会被删除，原文件不受影响。")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDelete) { Text("移除", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { confirmDelete = false }) { Text("取消") }
                }
            }

            if (book.type == DocType.HTML) {
                HorizontalDivider()
                PermissionEditor(DocPermissions.fromBits(book.permissions), onPermissions)
            }
        }
    }
}

/** 阅读页顶栏"权限"按钮打开的面板：只有权限编辑 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSheet(permissions: DocPermissions, onChange: (DocPermissions) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PermissionEditor(permissions, onChange)
            Text(
                "修改后页面会以新权限重新加载。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 三个档位 + 七个开关。选档位 = 把这个档位的权限全部套用；之后手动改任何一项，档位显示"自定义"。
 * 开关显示的是"实际生效"的状态：被版本或依赖关系禁用的项灰掉，并写明原因。
 */
@Composable
fun PermissionEditor(permissions: DocPermissions, onChange: (DocPermissions) -> Unit) {
    val net = AppCapabilities.networkCapable
    val storageSupported = AppCapabilities.persistentStorageSupported
    val tier = PermissionTier.of(permissions, net, storageSupported)
    val p = AppCapabilities.effective(permissions)
    val strictReason = "需要 Standard 版（Strict 版不联网）"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("权限", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(PermissionTier.STRICT, PermissionTier.STANDARD, PermissionTier.TRUSTED).forEach { t ->
                FilterChip(selected = tier == t, onClick = { onChange(AppCapabilities.effective(t.preset!!)) }, label = { Text(t.label) })
            }
            if (tier == PermissionTier.CUSTOM) {
                FilterChip(selected = true, onClick = {}, label = { Text(PermissionTier.CUSTOM.label) })
            }
        }
        Text(tierHint(tier), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        PermissionRow("运行脚本", "页面的交互、图表、动画需要它。关闭后只显示静态内容", p.scripts, enabled = true) {
            onChange(p.copy(scripts = it))
        }
        PermissionRow(
            "保留本地数据", "页面保存的设置、进度下次打开还在。数据只属于这份文档",
            p.persistentStorage, enabled = p.scripts && storageSupported,
            reason = when {
                !storageSupported -> "需要更新系统 WebView"
                !p.scripts -> "需要先允许运行脚本"
                else -> null
            },
        ) { onChange(p.copy(persistentStorage = it)) }
        PermissionRow(
            "脚本打开外部链接", "允许页面不经点击，把你带到浏览器",
            p.scriptedNavigation, enabled = p.scripts, reason = if (!p.scripts) "需要先允许运行脚本" else null,
        ) { onChange(p.copy(scriptedNavigation = it)) }
        PermissionRow(
            "加载 CDN 脚本和样式", "来自 cdnjs、jsDelivr、unpkg 的图表库等。页面仍不能主动联网",
            p.cdn, enabled = net, reason = if (!net) strictReason else null,
        ) { onChange(p.copy(cdn = it)) }
        PermissionRow(
            "加载外部图片和字体", "⚠ 任意 HTTPS 图片、字体、音视频 URL 都可能携带文档内容并发送到外部服务器",
            p.externalMedia, enabled = net, reason = if (!net) strictReason else null, warning = true,
        ) { onChange(p.copy(externalMedia = it)) }
        PermissionRow(
            "嵌入外部页面", "⚠ 嵌入的网页按它自己的规则运行，可以自由联网；它的 URL 也可能携带文档内容并发送到外部服务器",
            p.frames, enabled = net && storageSupported,
            reason = when {
                !net -> strictReason
                !storageSupported -> "需要更新系统 WebView，才能隔离嵌入页面的数据"
                else -> null
            }, warning = true,
        ) { onChange(p.copy(frames = it)) }
        PermissionRow(
            "主动联网", "⚠ 页面可以把任何内容发送到任意服务器。只对你完全信任的页面打开",
            p.network, enabled = net, reason = if (!net) strictReason else null, warning = true,
        ) { onChange(p.copy(network = it)) }

        Text(
            "无论怎么设置，页面都不能读取手机里的文件、不能调用应用内部功能、读不到其他文档的数据。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun tierHint(tier: PermissionTier) = when (tier) {
    PermissionTier.STRICT -> "只显示静态内容，不运行任何脚本。适合来路不明的文件"
    PermissionTier.STANDARD -> "默认档。可以运行脚本，但不能联网、不保留数据"
    PermissionTier.TRUSTED -> "放开除“嵌入外部页面”和“主动联网”外的权限。注意：外部图片的地址也能携带数据，适合你自己生成、确认安全的页面"
    PermissionTier.CUSTOM -> "已在档位基础上手动调整"
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    reason: String? = null,
    warning: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                reason ?: description,
                style = MaterialTheme.typography.bodySmall,
                color = if (warning && enabled && checked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 显示实际生效的状态：被禁用的项即使存储里是"开"，也显示为关
        Switch(checked = checked && enabled, onCheckedChange = onChange, enabled = enabled)
    }
}
