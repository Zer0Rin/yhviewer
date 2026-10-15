# 第 3 步沙箱验证记录

日期：2026-10-15。设备：API 37 模拟器。未验证真机或 API 26。

## 接入

- 新建 SandboxWebView.kt，保留项目包名，删除 MainActivity.kt 中旧 HtmlView、BASE_URL 及无用 import。
- ReaderApp 与深浅色主题保留。
- 引入 androidx.webkit:webkit:1.17.1：依据 Google Maven 元数据选择最新正式稳定版，未使用 1.18.0-rc01。
- 提供的沙箱逻辑未作功能性改写，测试页原样放在 androidTest/assets；模拟器 Download 中也有测试文件。

## 有网络权限的一轮

临时在 app/src/debug/AndroidManifest.xml 声明 INTERNET；测试进程先断言目标应用已获权限，再用原生 HTTPS 请求作正向验证，https://example.com/ 返回 HTTP 200。

随后读取 window.__sandboxResult，首次打开和关闭后重新创建页面均为 failed=0、9/9 PASS。没有把外网不可达或权限缺失当成沙箱成功。

沙箱专项 3 个设备测试全部通过：测试页与重开隔离、独立请求拦截与 iframe CSP、用户点击与自动跳转。

## 9 项结果

| 检查 | 临时有 INTERNET | 最终无 INTERNET |
| --- | --- | --- |
| JS 正常运行 | PASS | PASS |
| 外网图片 | PASS | PASS |
| fetch 外网 | PASS | PASS |
| fetch file:// | PASS | PASS |
| fetch content:// | PASS | PASS |
| WebSocket | PASS | PASS |
| 外部 iframe | PASS | PASS |
| localStorage 文档间隔离 | PASS | PASS |
| CSP 头已生效 | PASS | PASS |

两轮均重开一次，9 项仍全部通过。每次测试页报告 6 次 CSP 违规拦截。

## 补充验证

- 请求拦截器独立验证：外部 HTTPS、file://、content://、虚拟域名不存在的资源、相似恶意域名，真实 SandboxClient 均返回 403。通过直接调用真实客户端回调验证这一层，避免 CSP 提前阻断使回调没有机会执行。
- 原测试页的 iframe 检查使用全局 CSP 违规计数，可能被前面其他请求的违规满足；额外增加只针对 frame-src 和 example.com 的违规事件断言，已通过。
- 5 秒脚本自动导航：页面仍为虚拟文档 URL，日志 gesture=false；不会发出外部浏览器 Intent。
- 用户真实点击：设备测试验证会发出 BROWSABLE ACTION_VIEW；再在实际测试页手动点击确认切到 com.android.chrome。模拟器 Chrome 尚未完成首次启动设置，因此只确认成功移交浏览器，未完成浏览器内网站加载。
- Sandbox 标签有请求 403 与 blocked navigation 日志。CSP 提前阻断的请求不会进入 shouldInterceptRequest，因此不会每一条都出现在 Sandbox 标签；这部分以 securitypolicyviolation 事件验证。
- hello.html、sample.md、真实文件名、singleTop 文件切换、返回历史、20MiB 超限拒绝：ReaderIntegrationTest 3 项回归全部通过。
- 实际以 content:// 打开 sandbox-test.html，页面显示“全部 9 项通过”，截图见 build/verification-step3/sandbox.png。

## 最终交付检查

临时 debug Manifest 已删除。重新构建并测试无 INTERNET 的 APK；测试代码断言权限不存在，aapt dump permissions 也确认不存在 android.permission.INTERNET。

有权限专项 3/3、无权限专项 3/3、最终回归 3/3 均通过；Lint 无错误，17 项警告。SetJavaScriptEnabled 已按提供版本附注释豁免；新 WebKit 检查另提示 MissingOnRenderProcessGone（渲染进程崩溃恢复），暂未扩展实现，其余为模板依赖/资源等提示。

## 复现

测试源码：app/src/androidTest/java/com/example/yhviewer/SandboxIntegrationTest.kt。

临时添加仅含 INTERNET 权限的 debug Manifest 后：

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:connectedDebugAndroidTest --no-configuration-cache -Pandroid.testInstrumentationRunnerArguments.class=com.example.yhviewer.SandboxIntegrationTest -Pandroid.testInstrumentationRunnerArguments.expectInternet=true
```

删除临时 Manifest，重跑 expectInternet=false，再单独运行 ReaderIntegrationTest。最终构建与 Lint：

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :app:lintDebug
```

证据目录：build/verification-step3/，包含两轮结果 XML、原始日志、sandbox-results.json、最终 APK 权限清单及截图。

相对路径资源与 CDN 脚本限制按本步骤设计保留。
