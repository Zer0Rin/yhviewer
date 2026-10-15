# 第 4 步 Markdown 渲染验证

日期：2026-10-15。设备：API 37 模拟器；未验证真机或 API 26。

## 接入

- 新建 MarkdownRenderer.kt；模板放入 assets/md/template.html。
- 按指定版本打包 markdown-it 14.1.0、highlight.js 11.10.0 与 GitHub 深浅色 CSS，运行时全部本地加载。
- WebViewAssetLoader 增加 /assets/ 路径，CSP、请求拦截及无 INTERNET 权限保持不变。
- LoadedDoc.toDisplayHtml(context) 区分 HTML / Markdown / 未知纯文本；ReaderApp 用 remember 缓存生成结果。
- MD 通过 JSON 数据区传入，所有 < 编码为字面的 \u003c；markdown-it 禁用原始 HTML。
- MIT、BSD-3-Clause 许可证随 assets 打包；版本、下载地址及 SHA-256 见 THIRD_PARTY_NOTICES.md。
- md 目录资源与许可证合计 258342 字节，约 252.3 KiB。

## 测试结果

| 检查项 | 结果 |
| --- | --- |
| 提供的 MarkdownRendererTest | 10/10 JVM 单元测试通过 |
| 标题、粗体、斜体、引用、列表、嵌套列表、表格 | DOM 断言与实际页面显示通过 |
| 裸网址 linkify | 通过 |
| 窄屏表格滚动 | 添加长单元格压力用例，确认 table.scrollWidth > clientWidth 且页面整体不横向溢出 |
| Kotlin / Python 高亮 | 两种语言均被打包支持，实际代码块生成高亮节点 |
| 浅色与深色高亮 | 系统真实切换两轮通过；关键字计算色分别 rgb(215, 58, 73) / rgb(255, 123, 114) |
| 原始 HTML 中 alert | 不执行，渲染区无 script 节点，原文可见 |
| 代码块中的 script 结束标签逃逸 | 不执行，原文可见；JSON.parse 后原文逐字往返一致 |
| 外网图片 | img-src CSP 违规事件已触发，图片 naturalWidth 为 0 |
| Markdown 链接 | 真实点击触发系统浏览器 BROWSABLE ACTION_VIEW |
| 缺失 markdown-it | 实际将 JS 库改名后重建测试 APK；显示“未加载”提示和完整原文，无白屏、无脚本执行 |
| sample.md 回归 | 渲染成标题、粗体等，原始 script 保持文字 |
| hello.html、文件切换、返回键、20MiB 限制 | 回归通过 |
| sandbox-test.html | 两次打开均 9/9 PASS、failed=0 |

最终正常 APK 全量设备测试 9/9 通过，无失败、无跳过；另有浅色、深色和缺库专项各 1 项通过。JVM 除提供的 10 项外，原有示例测试也通过。

## 实际文件与视觉检查

通过 content:// 打开 Download/render-test.md，检查实际页面与高亮。运行期间切换系统深色模式，文件继续显示且配色变化。截图：

- build/verification-step4/render-light.png
- build/verification-step4/code-light.png
- build/verification-step4/render-dark.png

首次启动模拟器出现 System UI 无响应，导致首轮点击测试无窗口焦点；关闭系统提示后重跑通过。测试框架曾将条件跳过写成失败报告，最终测试改为按构建变体明确验证“库存在”或“缺库降级”，最终结果不含跳过或误报。

## 最终交付核对

库文件已恢复。逐字节确认最终 APK 内四个 JS/CSS 文件与下载文件一致，不含 .disabled 文件。aapt 权限清单确认没有 INTERNET。

Lint 无错误，有 17 项警告，未扩展清理原有依赖/资源或渲染进程恢复提示。

最终验证命令：

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:connectedDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-configuration-cache
```

专项测试：MarkdownRenderingIntegrationTest。深色测试先执行 cmd uimode night yes，传 dark=true；缺库测试临时改名 markdown-it.min.js，运行 libraryAvailabilityMatchesBuildVariant 并传 missingMarkdown=true，结束后恢复库并重新构建正常 APK。

原始证据：build/verification-step4/（XML、日志、权限清单、截图）。数学公式和 Mermaid 未加入；书架留待后续步骤。
