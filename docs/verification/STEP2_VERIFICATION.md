# 第 2 步验证记录

日期：2026-10-15。设备：Medium_Phone_API_37.0，API 37 模拟器。未验证真机及 API 26。

## 接入与调整

- 接入 MainActivity-2.kt、DocumentLoader.kt，保留包名 com.example.yhviewer。
- 保留 YHViewerTheme Compose 包裹及 values-night 原生主题。
- 只替换清单的 Activity 部分，使用 singleTop 和 VIEW/SEND MIME 过滤器。
- 应用在“打开方式”列表中的名字为 YHViewer。
- 为解决 API 37 测试框架 InputManager.getInstance 不兼容，仅升级 AndroidX 测试 JUnit 至 1.3.0、Espresso 至 3.7.0。其他依赖保持原状。

## 检查结果

| 项目 | 结果与证据 |
| --- | --- |
| Files → 长按 hello.html → Open with → YHViewer | 正常打开，标题为 hello.html，绿色 JS 正常。截图 build/verification-step2/html.png |
| 顶栏“打开” → 系统选择器 → sample.md | 正常打开，顶栏真实文件名，保留 #、** 等 Markdown 符号。截图 build/verification-step2/markdown.png |
| Markdown 中的脚本 | 页面按字面显示完整 script 标签，无弹窗。设备测试进一步断言 document.scripts.length == 0，pre 中保留脚本文本，排除仅因缺少 WebChromeClient 而没有弹窗的假通过 |
| 应用已打开时接收另一份文件 | 设备测试以 ACTION_VIEW 切到 sample.md，再以 ACTION_SEND 切回 hello.html；断言 Activity 为同一个实例、标题更新、旧文档计数和 WebView 历史不残留 |
| 返回历史 | 真实点击页面锚点链接后，返回键先回到原页面位置，Activity 未结束 |
| 超大文件 | 通过真实 content:// 流读取 20MiB+1 字节文件，返回“文件超过 20MB” |

测试文件：app/src/androidTest/java/com/example/yhviewer/ReaderIntegrationTest.kt。
Markdown 测试素材：app/src/androidTest/assets/sample.md。

## 执行结果

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :app:lintDebug
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.yhviewer.ReaderIntegrationTest
```

构建与 Lint：BUILD SUCCESSFUL。
设备集成测试：3 项通过，0 失败，0 错误。
已有 JVM 示例测试也执行通过。

测试排查：旧 Espresso 在 API 37 上失败，升级测试依赖后恢复；测试用 MediaStore 在 text/plain 下将文件名自动改为 sample.md.txt，改用 Markdown MIME 创建测试素材。返回测试改用实际点击链接，避免无用户操作的 JavaScript 历史写入影响判定。应用的 loadDataWithBaseURL 参数最终保持提供版本。

## Lint 警告

以下为本轮报告的实际条目，未进行批量清理：

| ID | 信息 |
| --- | --- |
| AndroidGradlePluginVersion | A newer version of Gradle than 9.6.0 is available: 9.8.1 |
| AndroidGradlePluginVersion | A newer version of com.android.application than 9.4.0 is available: 9.4.1 |
| GradleDependency | A newer version of androidx.core:core-ktx than 1.10.1 is available: 1.19.1 |
| GradleDependency | A newer version of androidx.test.ext:junit than 1.1.5 is available: 1.3.0 |
| GradleDependency | A newer version of androidx.test.espresso:espresso-core than 3.5.1 is available: 3.7.0 |
| GradleDependency | A newer version of androidx.lifecycle:lifecycle-runtime-ktx than 2.6.1 is available: 2.11.0 |
| GradleDependency | A newer version of androidx.activity:activity-compose than 1.8.0 is available: 1.13.0 |
| GradleDependency | A newer version of androidx.compose:compose-bom than 2026.02.01 is available: 2026.09.00 |
| NewerVersionAvailable | A newer version of org.jetbrains.kotlin.plugin.compose than 2.2.10 is available: 2.4.21 |
| SetJavaScriptEnabled | Using `setJavaScriptEnabled` can introduce XSS vulnerabilities into your application, review carefully |
| ObsoleteSdkInt | This folder configuration (`v26`) is unnecessary; `minSdkVersion` is 26. Merge all the resources in this folder into `mipmap-anydpi`. |
| UnusedResources | The resource `R.color.purple_200` appears to be unused |
| UnusedResources | The resource `R.color.purple_500` appears to be unused |
| UnusedResources | The resource `R.color.purple_700` appears to be unused |
| UnusedResources | The resource `R.color.teal_200` appears to be unused |
| UnusedResources | The resource `R.color.teal_700` appears to be unused |
| UnusedResources | The resource `R.color.black` appears to be unused |
| UnusedResources | The resource `R.color.white` appears to be unused |

报告：app/build/reports/lint-results-debug.html。

## 保留的后续范围

相对路径资源、外部网络链接策略、沙箱拦截留给第 3 步；Markdown 渲染留给第 4 步。
