# 第 1 步验证记录

日期：2026-10-15
项目：YHViewer（com.example.yhviewer）
设备：Medium_Phone_API_37.0，Android Emulator 37.2.12，API 37

## 接入

- 将提供的 MainActivity.kt 接入，保留项目包名，使用现有 YHViewerTheme。
- hello.html 原样放入 app/src/main/assets/。
- minSdk 设为 26。
- 新增 values-night/themes.xml：原生主题随系统使用深色主题，让 WebView 的 prefers-color-scheme 正确变化。

## 构建

使用 Android Studio 自带 JDK 与当前项目 Gradle wrapper 执行：

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :app:lintDebug
```

结果：BUILD SUCCESSFUL。Lint 无错误，有 19 项警告，主要为模板依赖升级提示、未使用资源及启用 JavaScript 的提醒。

## 模拟器手工验证

| 检查项 | 结果 |
| --- | --- |
| 安装并启动 APK | 通过 |
| 标题、表格 | 正常显示 |
| JS 状态 | 绿色“JS 正常” |
| 连续点击按钮三次 | 显示“已点 3 次” |
| 系统切换深色 | 页面变深色 |
| 系统切回浅色 | 页面恢复浅色 |
| 单页返回键 | 回到系统桌面 |

截图：build/verification/light.png、build/verification/dark-counter.png。

首次启动模拟器时出现过 System UI 无响应及 UIAutomator 超时，系统恢复后完成上述验证；未发现本应用崩溃。验证期间临时降低模拟器分辨率，结束后已恢复分辨率、密度和浅色模式。

## 后续范围

本次仅完成第 1 步。多页面返回历史更新、文件管理器打开 HTML、网络拦截与沙箱测试尚未实现。仅在 API 37 模拟器验证，未做真机或 API 26 运行测试。
