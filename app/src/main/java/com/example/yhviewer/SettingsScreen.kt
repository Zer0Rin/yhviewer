package com.example.yhviewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * v16：设置页（书架右上角的齿轮进入）。目前展示版本、作者、项目主页和隐私说明。
 * 以后有真正的设置项（默认主题、导入行为等）再加分组。
 */
object AboutInfo {
    const val AUTHOR = "Zer0Rin"
    const val HOMEPAGE = "https://github.com/Zer0Rin/yhviewer"
    const val ISSUES = "https://github.com/Zer0Rin/yhviewer/issues"

    /** 打包校验用的许可证文件清单；设置页暂时不展示。 */
    data class License(val name: String, val license: String, val asset: String)

    val LICENSES = listOf(
        License("YHViewer", "MIT", "about/YHViewer-LICENSE.txt"),
        License("markdown-it", "MIT", "md/licenses/markdown-it-LICENSE.txt"),
        License("markdown-it-footnote", "MIT", "md/licenses/markdown-it-footnote-LICENSE.txt"),
        License("highlight.js", "BSD-3-Clause", "md/licenses/highlight.js-LICENSE.txt"),
        License("CodeMirror 6", "MIT", "edit/LICENSES.txt"),
        License("Manrope 字体", "SIL OFL 1.1", "fonts/licenses/Manrope-OFL.txt"),
        License("Pyodide", "MPL-2.0", "pyodide/LICENSE.pyodide"),
        License("CPython", "PSF License", "pyodide/LICENSE.cpython"),
    )

    /** 版本行，例如 "版本 0.9.0 · Standard 版" */
    fun versionLine(versionName: String, flavor: String): String {
        val edition = when (flavor.lowercase()) {
            "strict", "strictrc" -> "Strict 版"
            "standard", "standardrc" -> "Standard 版"
            else -> flavor
        }
        return if (edition.isBlank()) "版本 $versionName" else "版本 $versionName · $edition"
    }

    /** 隐私与安全说明。按版本区分联网能力，和 AppCapabilities 一致 */
    fun privacyLines(networkCapable: Boolean, pythonAvailable: Boolean): List<String> = buildList {
        add("文档只保存在这台手机上，不会上传到任何地方。")
        add("网页在沙箱里显示：不能读取手机上的其他文件，书籍里的脚本一律不执行。")
        if (networkCapable) {
            add("网页默认不能联网；需要时可以在网页的「权限」里单独为这一个文档打开。")
        } else {
            add("Strict 版没有网络权限，任何内容都无法联网。")
        }
        if (pythonAvailable) add("Python 代码在本机运行，不会把代码发到服务器。")
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val yh = LocalYH.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(yh.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 32.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 书架") }
        }
        Text(
            "设置",
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 20.dp),
            style = MaterialTheme.typography.headlineLarge,
            color = yh.fg,
        )

        // ---------- 应用 ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 自适应图标不能直接用 painterResource，按启动器的样子拼：深色底 + 前景
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF262624)),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(64.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("YHViewer", style = MaterialTheme.typography.titleLarge, color = yh.fg)
                Text(
                    AboutInfo.versionLine(BuildConfig.VERSION_NAME, BuildConfig.FLAVOR),
                    style = MaterialTheme.typography.bodySmall,
                    color = yh.muted,
                )
            }
        }
        Text(
            "安全的本地阅读器：HTML、Markdown、代码、TXT 和 EPUB。",
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = yh.fg,
        )

        // ---------- 关于 ----------
        SectionTitle("关于")
        InfoRow("作者", AboutInfo.AUTHOR)
        InfoRow("项目主页", "github.com/Zer0Rin/yhviewer") { openUrl(context, AboutInfo.HOMEPAGE) }
        InfoRow("反馈问题", "在 GitHub 上提交") { openUrl(context, AboutInfo.ISSUES) }

        // ---------- 隐私与安全 ----------
        SectionTitle("隐私与安全")
        AboutInfo.privacyLines(AppCapabilities.networkCapable, AppCapabilities.pythonAvailable).forEach { line ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text("·", color = yh.accent, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(10.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium, color = yh.fg)
            }
        }

    }
}

@Composable
private fun SectionTitle(text: String) {
    val yh = LocalYH.current
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 6.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = yh.subtle)
        Spacer(Modifier.size(6.dp))
        HorizontalDivider(color = yh.lineSoft)
    }
}

/** 一行：左边名称，右边值；可点时值用强调色，后面带 › */
@Composable
private fun InfoRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    val yh = LocalYH.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = yh.fg, modifier = Modifier.weight(1f))
        Text(
            if (onClick != null) "$value ›" else value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) yh.accent else yh.muted,
        )
    }
}

/** 用系统浏览器打开（Strict 版没有网络权限，但交给浏览器打开不受影响） */
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "没有可以打开链接的应用", android.widget.Toast.LENGTH_SHORT).show()
    }
}
