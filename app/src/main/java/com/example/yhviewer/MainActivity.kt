package com.example.yhviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.example.yhviewer.ui.theme.YHViewerTheme

class MainActivity : ComponentActivity() {

    private val vm: ShelfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

    // 进入后台时立刻保存阅读位置：之后进程可能随时被系统回收
    override fun onStop() {
        super.onStop()
        vm.flushPosition()
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

@Composable
fun CenterText(text: String, modifier: Modifier) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) { Text(text) }
}
