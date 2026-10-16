package com.example.yhviewer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Trusted editor page; document contents are data and never inherit HTML grants. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: ShelfViewModel, doc: OpenedDoc.Single, template: String? = null, onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val session = remember(vm, doc.bookId) { vm.startEditor(doc, template) }
    val scope = session.scope
    val handle = session.handle
    val snackbar = session.snackbar
    val prefs = remember { context.getSharedPreferences("editor_prefs", android.content.Context.MODE_PRIVATE) }
    val lang = session.lang
    val runLang = CodeRunner.runnableLang(lang, AppCapabilities.pythonAvailable)
    var menuOpen by remember { mutableStateOf(false) }
    var runCode by remember { mutableStateOf<String?>(null) }

    fun close(saved: Boolean = false) { vm.finishEditor(saved); onClose() }
    fun notify(message: String) { scope.launch { snackbar.showSnackbar(message) } }
    fun action(block: suspend () -> Unit) {
        if (session.busy) return
        scope.launch {
            session.busy = true
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { notify(e.message ?: "编辑操作失败") }
            finally { session.busy = false }
        }
    }
    suspend fun snapshot(): String? {
        val text = handle.editorText()
        if (text == null) notify("编辑器未就绪，没有保存")
        else require(EditorRenderer.canEdit(text)) { "文档超过 100 万字符，无法保存" }
        return text
    }
    suspend fun save(text: String, close: Boolean) {
        vm.saveEdits(doc.bookId, text).fold(
            onSuccess = {
                // Mark exactly the persisted snapshot; later keystrokes must stay dirty.
                handle.markSaved(text)
                if (close) {
                    if (handle.evaluate("window.editorApi ? window.editorApi.isDirty() : null") == "false" && !session.crashed) close(saved = true) else session.confirmLeave = true
                } else notify("已保存到书架副本（原文件未改动）")
            },
            onFailure = { notify("保存失败：${it.message}") },
        )
    }
    fun requestSave(close: Boolean) = action {
        val text = snapshot() ?: return@action
        if (prefs.getBoolean("copy_warning_seen", false)) save(text, close)
        else { session.closeAfterSave = close; session.pendingSave = text }
    }
    fun leave() = action {
        val state = handle.evaluate("window.editorApi ? window.editorApi.isDirty() : null")
        if (state == "false" && !session.crashed) close() else session.confirmLeave = true
    }

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val text = session.exportSnapshot
        session.exportSnapshot = null
        if (uri != null && text != null) action {
            vm.saveAs(uri, text).fold(
                onSuccess = { notify("已另存为新文件，并加入书架") },
                onFailure = { notify("另存为失败：${it.message}") },
            )
        }
    }
    BackHandler { leave() }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(enabled = !session.busy, onClick = { leave() }) { Text("‹ 完成") } },
                actions = {
                    if (runLang != null) TextButton(enabled = !session.busy && !session.crashed, onClick = {
                        action { runCode = snapshot() }
                    }) { Text("▶ 运行") }
                    TextButton(enabled = !session.busy, onClick = { requestSave(false) }) { Text("保存") }
                    Box {
                        TextButton(enabled = !session.busy, onClick = { menuOpen = true }) { Text("⋯") }
                        DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("另存为…") }, onClick = {
                                menuOpen = false
                                action {
                                    val text = snapshot() ?: return@action
                                    session.exportSnapshot = text
                                    saveAs.launch(doc.name)
                                }
                            })
                            DropdownMenuItem(text = { Text((if (vm.wrap) "✓ " else "") + "自动换行") }, onClick = {
                                menuOpen = false; vm.toggleWrap()
                            })
                            HorizontalDivider()
                            ReaderTheme.entries.forEach { theme ->
                                DropdownMenuItem(text = { Text((if (theme == vm.theme) "✓ " else "") + theme.label) }, onClick = {
                                    vm.selectTheme(theme); menuOpen = false
                                })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (session.crashed) CenterText("编辑器进程已终止。未保存的修改可能丢失，书架副本未受影响。", Modifier.padding(padding).fillMaxSize())
        else {
            val owner = remember(session) { Any() }
            LaunchedEffect(session, session.pageLoads, vm.theme, vm.wrap) {
                if (session.pageLoads > 0) handle.evaluate(
                    "window.setTheme && window.setTheme(${MarkdownRenderer.toJsonString(vm.theme.id)});" +
                        "window.setWrap && window.setWrap(${vm.wrap})",
                )
            }
            AndroidView(
                modifier = Modifier.padding(padding).fillMaxSize().imePadding(),
                factory = { session.mount(owner) },
                onRelease = { session.unmount(owner) },
            )
        }
    }
    if (session.confirmLeave) AlertDialog(
        onDismissRequest = { vm.cancelEditorLeave() },
        title = { Text("保存修改？") },
        text = { Text(if (session.crashed) "编辑器已停止，无法读取未保存内容。" else if (session.incomingUri != null) "收到打开文件的请求。保存后再打开；若来自同一原文件，将打开已保存的书架副本。不保存会重新导入原文件。" else "「${doc.name}」可能有未保存的修改。") },
        confirmButton = { TextButton(enabled = !session.busy, onClick = { session.confirmLeave = false; requestSave(true) }) { Text("保存") } },
        dismissButton = { Row {
            TextButton(enabled = !session.busy, onClick = { session.confirmLeave = false; close() }) { Text("不保存") }
            TextButton(onClick = { vm.cancelEditorLeave() }) { Text("取消") }
        } },
    )
    session.pendingSave?.let { text ->
        AlertDialog(
            onDismissRequest = { session.pendingSave = null; vm.cancelEditorLeave() },
            title = { Text("保存到书架副本") },
            text = { Text("原文件不会改动。从原位置重新打开同一文件会覆盖书架副本，编辑内容可能丢失。需要长期保留修改，请使用“另存为”。") },
            confirmButton = { TextButton(onClick = {
                session.pendingSave = null
                prefs.edit().putBoolean("copy_warning_seen", true).apply()
                action { save(text, session.closeAfterSave) }
            }) { Text("继续保存") } },
            dismissButton = { TextButton(onClick = { session.pendingSave = null; vm.cancelEditorLeave() }) { Text("取消") } },
        )
    }
    runCode?.let { code ->
        if (runLang != null) RunSheet(code, doc.name, vm.theme, runLang) { runCode = null }
    }
}
