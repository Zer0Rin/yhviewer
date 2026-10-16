package com.example.yhviewer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

const val RUNNER_CRASH_MESSAGE = "运行环境崩溃（可能是内存耗尽）"

/** Each run owns an ephemeral WebView. Stop/restart/dismiss remove it and terminate its Worker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunSheet(code: String, fileName: String, theme: ReaderTheme, lang: CodeRunner.Lang = CodeRunner.Lang.JAVASCRIPT, onDismiss: () -> Unit) {
    var runId by remember(code, fileName, lang) { mutableIntStateOf(0) }
    var timeoutMs by remember { mutableIntStateOf(CodeRunner.DEFAULT_TIMEOUT_MS) }
    var halted by remember(code, fileName, lang) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val outputHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).coerceIn(160f,420f).dp
    // Resolve AUTO outside the dialog: its themed WebView context can report dark in a light system.
    val runnerTheme = if (theme == ReaderTheme.AUTO) {
        if (isSystemInDarkTheme()) ReaderTheme.INK else ReaderTheme.PAPER
    } else theme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("运行 $fileName", style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (halted == null) TextButton(onClick = { halted = "已停止" }) { Text("停止") }
                TextButton(onClick = { halted = null; runId++ }) { Text("重新运行") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            Box(Modifier.fillMaxWidth().height(outputHeight)) {
                val message = halted
                if (message != null) {
                    Text(message, modifier = Modifier.align(Alignment.Center).padding(20.dp),
                        color = if (message == RUNNER_CRASH_MESSAGE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    key(runId, timeoutMs, code, lang) {
                        val html = remember(code, timeoutMs, lang) {
                            CodeRunner.render(code, Templates.get(context, CodeRunner.TEMPLATE), runnerTheme, timeoutMs, lang)
                        }
                        HtmlView(
                            source = PageSource.Html(html, csp = CodeRunner.cspFor(lang)),
                            modifier = Modifier.fillMaxSize(),
                            theme = runnerTheme,
                            permissions = DocPermissions.STANDARD,
                            onRendererGone = { halted = RUNNER_CRASH_MESSAGE },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((if (lang == CodeRunner.Lang.PYTHON) "Python 只含标准库，首次加载需几秒；不支持键盘输入。" else "") +
                    "独立线程运行；不能联网或访问页面、手机文件。超时 ${timeoutMs / 1000} 秒终止；最多 1000 条输出 / 20 万字符。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                val long = timeoutMs == CodeRunner.LONG_TIMEOUT_MS
                TextButton(onClick = { timeoutMs = if (long) CodeRunner.DEFAULT_TIMEOUT_MS else CodeRunner.LONG_TIMEOUT_MS; halted = null }) {
                    Text(if (long) "恢复 5 秒" else "放宽到 30 秒")
                }
            }
        }
    }
}
