package com.example.yhviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.example.yhviewer.ui.theme.YHViewerTheme

class MainActivity : ComponentActivity() {

    private val vm: ShelfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 只在首次创建时处理启动 Intent：旋转屏幕重建 Activity 时 intent 还是同一个，
        // 不加这个判断会重复导入、并把用户从书架强行拉回阅读页
        if (savedInstanceState == null) {
            extractUri(intent)?.let(vm::importAndOpen)
        }
        setContent {
            YHViewerTheme { AppRoot(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractUri(intent)?.let(vm::importAndOpen)
    }

    private fun extractUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }
}

@Composable
fun AppRoot(vm: ShelfViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importAndOpen)
    }
    when (vm.screen) {
        Screen.Shelf -> ShelfScreen(vm, onOpenFile = { picker.launch(arrayOf("*/*")) })
        Screen.Reader -> ReaderScreen(vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(vm: ShelfViewModel) {
    val context = LocalContext.current
    val state = vm.reader

    // 先注册的 BackHandler 优先级低：网页能后退时 HtmlView 里的那个先处理，退到头才回书架
    BackHandler { vm.backToShelf() }

    val title = (state as? ReaderState.Ready)?.doc?.name ?: ""

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = vm::backToShelf) { Text("‹ 书架") } },
            )
        },
    ) { padding ->
        val m = Modifier.padding(padding).fillMaxSize()
        when (state) {
            ReaderState.Loading -> Box(m, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is ReaderState.Error -> CenterText("打开失败：${state.message}", m)
            is ReaderState.Ready -> key(state.doc) {
                val html = remember(state.doc) { state.doc.toDisplayHtml(context) }
                HtmlView(html = html, modifier = m)
            }
        }
    }
}

@Composable
fun CenterText(text: String, modifier: Modifier) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) { Text(text) }
}
