# 第 5 步书架与 MVP 验证

日期：2026-10-15。验证设备：API 37 模拟器；未做真机和 API 26 验证。

## 接入与依赖

接入 Shelf.kt、ShelfRepository.kt、ShelfViewModel.kt、ShelfScreen.kt，整体替换 MainActivity，保留 YHViewerTheme 与既有沙箱/Markdown 实现。提供的功能代码仅调整包名和主题包裹，首次编译通过。

- Room 2 稳定线 2.8.5，沿用提供代码的 androidx.room API（未迁移 Room 3）。
- KSP2 2.3.12。KSP2 的版本独立于 Kotlin 编译器；Context7 官方源码说明与当前 AGP 9.4 内置 Kotlin 实际编译均确认可用。
- lifecycle-viewmodel-compose 2.11.0。
- Room/KSP 插件在根构建 apply false 声明，app 应用插件，并配置 schemaDirectory。
- schema：app/schemas/com.example.yhviewer.AppDatabase/1.json，含 sourceUri 唯一索引与 lastOpenedAt 排序索引。

当前目录不是 Git 仓库，因此 schema 已落盘但未提交；未自行初始化 Git。

## 提供的 18 个新测试

| 测试类 | 结果 |
| --- | --- |
| EscapeLikeTest | JVM 4/4 |
| ShelfDaoTest | 设备 7/7，内存数据库 |
| ShelfRepositoryTest | 设备 7/7，临时目录 |

全量常规验证：JVM 共 15 项通过，设备共 25 项通过，0 失败、0 错误、0 跳过。

新增 ShelfUiTest 两个场景，补充搜索/收藏/删除 UI 与旋转/副本/返回链路验证。

## 验收清单

| 项目 | 验证结果 |
| --- | --- |
| 首次空书架 | 正常显示空状态提示，截图 empty-shelf.png；代码先用 null 表示首次查询未返回，避免提前显示空状态 |
| 最近打开排序 | DAO 控制时间排序 + UI 三次导入后的行位置验证均通过 |
| 类型、相对时间、大小 | UI 断言及实际书架截图显示 HTML / MD、相对时间、字节大小 |
| 中英文与大小写搜索 | UI 验证中文、ALPHA；DAO 验证 report 子串大小写不敏感 |
| 50% 与 a_b | JVM 转义、DAO 实际 SQL、UI 输入均通过 |
| 收藏与只看收藏 | UI 星标及筛选通过，DAO 持久字段验证通过 |
| 长按删除 | UI 验证取消不删除、确认后消失；仓库验证记录与副本均删除；原文件仍可读取 |
| 同来源重新导入 | 仓库验证 ID 不变、只保留一条、内容刷新 |
| 删除原文件后打开 | 仓库、UI、实际杀进程验收均通过 |
| 旋转不重复导入 | ActivityScenario.recreate 模拟配置重建，原文件已删除；页面仍显示，整条 Book 元数据与重建前一致、记录仍只有一条 |
| 返回键层级 | 真实点击网页锚点后先退网页，再退书架，再退出应用前台，通过 |
| Markdown 与 HTML | 原有渲染、安全转义、文件切换回归通过 |
| 沙箱测试 | 两次打开均 failed=0，9/9 PASS |
| APK 网络权限 | aapt 清单确认不含 INTERNET |

## 实际杀进程验证

1. 安装最终 APK，确认空书架。
2. 在 Download 创建本轮专用 YHViewer-MVP-check-3bb0b846.md，经 content:// 导入。
3. 保存 Room 数据库与 files/shelf 副本快照，确认记录名称、私有副本及 145 字节内容一致。
4. 使用 am force-stop 杀掉应用，删除上述专用原文件，冷启动应用。
5. 书架记录仍在；点开后 Markdown 显示完整“离线副本验证”内容。
6. 重启后再次保存快照：ID、来源、私有文件名、大小、addedAt 均不变，副本字节逐字一致。

只删除了本轮生成的专用原文件，没有删除用户提供的 hello.html、render-test.md 等文件。最后还导入这几个演示文档并回到书架，供继续体验。

证据：persistence-before.json / persistence-after.json、before-kill.tar / after-restart.tar、persistence-after.png、restarted-shelf.png、final-shelf.png，均位于 build/verification-step5/。

## 构建与审查

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --no-configuration-cache
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:connectedDebugAndroidTest :app:lintDebug --no-configuration-cache
```

均 BUILD SUCCESSFUL。Lint 0 错误，17 项警告，未做无关清理。

按正确性、结构、安全、性能复核：元数据与内容分离；事务串行同来源导入；复制仅写私有目录；LIKE 参数绑定并转义；删除先移除记录；加载保留协程取消语义；原生主题、WebView 释放、CSP 与网络权限保持原实现。验收范围内未发现阻断问题。

不同来源 Uri 可能产生重复条目、删除失败可留下孤儿文件，这两个已知后续缺口保持不变。

MVP 的书架、导入、离线副本、收藏/搜索/删除、阅读器及沙箱路径已完成本轮模拟器验收；未声称完成真机、发布构建或迁移升级测试。
