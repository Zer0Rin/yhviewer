package com.example.yhviewer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
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

class MainActivity : ComponentActivity() {

    private val vm: ShelfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 三键导航时，enableEdgeToEdge() 默认在导航栏后面放一层半透明白色遮罩（截图里底部那条白带）。
        // 内容已经自己处理了 inset：这里把系统栏设成全透明并关掉对比度遮罩，让底色一直铺到屏幕底部。
        // 图标深浅不在这里定，由 YHTheme 按当前色板设置。
        val transparent = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = transparent, navigationBarStyle = transparent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            YHTheme(vm.theme) { AppRoot(vm) }  // 外壳颜色跟随阅读主题
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    // 进入后台时立刻保存阅读位置：之后进程可能随时被系统回收
    override fun onStop() {
        super.onStop()
        vm.flushPosition()
    }

    /** v10：分享多个文件（ACTION_SEND_MULTIPLE）走批量导入 */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.filterNotNull()?.let(vm::importMany)
            return
        }
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
    // v10：可以一次选多个文件；只选一个时和以前一样直接打开
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importMany(uris)
    }
    // v10：导入整个文件夹（只读一次，不申请持久授权）
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree?.let(vm::importFolder)
    }
    // v17：书架、阅读页、设置页之间新页面 180ms 淡入（不位移：这是一天几十次的操作，要轻）。
    // 旧页面立即移除，不和新页面同时存在，阅读页的 WebView 不会多活一段时间。
    val screen = vm.screen
    Box(Modifier.fillMaxSize().background(LocalYH.current.bg)) {
        key(screen) {
            val appear = remember { Animatable(0f) }
            LaunchedEffect(Unit) { appear.animateTo(1f, tween(Motion.SCREEN_MS, easing = Motion.EaseOut)) }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value }) {
                when (screen) {
                    Screen.Shelf -> ShelfScreen(
                        vm,
                        onOpenFile = { picker.launch(arrayOf("*/*")) },
                        onOpenFolder = { folderPicker.launch(null) },
                    )
                    Screen.Reader -> ReaderScreen(vm)
                    Screen.Settings -> SettingsScreen(onBack = vm::closeSettings)
                }
            }
        }
    }
}

@Composable
fun CenterText(text: String, modifier: Modifier) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) { Text(text) }
}
