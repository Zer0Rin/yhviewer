# YHViewer 1.0.0 真机验收报告

## FIX4收尾结论（2026-10-23，当前结论）

**指定验收通过，已停止并等待用户决定发布。** FIX4新提交`868a598cd4ec15bd81e7a356b7dfbcabaee3e6bc`，仅提交补丁3文件。新增2项测试先在旧实现失败；应用补丁后四个debug变体各268项、共1072项单测全部通过，`node scripts/test-flow-shell.cjs`通过。四个Release APK重建成功；仅覆盖安装`.rc.strict`/`.rc.standard`，版本仍1.0.0/12，正式/Preview/QA未动。

- **4.6.2：两个RC通过。** F4连续模式从第三章点“返回脚注”，顶栏显示时“脚注内容”完整位于顶栏下方；跳转前后截图均已目视核对，原连续模式恢复。
- **5.9：两个RC通过。** R4-copy-lines.kt从alpha选到第三行末尾，系统Copy后原生Paste；实际编辑器画面均保留中间空行，没有代码行号。全选也保留空行，并按设计带上“Kotlin · 3 行”。所有临时Scratch编辑均不保存。该WebView编辑器的UI树文本未完整保留连续换行，初版自动断言因此误报；判定以实际粘贴画面为准，原始UI树和截图同时保留，不把UI树字符串冒称原始剪贴板字节。
- **日常试用通过，无新失败。** Strict约303秒，Standard约304秒：打开书与文档、滚动/翻页、目录跳转、切换并恢复主题、各导入一份合成笔记。未观察到崩溃、白屏或明显错乱；未录屏、未逐帧分析，未扩展为新的测试矩阵。
- **改判保持：** 4.5.3为已知问题，不阻塞1.0；9.4及3.3（EPUB原文件名）按设计通过；4.4.1为不阻塞发布的视觉细节。TalkBack取消，其余未测不再补测；用户已确认的设置页外链/旧版升级/Standard首次空书架沿用。

| FIX4 APK | 状态 | SHA-256 |
|---|---|---|
| strict | 未签名构建，未安装 | `40e61d10bf359ad13911c6f3a1a2ea5e1d828b92b65356eb0cc9461fb5688134` |
| standard | 未签名构建，未安装 | `5b13a4e33d798aae39d2cf2c9aa9e0dc914077e355c16594e4c3b102d97dd9a5` |
| strictRc | RC测试证书签名，已覆盖安装 | `c6e776f1c801bf378614d54dc47877a8d3128eff992098c06e91dbbd46089c8e` |
| standardRc | RC测试证书签名，已覆盖安装 | `d6ae062b1264b447e8851c1728e6f94624fcb6ebd4b16afd41189bd060d43049` |

两个RC沿用原测试证书2677ad64…，签名口令仅作为签名子进程环境变量传递，未另存口令。正式两包本地无正式签名配置，仍为未签名构建，不能把它们当作已签名发布产物。规范路径及哈希在`build/device-1.0/fix4/four-apks.json`、`SHA256SUMS.txt`，APK在`fix4/artifacts/`。

构建源码仍是FIX4提交叠加六个原有继承改动，未重置或混入本次提交；`fix4/source-before.json`、`inherited-working-tree.patch`、`inherited-sources/`及`source-state.json`归档来源，反向应用FIX4可精确恢复补丁前源码。后续若由CI发布，需纳入这六个继承改动以匹配本次验收源码；本轮不额外整理提交。真机证据在`build/device-1.0/continuation-20261022/r11-*`。

收尾审计：5757行两RC UID日志无FATAL/ANR匹配，退出无reason4/5/6；系统12+3项、源码、四APK及两个已装RC哈希核对一致，正式/Preview/QA版本和安装更新时间不变。日志已停止、临时UI XML删除，两RC回书架F4，Strict前台/fullscreen，原主题和视图恢复，测试源及副本保留。详见`r11-crash-anr-final-check.json`。

**按FIX4收尾说明，本轮到此停止：未推送、未打标签、未发布，等待用户决定。** 此安排覆盖第17节此前“通过后进入发布”的计划；历史章节以下按采集当时原样保留，其旧状态不覆盖本段结论。

---


**最新状态（2026-10-23，用户冻结范围）：停止扩大范围，剩余未测项不再补测。4.5.3为不阻塞1.0的已知问题，9.4及3.3的EPUB原文件名搜索按设计通过，4.4.1为不阻塞发布的视觉细节。仅4.6.2、5.9等待FIX4修复后重测，再对两个RC各做5分钟日常试用；没有新失败则进入发布步骤。当前不改代码、不发布，详见第17节。**

**历史状态（FIX2及此前；当前以第14节为准）：2026-10-22 已恢复续测；验收未完成，已确认章节/脚注定位、进程恢复及代码范围复制丢空行，无法据此发布 1.0.0。** 安全门（6.1）已完成且未发现安全失败。FIX1 基于 `release/1.0.0` 的提交 `1e9d294b975a08e3e6e1bf93a06a4b185ab30628`，代码改动与 FIX2 测试改动都在未提交工作树中。FIX2 后四个 debug 变体完整单测各 266 项、0 失败；阅读器 shell 测试通过。两个 RC 用 `build/` 下临时测试密钥签名并安装，未触碰正式主包、Preview 或 QA 包。连续阅读边界经用户手动复核并按顶部栏位置复测：章节标题尚在顶部栏下方时维持上一章，标题滚到顶部栏覆盖的切换线后标题和计数同步更新，符合预期，不计失败。编码设备复测通过；FIX1 后续测中章节链接在连续模式和单章模式均跳转到目标章；续测确认两个 RC 连续模式的脚注被顶栏遮挡、F4 目录第三章主动跳转落在第二章；后台进程被回收后从应用入口返回书架，未直接恢复阅读页（9.4）。另有性能及系统用例未完成，因此目前仍不能据此发布。

日期：2026-10-21；最近续测：2026-10-23（Asia/Taipei）

## 1. 设备

| 项目 | 值 |
|---|---|
| 设备 | Redmi Note 12 Turbo，型号 23049RAD8C |
| Android | 15，API 35 |
| 屏幕 | 1080 × 2400，440 dpi，最高刷新率 120 Hz |
| WebView | `com.google.android.webview` 153.0.8010.39 |

## 2. 构建和 APK

| 项目 | 值 |
|---|---|
| 分支 / 提交 | `release/1.0.0` / `1e9d294b975a08e3e6e1bf93a06a4b185ab30628` |
| 签名证书 SHA-256 | `7d04d1564661330df2029624151d50826c3bcc0ada217d77b5889ae411ef1794` |
| Standard 正式 APK SHA-256 | `ab3a7d9ac567bc23576f04b22ab8e15d4943a6b19585c4a2f1fd2b31b866ece4` |
| Strict 正式 APK SHA-256 | `735f7702e0b6b1e7877bbad83d087000782a3f1f795eb317cd2db2c43a311eb7` |
| Standard RC applicationId | `io.github.zer0rin.yhviewer.rc.standard` |
| Strict RC applicationId | `io.github.zer0rin.yhviewer.rc.strict` |
| 真机上 YHViewer 相关包 | `io.github.zer0rin.yhviewer`、`io.github.zer0rin.yhviewer.preview`、`io.github.zer0rin.yhviewer.qa.test`、两个 RC 包 |
| Standard RC APK SHA-256 | `d029745c0af56f9bf853a6b997089f42c09c8a3f311f503626f4b8a4dcf1f361` |
| Strict RC APK SHA-256 | `7707d465db202e27f276ab0b78a62bb242cceed881b57d76815ff19849bef070` |

本节列出的是首轮验收使用的四个 APK：均为 `versionName=1.0.0`、`versionCode=12`，签名验证通过且签名证书一致。APK 及签名核对文件在 git 忽略目录 `build/device-1.0/artifacts/`。最终包的 API 34 R8 CI 检查已通过；本报告不把 CI 结果当作真机功能验收。FIX1 正式 APK 和 RC APK 的未签名构建结果见下表；fix2 后重建的 RC 包另列。

### FIX1 补丁与重建结果

| 项目 | 结果 |
|---|---|
| 源码基线 | `release/1.0.0`，`1e9d294b975a08e3e6e1bf93a06a4b185ab30628`；FIX1 当前为未提交工作树改动。补丁 SHA-256：`a1a14ce00fac19139ab9f0a309cad7a23ac20dbe59770da63fc04ac1f70b7a76`。 |
| 版本 | 四个变体均为 `1.0.0` / `versionCode=12`。 |
| Standard 正式 APK | 未签名；SHA-256 `690315bad919725426b70ae3c8876843431d76bd9534c29d7249dd9c4cc54a0c`。 |
| Strict 正式 APK | 未签名；SHA-256 `bbc7eb64362806ab8dd43a18ac190a26fcdc33ba2ff322236b2fe3d59dfba2fb`。 |
| Standard RC APK | 未签名；SHA-256 `8023370093f19114bb3a8bd8bd4f673f72d20e6132b896c0f690e65c084b333a`。 |
| Strict RC APK | 未签名；SHA-256 `1155669e7a260e2e58039b79e61683b8b150114c7d67993b329a13dae2b90382`。 |
| 安装与签名 | 本地正式签名密钥未配置。用户授权只卸载旧 `.rc.standard`、`.rc.strict` 测试包；新 RC 以 `build/device-1.0/fix2-test/rc-test.jks` 临时测试密钥签名，证书 SHA-256 `2677ad64917b19a529be56834234a8bca526f8db176a1025fca24035c121ca86`。正式主包、Preview、QA 包未卸载或改动。 |
| RC APK | FIX2 后两个 RC 为 `1.0.0` / `versionCode=12`；Standard RC SHA-256 `e01c844f1fe098096ec5bd60b0f40299edec584f6d497a95ab0a65c52035e215`；Strict RC SHA-256 `57e0395e3d7970b78156b98dc4747d9088f4e42838f52cdadde35862e00d0dc3`。签名验证通过，二者证书相同。 |
| 构建来源 | 正式 APK 与两个 RC 来自相同源码基线提交 `1e9d294b975a08e3e6e1bf93a06a4b185ab30628` 和相同 FIX1 应用代码；FIX2 只改 `CharsetMemoryRegressionTest.kt`，不改 APK 应用源码。FIX1/FIX2 尚未提交。 |
| FIX2 补丁 | SHA-256 `4205f04560a4bb4990a30159df98f5be96f627904f575dc2965daed7a2f23c22`；仅调整 `CharsetMemoryRegressionTest` 对旧错误解码预期的断言。 |
| 完整单测 | `testStandardDebugUnitTest`、`testStrictDebugUnitTest`、`testStandardRcDebugUnitTest`、`testStrictRcDebugUnitTest` 各 266 项，均 0 失败 / 错误。 |
| 阅读器 shell | `node scripts/test-flow-shell.cjs` 通过；这是宿主 DOM 模型，不代替真机 WebView 验证。 |

### FIX2 后设备重测

F6 GBK、Big5、无 BOM UTF-16LE、带 BOM UTF-16LE 在 Standard 与 Strict RC 上均显示预期中文，无乱码。截图在 `build/device-1.0/fix2-test/`。为测试带 BOM 文件，在用户测试目录 `staging/fixtures/` 新增了单个 UTF-16LE BOM fixture。

F4 连续模式中下一章正文先进入可视区域、顶栏与章节计数继续显示当前章。用户手动确认这是符合连续阅读预期的表现，因此 4.1 不作为失败项。相关截图和日志保留作复核记录：`build/device-1.0/fix2-test/standard-F4-flow-mismatch.png`、`standard-F4-flow-mismatch.xml`、`standard-F4-YHFlow.log`。截图 SHA-256：`091d43e12c502ff32e2101882d868c3968b168fb9b8585e74e2c97fe9df8cb0d`；日志 SHA-256：`6386655c1ac8fa58137a1933edac14781c638d176e9c2c292d6c331b437ecdb9`。

新 RC 安装前按用户授权卸载了两个旧 RC 包，因此这两个包原有的本地验收数据已清除；设备上的测试素材目录仍保留。主包、Preview、QA 包没有卸载、启动或改动。当前不创建提交、不推送、不发布。

## 3. 主包现状和安装

测试开始时，`io.github.zer0rin.yhviewer` 主包未安装（`dumpsys package` 返回未安装）。用户确认没有安装 0.10.0，小说文件在手机存储中；没有搜索、打开或导入该小说。依照方案对“没有安装主包”的处理，只做了 Standard 1.0.0 全新安装，没有卸载或清除任何应用数据。`preview` 和 `qa.test` 包也在包清单中；均视为可能含用户数据的包，只读检查，没有启动、卸载或清理。安装后主包版本为 1.0.0 / code 12，书架为空；因此升级前后书架、分组、收藏、阅读进度和设置对比，以及用户小说的只读计时均不适用，不能记为通过。

## 4. 用例结果

| ID | 结果 | 证据 | 备注 |
|---|---|---|---|
| 0.2 | 通过（现状核查） | `build/device-1.0/main-first-launch.xml` | 主包原先未安装；按方案做全新安装。 |
| 1.1 | 通过 | `build/device-1.0/apksigner-*.txt`、`build/device-1.0/artifacts/` | 四个 APK 签名、版本、权限核对通过；Strict 无 INTERNET 权限，Strict 不含 Python/Pyodide 资源，Standard 有 INTERNET 权限及 Python 资源。 |
| 1.2 | 阻塞 | `build/device-1.0/main-first-launch.png`、`main-settings.png` | 无升级前的主包数据可供比较。 |
| 1.3 | 通过 | `build/device-1.0/main-first-launch.xml` | Standard 正式包安装成功。 |
| 1.4 | 部分通过 / 阻塞 | `build/device-1.0/main-settings.png`、`main-settings.xml` | 设置页版本行显示 1.0.0 · Standard 版，开源许可一栏未显示；数据保留对比不适用。 |
| 1.5 | 通过 | `build/device-1.0/main-startup.txt` | 冷启动 TotalTime：510、406、399、414、351 ms；中位数 399 ms。 |
| 1.6–1.8 | 阻塞 | — | 主包原先无用户书籍；用户大 TXT / EPUB 计时和返回位置无法测。 |
| 2.1 | 部分通过 | `build/device-1.0/strict-rc-first-launch.png`、`strict-rc-first-launch.xml` | Strict RC 首次空书架已确认；Standard RC 已安装但未完成首次界面检查。 |
| 2.2 | 部分通过；FIX1 设备复测编码项通过 | `build/device-1.0/fix2-test/standard-GBK.png`、`standard-big5-open.png`、`standard-UTF16LE.png`、`standard-UTF16LE-BOM.png`、`strict-GBK.png`、`strict-Big5.png`、`strict-UTF16LE.png`、`strict-UTF16LE-BOM.png` | F6 的 GBK、Big5、无 BOM UTF-16LE、带 BOM UTF-16LE 在两个新 RC 均显示正常。F1 目录其余文本格式仍未重测。 |
| 2.3 | 导入及数量通过（两个 RC）；进度显示未捕获 | `build/device-1.0/continuation-20261021/r2-{strict,standard}-picker-ten-selected.xml`、`r2-{strict,standard}-picked-ten.json`、`r2-{strict,standard}-multiselect-ten-import-complete.png`、`r2-picker-view-baseline.json` | 经“＋ 打开 → 选择文件”，在已核实的测试目录内选择同一组10个TXT/Markdown/代码/数据文件；系统显示10 selected，两个RC完成报告均为“已导入 10 项”。未以完成报告代替导入中进度的验证。选择器临时切列表以便操作，已恢复原网格视图。 |
| 2.3（r4进度续测） | 通过（两个RC） | `build/device-1.0/continuation-20261022/r4-{strict,standard}-batch-ten-selected.xml`、`r4-{strict,standard}-batch-progress.mp4`、对应`-overview.jpg`/`-result.png`；脚本`r4_batch_progress.py`列出精确10项 | 原生文件选择器选择10个指定测试文件（含F2大TXT），录像拍到书架底部“正在导入6/10”“正在导入7/10”及当前F2文件名，随后完成报告均10项。补足上轮缺失的导入中进度证据；选择器临时切列表，结束恢复网格。 |
| 2.10 | 通过（两个 RC，单文件意图路径） | `build/device-1.0/continuation-20261021/F15-origin.txt`、`strict-type-H02-single-early.png`、`strict-type-H02-single.xml`、`standard-type-H02-single-early.png`、`standard-type-H02-single.xml` | 为避免复用已有带资源条目，把 F1/H02 原始 HTML 逐字节复制为 F15-H02-single.html 单独导入，不带 assets。两个 RC 顶部均列出 assets/layout.css、assets/reading-card.svg 缺失，并建议使用“导入文件夹”。 |
| 2.2（续测格式） | 打开功能通过（两个 RC）；文件选择器路径未测 | `build/device-1.0/continuation-20261021/strict-type-*.png`、`standard-type-*.png` | 通过测试素材的 MediaStore URI 打开 LOG、XML、CSV、Kotlin 和无标题 TXT，正文或代码可见；LOG 显示“纯文本 100 行”，含空格、全角括号的文件名正常。长无标题 TXT 显示“第 1 部分 / 1 / 28”，小文件为单段“正文”；末尾搜索已由4.7.2续测覆盖。SVG 图片路径已由 5.12 覆盖。 |
| 2.4 | 文档/资源数量及显示通过（两个 RC）；F1 根内无 zip | `build/device-1.0/continuation-20261021/r2-{strict,standard}-F1-root-import-report.png`、`r2-strict-F1-exact-root-picker.xml`、`r2-{strict,standard}-H02-folder-assets.png`；首轮 `build/device-1.0/strict-reimport-result.png` | 两个RC均经系统目录选择器选测试目录下 `staging/F1/Android书架测试文件_30例`，报告“已导入 34 项，带上 2 个图片 / 样式等资源”；H02繁体正文、外部CSS绿框/浅绿底和SVG卡片图均正常。所提供F1独立根内没有zip，故此路径不能验证跳过zip；首轮Strict选择整个测试目录时另有跳过2项不支持格式的报告（60文档/4资源的较大口径）。 |
| 2.5 | 通过（Strict RC） | `build/device-1.0/h02-favorited.xml`、`h02-grouped.xml`、`h02-scrolled-strict.png`、`h02-after-reimport.png`、`strict-reimport-result.png` | 重导入前为 H02 收藏 1 项、`rc-retention` 分组 1 项，并已滚至中段。再次选择同一目录，报告显示导入 60 项、跳过 2 项不支持格式；重开 H02 后阅读位置仍在相同段落附近，收藏与分组数量仍为 1。Standard RC 见下一行续测。 |
| 2.5（Standard 续测） | 通过 | `build/device-1.0/continuation-20261021/r2-standard-H02-favorite-group-{before,after}.xml`、`r2-standard-H02-middle-{before,after}-reimport.png`、`r2-standard-H02-reimport-report.png`、`r2-standard-H02-reimport-comparison.jpg` | H02新增收藏并移入临时分组rc-r2-retain，阅读至第4–8段；重导入同一F1独立根后仍收藏1项/分组1项，正文区域逐像素相同。报告34文档/2资源。验证后删除临时分组、取消本轮收藏，保留文档。 |
| 2.8 | 部分通过（两个 RC） | `build/device-1.0/f2-open-strict.png`、`f2-open-standard.png`、`f2-search.xml` | 30 MiB / 4000 章 F2 在 Strict 和 Standard RC 均成功打开，目录显示“1 / 4000”；热启动 `VIEW` 的 Activity TotalTime 分别为 108 ms、137 ms，约 2 秒时正文可见。尚未单独计时重复打开，且未测导入进度提示。 |
| 2.9 | 通过（Strict、Standard RC） | `build/device-1.0/f12-over-limit-strict.png`、`strict-after-f12-shelf.xml`、`f12-over-limit-standard.png`、`f12-over-limit-standard.xml`、`standard-after-f12-search.xml` | 两个 RC 对测试 F12 单文件 `VIEW` 均显示“打开失败：文件超过 32MB”，无崩溃；返回两个 RC 的书架搜索 F12 均为 0 项，没有半截条目。用户确认文件夹导入的 2 个跳过项均为不支持格式；这与单文件 F12 超限错误测试分别记录。 |
| 2.11 | 通过（Standard、Strict RC） | `build/device-1.0/continuation-20261021/deleted-generated-sources.json`、`strict-private-copy-filtered-after-force-stop.xml`、`strict-private-copy-reopened-after-source-delete.png`、`standard-private-copy-filtered-after-force-stop.xml`、`standard-private-copy-reopened-after-source-delete.png` | 新生成F14 a/b两个Markdown测试文件并分别导入两个RC，按SHA-256确认是本轮文件后只删除对应测试源文件。两个RC各force-stop再冷启，恢复F14筛选后从书架打开a，正文仍显示SOURCE_DELETE_REOPEN_A，验证来自应用私有副本。首次脚本因搜索词不跨force-stop保存而未找到条目；恢复精确筛选后完成验证，不属于打开失败。 |
| 2.6 | 部分通过 | `build/device-1.0/share-one-strict.png`、`share-one-strict-with-data-uri.png` | Strict RC 单文件 `ACTION_SEND` 测试成功。仅把 URI 放在 `EXTRA_STREAM` 时系统没有把读取权授予接收应用；把同一测试 URI 同时放在 Intent data 和 stream extra、并带读取标志后可打开。多文件原生分享见下一行续测。 |
| 2.6（三文件原生分享续测） | 通过（两个RC）；单文件见下行续测 | `build/device-1.0/continuation-20261021/r2-files-share-three-selected.xml`、`r2-{strict,standard}-native-share-three-result.png`、`r2-native-share-first-target.json`、`r2-standard-native-share-three-check.json` | 系统FilesActivity直接打开已核实的测试fixtures目录，选择F10-test.js/.kt/.py三项，经原生Share菜单分别进入Strict和Standard RC；两个完成报告均为“已导入 3 项”。选择器内只点击YHViewer RC目标，未选择正式包/Preview/其他分享服务，未改默认应用。报告截图仅保留对话框。此处为2026-10-21结果；单文件原生分享与“打开方式”已在2026-10-22补测，见下行。 |
| 2.6（单文件原生分享续测，2026-10-22） | 通过（两个RC，Markdown） | `build/device-1.0/continuation-20261022/r3-native-md-single-selected.xml`、`r3-{strict,standard}-native-single-share.png`、`r3-single-{first,second}-target.json` | 系统FilesActivity在已核实的测试fixtures目录只选F9-content.md，经Share原生面板分别进入Strict和Standard；直接显示F9 Markdown正文、表格、代码及转义HTML。结合上行三文件报告完成2.6的单/多文件路径。额外尝试单个F10-test.js时原生分享面板未提供RC；当前manifest的SEND不声明JavaScript类型，记录为类型限制，不据Markdown结果推断支持所有文件。未采集分享联系人或其他服务截图。 |
| 2.7 | 通过（意图路径） | `build/device-1.0/f9-strict-markdown-safety.png`、`f9-standard-markdown-safety.png` | 用 MediaStore `content://` URI 向两个 RC 发送 `ACTION_VIEW`，系统授予临时读取权并直接打开 Markdown。未通过文件管理器菜单手动选择。 |
| 2.7（原生打开方式续测，2026-10-22） | 通过（两个RC） | `build/device-1.0/continuation-20261022/r3-{strict,standard}-native-open-with.png`、`r3-open-with-{first,second}-target.json` | Files中长按F9-content.md → More options → Open with → YHViewer RC；用实际前台包名确认两个变体均直接进入阅读，正文正常。展开系统面板后点击RC，未调用正式包/Preview；该路径未显示“始终/仅此一次”确认，也未选择默认应用。 |
| 3.1 | 通过（Standard、Strict RC） | `build/device-1.0/standard-shelf-list-view.png`、`standard-shelf-reopen-after-force-stop.png`、`build/device-1.0/fix2-test/strict-shelf-view-before.xml`、`strict-shelf-view-list.xml`、`strict-shelf-view-reopened-list.xml`、`strict-shelf-view-cover-restored.xml` | Standard RC 的既有测试已确认封面/列表切换和 force-stop 后保留列表。Strict RC 将“封面”切为“列表”，强制停止并重开后仍选中“列表”；随后已恢复原“封面”偏好及 F10 搜索。 |
| 3.2 | 部分通过（Standard RC） | `build/device-1.0/standard-shelf-cover-view.png`、`standard-shelf-cover-fast-scroll.png`、`standard-shelf-cover-return.png` | 切换到封面模式后快速滑动到文档区域，书架响应并显示文档列表；未逐帧检查封面复用是否闪动，Strict RC 未测。 |
| 3.5（首轮书籍多选） | 部分通过（Standard RC） | `build/device-1.0/standard-longpress-selection.png`、`standard-multiselect-two-items.png` | 长按 F2 进入多选，追加选择 F6 后计数为 2；未覆盖全选、取消选中和删除确认。 |
| 3.5（首轮分组与收藏） | 部分通过（Standard RC） | `build/device-1.0/standard-multiselect-move-to-group.png`、`standard-create-group-entered.png`、`standard-group-filter-acceptance.png`、`standard-favorite-grouped-item.png`、`standard-favorite-filter.png` | 创建 `acceptance` 分组并把 F2 移入其中，分组筛选只显示 F2；收藏后收藏数为 1，收藏筛选只显示 F2。删除操作未执行。 |
| 3.5（续测） | 功能通过（Standard、Strict RC）；动效未验证 | `build/device-1.0/continuation-20261021/standard-F14-*.{png,xml}`、`strict-F14-*.{png,xml}`、`group-result-checks.json` | 两个RC均仅在精确F14筛选的两个合成文档上完成长按进入多选、单项详情、全选2项、取消其中1项后重选、两项收藏、新建rc-continue并移入、分组筛选2项、改名rc-renamed、删除分组后两项文档仍在、删除确认取消后保留2项、再次确认移除后F14结果为0。未用这些文档测试证明封面按压动效；多选栏滑入/淡入仍未逐帧验证。 |
| 3.9 | 未验证（封面按压动效） | — | 原报告本ID下的分组/收藏证据已归入3.5；未据此判定封面轻微下沉、松手回稳。 |
| 3.10 | 通过（Standard、Strict RC） | `build/device-1.0/standard-continue-reading-open.png`、`build/device-1.0/fix2-test/strict-verified-continue-card.xml`、`strict-verified-continue-open.xml` | Standard RC 卡片显示 F4、约 30 分钟前、读到第 2 章 / 66%，点击后打开 Chapter 2。经包名确认的 Strict RC 卡片显示 F4、安全与链接、读到第 2 章 / 66%；点击后阅读页标题为 Chapter 2、计数为 2 / 3。 |
| 3.3、3.7 | 部分通过（Standard、Strict RC） | `build/device-1.0/fix2-test/standard-search-big5.png`、`standard-search-missing.png`、`strict-search-big5.xml`、`strict-verified-search-missing.xml`、`strict-37-scrolled.xml`、`strict-37-after-search-closed.xml`、`strict-37-after-search-closed.png` | 通过 adb 输入确认搜索框内容确为小写 `big5`，两个 RC 均找到 F6 Big5；Standard 和经包名确认的 Strict RC 输入 `xyzmiss` 并收起键盘后均显示“没有匹配「xyzmiss」的内容”。Strict 书架滚到 F6 / 文档区域后搜索 F4，筛选结果回到书籍列表顶部并显示 F4 封面。 |
| 3.4 | 部分通过（Standard、Strict RC） | `build/device-1.0/standard-shelf-filter-code.png`、`build/device-1.0/fix2-test/strict-shelf-filter-code.xml`、`strict-shelf-filter-data.xml`、`strict-shelf-filter-restored.xml` | Standard RC 点“代码 4”后显示 4 份代码文档。Strict RC 的 F10 搜索结果中，“代码”筛选显示 2 项（JS、CPP），“数据”筛选显示 1 项（JSON），恢复“全部”后 3 项均显示。收藏、分组和其余类型筛选未覆盖。 |
| 3.6 | 通过（Standard RC） | `build/device-1.0/standard-shelf-docs-scroll.png`、`standard-open-from-shelf-doc.png`、`standard-shelf-return-position.png` | 书架滚到可见文档列表后打开 F10 JS，再点“书架”返回，原文档列表位置仍在屏幕内。Strict RC 未测。 |
| 3.8 | 部分通过（Standard、Strict RC） | `build/device-1.0/standard-settings-open.png`、`standard-settings-project-link.png`、`build/device-1.0/fix2-test/strict-settings-open.xml`、`strict-settings-hardware-back.xml`、`strict-settings-shelf-return.xml` | Standard 设置页版本及隐私说明已核验，项目主页进入 Chrome。Strict 设置页显示“版本 1.0.0 · Strict 版”及本地存储、沙箱与无网络权限说明；系统返回键和“‹ 书架”均回到 F10 搜索书架。Strict 两个外链点击均被手机的 AppLockActivity 拦截，未解锁或继续访问；因此 Strict 浏览器跳转及反馈链接是否打开浏览器仍未确认。 |
| 4.1 | 通过（用户确认的连续阅读边界） | `build/device-1.0/fix2-test/standard-F4-boundary-ch2-near-top.png`、`standard-F4-boundary-heading-below-toolbar.png`、`standard-F4-boundary-ch2-crossing.png` 及对应 XML | 复测显示：第二章标题完整位于顶部栏下方时仍显示 Chapter 1 / 1 / 3；继续滚动到标题进入顶部栏覆盖区域后，顶栏与计数同步切为 Chapter 2 / 2 / 3。用户说明以顶部栏遮挡位置作为章节切换判断，确认此行为符合预期。旧的“正文已显示第三章”截图不再作为失败证据。 |
| 4.2.1 | 部分通过（Standard RC） | `build/device-1.0/fix2-test/standard-page-turn-unique-single-tap.png`、`standard-page-turn-unique-before-triple.xml` | 唯一编号段落的单次侧边点按后正文约前进 0.9 屏，证明翻页动作已发生；本次截图采样未测得动作起始的精确延迟，故“抬手后立即响应”的时间要求仍待量化。 |
| 4.2.2 | 通过（Standard RC） | `build/device-1.0/fix2-test/standard-page-turn-unique-before-triple.png`、`standard-page-turn-unique-after-triple.png` 及对应 XML | 同一唯一编号 TXT 连点右侧三次后，正文标记从 0001–0005 推进到约 0015–0020，约三屏；章节计数仍为 `1 / 4`，表明按屏推进而不是误把章号当页码。 |
| 4.2.3 | 通过（Standard RC） | `build/device-1.0/fix2-test/standard-page-turn-unique-left-before.png`、`standard-page-turn-unique-left-after.png` | 唯一段落 TXT 点左侧后，视口从约 0237–0240 回退到约 0231–0235，约 0.9 屏；章节计数保持 `4 / 4`。 |
| 4.2.5、4.3.2、4.4.2–4.4.4、4.6.4–4.8.1 | 未完成 / 部分观察 | `build/device-1.0/fix2-test/standard-F2-tap-drag-interruption-retest.mp4`、`standard-F2-tap-drag-interruption-retest-after.png`、`standard-page-turn-tap-drag.mp4`、`standard-page-turn-tap-drag.png`、`standard-F2-singlepage-tap-drag.mp4`、`standard-F2-singlepage-tap-drag-after.png`、`standard-F2-continuous-tap-drag-after.png`、`standard-F2-continuous-tap-drag.mp4`、`standard-F3-toc-middle.png`、`standard-F3-toc-fast-scroll.png`、`standard-F3-AA-after-3s.png`、`standard-F3-ink-reading.png`、`standard-F5-vertical.png` | F3 目录快速拖动时气泡显示第 1090 章并与列表位置一致，筛选 `Chapter 12` 后拖至 Chapter 1245，列表编号和气泡“第 1245 章 / Chapter 1245”一致。Aa 面板等待 3 秒后位置稳定、未上移；墨色主题保留 Chapter 750。F5 原竖排 EPUB 显示转为横排阅读提示。连续模式下从 marker-0231 侧边点按后 50 ms 开始上滑，终态到 marker-0236，约前进一屏；录屏采样帧仍未呈现动画过程，无法区分翻页动画被拖动中断后的位移和动画先完成后的位移，因此即时中断及手指跟随滚动仍待确认。单章模式的末屏复测也未产生可辨认的翻页动画。测试后恢复连续滚动，并通过搜索回到 marker-0231 附近。 |
| 4.2.1、8.4（r4带触摸轨迹录屏） | 录屏可见状态观察通过；非内核输入时间测量 | `build/device-1.0/continuation-20261022/r4-{strict,standard}-page-single.mp4`、`-up-motion-frames.jpg`、`-frame-metrics.json`；`r4-page-turn-observations.json` | 动画1倍，系统Pointer location显示P:1→P:0作为可见抬手标记，逐个源帧核对正文。Strict首个P:0与首个正文移动同帧（2.154811s），Standard两者差15.733ms（2.155989→2.171722s）；从最后P:1帧到首个移动帧分别8.144/34.066ms，均在录屏可见状态的50ms范围内。相邻帧实际间距约8–18ms，未假定全片恒定120fps；系统标记的绘制时刻不等于内核MotionEvent时间，因此不作无误差的硬件输入延迟保证。单次位移由编号段落锚点量得约2060px，为2289px WebView高度的0.9屏。 |
| 4.2.2–4.2.3（r4续测） | 通过（两个RC） | `build/device-1.0/continuation-20261022/r4-{strict,standard}-page-triple-left.mp4`、`-page-triple-after.xml`、`-page-left-after.xml`、`-page-triple-input.json`、`r4-page-turn-observations.json` | 三次点击的主机命令总时长Strict299ms/Standard312ms（含全部输入等待），小于0.5秒。按唯一编号段落及等距锚点估算，单次约2060px，三连额外约6179px、随后左翻回退约2060px，取整/锚点推算误差约2px；对应3屏及1屏，没有漏翻/重复。章节保持1/4。 |
| 4.2.5（r4续测） | 录制交互通过（两个RC） | `build/device-1.0/continuation-20261022/r4-{strict,standard}-page-interrupt.mp4`、`-overview.jpg`、`-before.png`/`-after.png`；`r4-touch-settings-{before,restored}.json` | 临时将Animator设5倍延长翻页过程，侧边点按启动后约40ms再发起向下拖动；轨迹录像显示原先向上翻动的正文随向下手势反向移动，松手后停在拖动位置，没有继续跑到原翻页目标。最终估算位置约6952/7004px，低于未打断应到的约8240px。未以主机发送时间当作精确中断延迟。两RC结束均恢复Pointer location/Show touches和原动画值1/1/0.5。 |
| 4.3.1 | 部分观察（Standard RC） | `build/device-1.0/fix2-test/standard-page-turn-unique-ink-reader.png`、`standard-page-turn-unique-paper-reader-restored.png` | 连续阅读的纸张、墨色主题截图均包含正文及顶栏/底部胶囊；两主题下页面与控件配色一致，测试后恢复纸张主题。当前重叠区域不足以可靠判断背景文字是否达到预期模糊程度，未据此判失败或通过。 |
| 4.3.2 | 部分观察（Standard RC） | `build/device-1.0/fix2-test/standard-F4-single-paper.png`、`standard-F4-after-blur-aa.xml` | F4 EPUB 单章模式纸张主题下，正文、顶栏和底部胶囊可见，背景文本仍能辨认且控件对比度足够；仅有截图，未确认是否存在预期的无模糊、较不透明背景。测试后已恢复连续模式开启。 |
| 4.3.3 | 通过（Standard RC） | `build/device-1.0/fix2-test/standard-page-turn-unique-find-bar.png`、`standard-page-turn-unique-find-bar.xml` | 打开页内查找后，查找栏不透明，输入提示与上/下一个、全书控件清楚可见；本用例只检查栏的外观和可读性。 |
| 4.4.1 | 视觉细节，不阻塞发布（用户改判，见17） | `build/device-1.0/fix2-test/standard-page-turn-unique-ink-aa.png`、`standard-page-turn-unique-paper-reader-restored.png`、`standard-page-turn-unique-paper-restored.xml` | 连续模式下从纸张切换墨色后，Aa 面板、阅读顶栏和正文均呈墨色配色；回切纸张后阅读位置仍在第 4 / 4 部分、段落约 0231–0235。未用高速录屏确认三个区域是否同一帧变色；其余主题和单章模式未测。 |
| 4.4.2–4.4.3（续测） | 部分通过（Strict RC） | `build/device-1.0/continuation-20261021/strict-font-slider-during.png`、`strict-font-slider-after.xml`、`strict-font-persisted-after-reopen.xml`、`strict-line-slider-during.png`、`strict-line-slider-after.xml`、`strict-margin-slider-during.png`、`strict-margin-slider-after.xml`、`strict-aa-slider-restored.xml` | 连续模式 EPUB 拖字号时面板/遮罩/顶底栏隐去，只剩浮动滑杆；字号100→160%，退出书架后重开仍160%。行距1.9→2.0、页边距20→36的拖动状态与结束值均已记录；所有数值恢复100%/1.9/20。字体切无衬线后恢复跟随主题。EPUB正文实时排版变化尚未按帧量化；TXT见下一行续测。 |
| 4.4.2–4.4.3（TXT续测） | 实时预览/保存/重开通过（两个RC）；模糊强度未量化 | `build/device-1.0/continuation-20261021/r2-{strict,standard}-txt-{font,line,margin}-during-{1,2}.png`、对应`-samples.json`/`-released.xml`、`r2-{strict,standard}-txt-font-persisted.xml`、`r2-{strict,standard}-txt-aa-restored.xml`、`r2-{strict,standard}-txt-sliders-contact.jpg` | F6无标题TXT的三个滑杆各拖6秒，在约1.7秒/3.4秒截图，记录采样时adb触摸进程仍在运行。松手前仅留浮动滑杆，正文随字号/行距/边距即时重新排版；字号中途120/140%，最终160%，回书架从已有副本重开仍160%。行距最终2.3，边距36；之后两个RC均恢复100%/1.9/20。未据静态采样量化毛玻璃强度或逐帧延迟。 |
| 4.4.5（续测） | 通过（两个RC） | `build/device-1.0/continuation-20261021/r2-{strict,standard}-F3-aa-{0.5s,3.5s}.png`、`r2-{strict,standard}-F3-aa-bottom.xml`、`r2-F3-aa-results.json` | F3的Aa打开后约0.5秒与3.5秒，去除系统状态/导航栏的内容区域逐像素一致，无向上位移；向面板底部滚动可见“章节连续滚动”“点击屏幕左右两侧翻页”“阅读时屏幕常亮”三个开关，未切换它们。 |
| 4.4.1、4.4.4（续测） | 部分通过（Strict RC） | `build/device-1.0/continuation-20261021/strict-continuous-theme-*.png`、`strict-single-theme-*.png`、`strict-single-font-during.png`、`strict-single-font-after.xml`、`strict-final-reader-preferences.xml` | 连续/单章分别选择四个主题，稳定画面配色一致；未据截图确认同帧变色或无重载。单章拖字号结束值160%，已恢复100%；生效时机未逐帧核验。最终恢复初始跟随系统、跟随主题字体、连续/侧边翻页开启、常亮关闭。 |
| 4.4.7 | 通过（Standard RC） | `build/device-1.0/fix2-test/standard-F4-stay-awake-enabled.xml`、`standard-F4-stay-awake-restored.xml` | 打开“阅读时屏幕常亮”后，将系统屏幕超时临时调为 30 秒，等待 2 分钟设备仍保持 Awake。随后关闭 RC 开关，并把系统超时恢复到原值 300 秒。 |
| 4.5.5 | 通过（Standard RC） | `build/device-1.0/fix2-test/standard-unique-find-copper.png`、`standard-unique-find-next-35.xml`、`standard-unique-find-previous-34.xml`、`standard-unique-find-closed-controls.png` | 连续模式 TXT 页内查找 `copper` 共显示 36 个命中并高亮当前项；命中计数从 33/36 前进到 34/36、35/36，点上一项回到 34/36。查找关闭后章节仍为第 4 / 4 部分，未观察到额外补章。 |
| 4.5.6 | 通过（Standard RC） | `build/device-1.0/fix2-test/standard-unique-fullbook-results-reopened.png`、`standard-unique-fullbook-scroll-near3.xml`、`standard-unique-fullbook-third-chapter-third-result.png`、`standard-unique-fullbook-target-closed.xml` | 全书搜索 `copper` 共显示 240 条结果。点第三部分下的第三条（marker-0139；该部分的首两条为 marker-0137、0138）后，阅读器落在 marker-0139 并显示第 3 / 4 部分，符合目标章节与该章第三个匹配位置。 |
| 4.5.1 | 通过（Standard RC） | `build/device-1.0/fix2-test/standard-F3-open-for-toc.png`、`standard-F3-toc-open-current.png` | F3 当前位于 Chapter 1131 时打开目录，列表自动滚至 Chapter 1128–1140 附近，Chapter 1131 显示“在读”。 |
| 4.5.2 | 通过（Standard RC） | `build/device-1.0/fix2-test/standard-F3-filtered-fast-scroll-bubble.png`、`standard-F3-filtered-fast-scroll-bubble.xml` | 目录筛选 `Chapter 12` 后拖动快速滚动条，列表项编号与标题为 Chapter 1245；气泡显示“第 1245 章 / Chapter 1245”，保留原始章节号且与实际条目一致。 |
| 4.5.3（续测） | 已知问题，不阻塞1.0（用户改判，见17） | `build/device-1.0/continuation-20261021/strict-continuous-toc-for-footnote.xml`、`strict-continuous-ch3-footnote-start.png`、`strict-continuous-toc-retry-ch3.xml`、`strict-continuous-toc-ch3-retry-result.png`、`strict-continuous-toc-ch3.log`、`standard-continuous-toc-before-ch3.xml`、`standard-continuous-toc-ch3-result.png`、`standard-continuous-toc-ch3.log` | F4 连续模式打开目录，点 Chapter 3（第三行），Strict 两次、Standard 一次均落在 Chapter 2 大图处；顶栏 Chapter 2 / 2 / 3，第三章正文未在视口。日志先报 chapter=2（0-based），约130ms后回报 chapter=1 ratio=0.405。单章模式导航正常。此项是主动跳转位置错误，与用户确认的自然滚动章节边界不同；尚未修复。 |
| 4.5.3 | 通过（Standard RC） | `build/device-1.0/fix2-test/standard-F3-toc-filter-750-current.png`、`standard-F3-toc-750-target.png` | 目录筛选输入 `750` 后只显示 Chapter 750；点选后阅读页显示 `750 / 1500` 和 Chapter 750。 |
| 4.2.4 | 部分通过（Standard RC） | `build/device-1.0/fix2-test/standard-F2-center-tap-hide.png`、`standard-F2-center-tap-show.png` | 屏幕中央点按后顶部工具栏及底部控件可隐藏，再点可显示；动画淡入/淡出时长与过程未录屏验证。 |
| 4.2.6 | 部分通过（Standard RC） | `build/device-1.0/fix2-test/standard-F2-page-right-tap-3.xml`、`standard-F2-page-left-tap.xml`、`standard-F2-aa-restored.xml` | F2 单章模式在第 22 章边界右侧点按后到第 23 章，再在章首左侧点按回第 22 章；每次章节计数只变一章。最后一章末尾点按无反应未测。测试后已恢复连续滚动与左右侧翻页开关。 |
| 4.2.7 | 通过（Standard RC） | `build/device-1.0/fix2-test/standard-F2-side-flip-disabled-after-right.png`、`standard-F2-side-flip-disabled-after-left.png`、`standard-F2-aa-restored.xml` | 关闭左右侧翻页后，右侧和左侧点按只隐藏/显示工具栏；章节仍为 22，之后已把设置恢复开启。 |
| 4.4.6 | 通过（Standard RC） | `build/device-1.0/fix2-test/standard-F3-bookmark-aa.xml` | F3 Aa 面板显示“添加当前位置书签”，没有“进度”项；点击添加后书签数增加。 |
| 4.5.4 | 通过（Standard RC） | `build/device-1.0/fix2-test/standard-F3-bookmark-added-751.png`、`standard-F3-bookmark-duplicate-751.png`、`standard-F3-bookmark-added-752.png`、`standard-F3-bookmark-panel-3.png`、`standard-F3-bookmarks-three-list.png`、`standard-F3-bookmark-jump-751-of-three.png`、`standard-F3-bookmarks-after-delete-751.png`、`standard-F3-bookmarks-clean.png` | F3 上添加 Chapter 751、752、753 三个书签，列表显示 3 项；在 Chapter 751 同一位置重复添加时提示“这里已有书签”，计数未重复增长。点击 Chapter 751 书签后阅读页回到 Chapter 751；逐项删除后计数从 3 降至 2、1、0，测试书签已清理。此前 Chapter 750 的单书签添加、跳转和删除记录仍作为补充证据。 |
| 4.7.2 | 通过（两个 RC，生成素材） | `build/device-1.0/continuation-20261021/strict-no-headings-tail-match.png`、`standard-no-headings-tail-match.png`、`strict-long-lines-fullbook-end-results.xml`、`standard-long-lines-fullbook-end-results.xml`、`strict-long-lines-end-search-target.png`、`standard-long-lines-end-search-target.png`、`tail-search-contact-sheet.jpg` | 小无标题TXT按单段“正文”打开，搜索END_MARKER_NO_HEADINGS均显示1/1并可见高亮。生成的12,000行长TXT分为28部分，全书搜索11999均返回唯一结果，点击后定位第28/28部分且末行11999可见高亮。未把小文件单段标题强记为“第n部分”。 |
| 4.7.1 | 部分通过；四类编码设备复测通过 | `build/device-1.0/fix2-test/standard-GBK.png`、`standard-big5-open.png`、`standard-UTF16LE.png`、`standard-UTF16LE-BOM.png`、`strict-GBK.png`、`strict-Big5.png`、`strict-UTF16LE.png`、`strict-UTF16LE-BOM.png` | 两个新 RC 的 GBK、Big5、无 BOM UTF-16LE、带 BOM UTF-16LE 均显示正常；F1 其余文本格式尚未逐项确认。 |
| 4.8.2 | 部分观察（Standard RC） | `build/device-1.0/fix2-test/standard-F3-paper-switch-200ms.png`、`standard-F3-paper-switch-1200ms.png`、`standard-F3-ink-switch-200ms.png` | F3 在 Aa 面板中切换纸张/墨色，200 ms 后截图分别呈对应主题色，未观察到白底闪帧；没有录屏逐帧分析，也未覆盖目录跳转、书签跳转或打开 Markdown/代码。 |
| 4.8.3 | 通过（Standard RC） | `build/device-1.0/fix2-test/standard-F7-ink-raw-html.png` | 墨色阅读主题下通过 `VIEW` 打开 F7 原样 HTML 沙箱页，应用顶栏为墨色，网页正文保持白底，符合原样 HTML 保持自身背景的预期。测试后已恢复 RC 纸张主题。 |
| 4.6.1 | 部分通过（Standard、Strict RC） | `build/device-1.0/f4-ch2-big-image-after-wait.png`、`build/device-1.0/fix2-test/standard-F4-link-chapter2.png`、`standard-F4-link-single.png` | F4 第 2 章大图可显示；章节内链接在连续模式及单章模式均跳到 Chapter 2，标题、章节计数与目标章一致。Strict RC 的链接路径未复测。 |
| 4.6.2（续测） | 失败（Standard、Strict RC 连续模式）；Strict 单章路径通过 | `build/device-1.0/continuation-20261021/strict-single-ch3-before-return-footnote.png`、`strict-single-return-footnote-result.png`、`strict-continuous-ch3-scrolled-footnote.xml`、`strict-continuous-return-footnote-result.png`、`strict-continuous-footnote-bars-hidden.xml`、`strict-continuous-footnote.log`、`standard-continuous-footnote-result.png`、`standard-continuous-footnote-bars-hidden.xml` | 单章模式从第三章点“返回脚注”，回第一章且“脚注内容”可见。连续模式同链接路由为 ch0001.xhtml#note，但正文目标落在顶栏遮挡区；隐藏顶底栏后，UI树显示“脚注内容”位于 y=111–204，位于此前顶栏 y=144–254 的覆盖范围。故连续模式锚点在顶栏显示时不可见，判为定位失败；Standard RC 同路径也复现：目标脚注 y=111–204，被显示中的顶栏覆盖，隐藏顶栏后可见。 |
| 4.6.2（首轮） | 首轮部分观察；续测已确认失败 | `build/device-1.0/fix2-test/standard-F4-link-chapter2-YHFlow.log`、`standard-F4-footnote-jump.png`、`standard-F4-footnote-YHFlow.log`、`standard-F4-footnote-single.png`、`standard-F4-footnote-anchor-continuous.png`、`standard-F4-footnote-anchor-continuous.log` | 连续模式点“去第二章图片”后到达 Chapter 2，标题与计数一致；单章模式同样到达 Chapter 2。再次在连续模式点脚注标记，日志确认路由到 `ch0001.xhtml#note`，但点击后截图停在 Chapter 2 大图附近，未清楚显示“脚注内容”；单章模式下目标原本就在当前视口，点击前后画面相同。这是首轮观察；本轮通过跨章返回脚注及隐藏顶栏对照，已确认两个 RC 目标被遮挡，以上述续测失败为准。 |
| 4.6.3 | 通过（安全检查） | `build/device-1.0/f4-strict-ch1.png`、`f4-strict-after-top-link.png`、`f4-onerror-broken-strict.png`、对应 `f4-standard-*` | 两个 RC 中，章节脚本未替换正文；刷新元标签未把页面跳走；破图版本的 `onerror` 未替换正文；点按 `target=_top` 后仍在阅读器，正文/外层未被替换。为明确触发 `onerror`，另生成了一个仅把图片路径改成不存在文件的补充 EPUB，和原 F4 分开。 |
| 4.8.1–4.8.2（r4逐帧续测） | 通过（两个RC，本轮覆盖操作） | `build/device-1.0/continuation-20261022/r4-{strict,standard}-dark-navigation.mp4`、`-overview.jpg`、`-white-check.json`、`-frame-brightest.png`；`r4_dark.py` | 墨色单章F3从Chapter1连续5次下一章到Chapter6，目录跳Chapter10，临时书签回Chapter1，再打开Markdown和代码。每个源视频解码帧都检查正文宽带白色背景，Strict750帧、Standard436帧均无宽幅白色闪帧；最亮水平带的亮像素占比分别约5.70%/5.64%，峰值帧为正常墨色Markdown白字。结合关键帧人工复核，没有把预期白字误算白闪；结论限录制帧和所列路径。新F3副本的临时书签已删除，连续模式恢复开，主题分别恢复Strict跟随系统/Standard纸张。 |
| 4.8.3（r4续测） | 通过（两个RC，F7文件夹隔离HTML） | `build/device-1.0/continuation-20261022/r4-{strict,standard}-F7-rawhtml-ink.png`、`r4-{strict,standard}-rawhtml-theme-restored.xml` | 墨色主题下打开F7的hello.html：原生顶栏和导航栏为墨色，网页本身保持白底、显示F7 folder isolation probe，符合原样HTML不套主题的预期。此白底路径独立于上行白闪检查；随后恢复原主题。 |
| 5.1 | 通过（默认权限） | `build/device-1.0/f7-sandbox-strict.png`、`f7-sandbox-strict-reopen.png`、`f7-folder-isolation-strict.png`、`f7-folder-isolation-strict-reopen.png`，对应初始 `f7-*standard*.png` | Strict/Standard 两个 RC 的普通沙箱页和文件夹隔离页，在默认档位首次打开、返回书架后重开均显示“全部 9 项通过”。Standard F7 后续为 6.3 测试而开启联网，WebSocket 测试随之能连接，属于该权限测试结果。 |
| 5.2 | 通过（Standard、Strict RC） | `build/device-1.0/f8-alert-standard.png`、`f8-confirm-standard.png`、`f8-prompt-standard.png`、`f8-prompt-entered-standard.png`、`f8-prompt-dismissed-standard.png`、`f8-alert-repeat-3.png`、`f8-alert-suppressed-standard.png`、`build/device-1.0/fix2-test/f8-alert-strict.png`、`f8-confirm-strict.png`、`f8-prompt-strict.png`、`f8-prompt-confirmed-strict.png` | Standard RC 中 alert、confirm、prompt 均以标题“「F8-dialogs.html」中的网页显示”的应用内对话框出现；确认/取消可关闭，prompt 可输入文字后确认。第三次 alert 可勾选“不再显示这个页面的弹窗”；确认后再次触发没有弹窗。Strict RC 中 alert、confirm（取消）、prompt（确认）也显示同一类应用内对话框，标题同样包含文档名。 |
| 5.5 | 部分通过（Standard、Strict RC） | `build/device-1.0/f13-standard-open.png`、`f13-link-system-confirm.png`、`f13-browser-resumed.txt`、`f13-browser-open.png`、`build/device-1.0/fix2-test/strict-f13-open.png`、`strict-f13-link-result.png` | Standard 的真实链接点击出现系统“允许 YHViewer RC 打开 Chrome？”确认框；选择“Allow once”（未选 Always allow）后进入 Chrome。Strict 的真实链接直接打开 Chrome；测试网络返回 `ERR_CONNECTION_REFUSED`。Strict 返回 RC 正常。F13 样例没有脚本跳转按钮，因此脚本发起跳转被拦截项未测。 |
| 5.6 | 通过（Standard、Strict RC） | `build/device-1.0/f9-standard-markdown-safety.png`、`build/device-1.0/fix2-test/strict-f9-markdown.png` | 两个 RC 的 Markdown 表格、代码高亮、脚注和原始 HTML 文本均正确显示；Strict 的注入脚本样例也以文本呈现。 |
| 5.7 | 通过（Standard RC，主题全局共用） | `build/device-1.0/f9-standard-aa.png`、`f9-standard-aa-theme.png`、`f9-standard-aa-font.png`、`f2-standard-aa-after-markdown.png` | 首轮确认字号调至 160% 后 TXT 仍为 100%，字体设置独立保存。主题在书架和阅读器共用同一个 `ThemeStore`，Markdown 切换主题后 TXT 同步变色符合设计；FIX1 说明澄清验收要求只期望字号分开保存，因此撤回该失败判定。 |
| 5.9 | 行号/高亮及复制不带行号通过（两个RC，覆盖整页全选路径） | 首轮 `build/device-1.0/fix2-test/{strict-f10-cpp-select,f10-cpp-select-all}.png`；续测 `build/device-1.0/continuation-20261021/r2-{strict,standard}-code-copy-native-pasted.txt`、`r2-{strict,standard}-code-copy-check.json`、`r2-{strict,standard}-code-copy-native-pasted-editor.png` | 两个RC均显示行号/语法高亮。续测在Kotlin阅读页用系统Select all/Copy，再在临时编辑会话中用原生Paste核对字符串：包含Kotlin/行数说明及代码，没有左侧1/2/3行号。Strict三行样例在整页全选路径中省略中间空行，已保留实际文本；不把此路径当作代码专属选区保留空行的证明。此前Ctrl+C/V及KEYCODE_PASTE自动化比对未获有效结论，不判应用失败。粘贴会话均未保存。 |
| 5.9（r4代码范围复制补充） | 失败：中间空行丢失（两个RC）；复制不带行号通过 | `build/device-1.0/continuation-20261022/r4-{strict,standard}-code-range-{selected,pasted}.png`、`-copied-pasted.txt`、`-check.json`；`r4-code-asset-check.json` | 新测试R4-copy-lines.kt包含第一行val alpha、第二行空、第三行val beta。长按alpha后拖右选区手柄到第三行END末尾，选区完全在代码区内且包含空行；原生Copy/Paste实际为`alpha = "BEGIN"`后单个换行接`val beta = "END`，缺少预期两个换行。两RC同样复现，没有格式头或1/2/3行号。Strict已安装APK中代码模板哈希与工作树相同且含copy listener，排除安装包缺失该资产的情况；未推断底层原因，未修改实现。Scratch粘贴全部不保存。 |
| 5.10 | 通过（Standard、Strict RC） | `build/device-1.0/f10-js-aa-standard.png`、`f10-js-aa-wrap-on.png`、`f10-js-aa-wrap-reopen-standard.png`、`build/device-1.0/fix2-test/strict-f10-aa.png`、`strict-f10-aa-reopen.png`、`strict-f10-aa-reopened-doc.png`、`strict-f10-aa-font-before.png`、`strict-f10-font-changed.png`、`strict-f10-aa-font-restored.png`、`strict-f10-aa-restored.png` | 两个 RC 的代码 Aa 均有字号和自动换行控件；自动换行开启后重开仍保持开启。Strict 字号调大后代码字形随之变大，已恢复 100%；自动换行也已恢复初始关闭状态。 |
| 5.14 | 保存及另存为功能通过（两个 RC 的覆盖路径见备注） | 首轮 `build/device-1.0/f9-editor-marker-saved-search.png`；续测 `build/device-1.0/continuation-20261021/r2-{strict,standard}-save-as-{editor-result,shelf-entry,reader}.png`、`r2-save-as-file-check.json`、`r2-strict-save-as-file-check.json`；代码保存另见5.15 | 首轮Standard的F9编辑/保存后页内搜索1/1；5.15中代码保存后的运行与恢复已覆盖JS/Python。续测Standard在F9插入R2_SAVE_AS_MARKER，Strict在Kotlin插入//R2_STRICT_SAVE_AS，经系统另存为分别在已核实测试fixtures目录生成新.md/.kt文件；两个RC均提示已另存并加入书架，新条目可打开且显示标记。两份原源文件SHA-256均与本地原素材一致；退出原编辑会话时丢弃未保存编辑，没有覆盖原源文件。 |
| 5.16 | 部分通过（Standard、Strict RC） | `build/device-1.0/fix2-test/standard-F9-bookmark-added.png`、`standard-F9-bookmark-jumped.png`、`strict-f9-bookmark-added.png`、`strict-f9-bookmarks-after-delete.png` | 两个 RC 均可通过“⋯ → 书签”添加并删除书签。Standard 已验证从不同阅读位置跳回书签；Strict 在 F9 单页文档当前 0% 位置添加，点击书签条目返回文档，再删除后计数恢复 0。由于单页素材未产生不同滚动位置，Strict 的跨位置跳转仍无法单独确认。 |
| 5.16（r4补充） | 通过（Strict跨位置跳转，结合首轮Standard） | `build/device-1.0/continuation-20261022/r4-strict-doc-bookmark-{position,added,far,jumped,deleted}.png`、`r4-strict-doc-bookmark-result.json` | Strict在F10-1500-lines.cpp的3%位置添加唯一书签，连续三次上滑离开，再经更多→书签点击该项。跳回后正文区域与添加前逐像素一致（MAD=0），远处对照MAD=9.028/255；随后仅删除本轮新书签，计数回0。补足Strict跨位置验证。 |
| 5.17 | 通过（Standard、Strict RC） | `build/device-1.0/f9-search-const.png`、`f9-editor-marker-saved-search.png`、`build/device-1.0/fix2-test/strict-f9-search-const.png` | 两个 RC 的 F9 文档页内查找 `const` 均显示 1/1 并高亮命中；Standard 保存后的 `ACCEPTANCE_EDIT` 查找也显示 1/1。 |
| 5.11 | 通过（两个 RC） | `build/device-1.0/f8-html-aa-standard.png`、`build/device-1.0/fix2-test/strict-f8-html-aa.png` | 两个 RC 的 HTML Aa 均显示说明“网页自带排版，这里只调整文字大小”及字号滑杆，没有主题选项。本轮以F15/H02 HTML把字号从100%调到200%，正文和标题明显放大、换行增加；退出重开后Aa仍显示200%。随后两个RC均恢复100%。补充证据：`build/device-1.0/continuation-20261021/r2-strict-html-body-100.png`、`r2-strict-html-body-200.png`、`r2-strict-html-aa-reopen-200.xml`、`r2-strict-html-aa-restored.xml`、对应`r2-standard-html-*`。 |
| 5.12 | 通过（Standard、Strict RC） | `build/device-1.0/f11-svg-standard.png`、`f11-svg-menu-standard.png`、`f11-svg-source-standard.png`、`f11-svg-image-aa-standard.png`、`build/device-1.0/fix2-test/strict-f11-svg.png`、`strict-f11-menu.png`、`strict-f11-source.png`、`strict-f11-aa.png` | 两个 RC 均将 F11 SVG 作为图片显示，包含脚本的样本未弹出脚本对话框；“⋯”可切换源码；图片模式 Aa 只有主题选项。 |
| 5.15 | 部分通过（Standard、Strict RC） | `build/device-1.0/f10-js-run-standard.png`、`f10-python-run-standard.png`、`f10-python-timeout-standard.png`、`f10-python-strict.png`、`build/device-1.0/fix2-test/f10-js-run-sheet.png`、`f10-js-timeout-strict.png`、`f10-js-timeout-strict.xml`、`f10-js-restored-after-save.png`、`standard-python-open.xml`、`standard-python-unicode-output.xml`、`standard-python-restored-after-save.xml`、`standard-python-manual-stop-result.xml` | Standard JavaScript 正常运行并输出 marker（424 ms）；Python 正常运行并输出 marker（420 ms）；`while True` 在 5003 ms 后报告超时并强制停止。Strict RC 正常 JavaScript 输出 marker（443 ms）；将编辑器缓冲区临时改为 `while(true){}` 后，5 秒超时于 5007 ms 显示“运行超过 5 秒，已强制终止”。随后已恢复并保存原始 `console.log("JavaScript marker");`，截图确认。之后经测试素材 `F10-test.py` 的 MediaStore URI 在 Standard RC 打开；临时运行 `print(chr(20013)+chr(25991))` 输出“中文”（424 ms），再恢复并保存为原始 `print("Python marker")`，应用提示保存到书架副本且原文件未改。Standard RC 的 `F10-infinite.py` 再运行后点击“停止”，显示“已停止”，手动停止通过。Strict 的 Python 页面没有“运行”按钮；其余未在两 RC 同时覆盖的脚本行为仍未完成。 |
| 5.13 | 通过（Standard、Strict RC） | `build/device-1.0/f10-json-standard.png`、`f10-cpp-standard.png`、`f10-cpp-standard-scrolled.png`、`build/device-1.0/fix2-test/strict-f10-json.png`、`strict-f10-cpp.png`、`strict-f10-cpp-scrolled.png` | 两个 RC 均能打开 500 KB 单行 JSON，并显示“1 行，文件较大，已关闭高亮”；1500 行 C++ 可打开并滚动到后续行。手动操作中未观察到卡死；未把卡顿量化，性能数据见第 8 节。 |
| 5.3 | 通过（Standard RC） | `build/device-1.0/f7-standard-opened.png`、`f7-standard-permissions.png`、`f7-standard-network-after.png`、`f7-folder-isolation-standard-new.png` | F7 默认网络探测 9/9；仅对该文档开启“主动联网”后，WebSocket 显示“连接上了”。另一个独立的文件夹隔离文档仍显示 9/9，网络探测未连通，权限未串到另一文档。 |
| 5.4 | 通过（Strict RC） | `build/device-1.0/f7-strict-permissions.png` | Strict RC 权限页将外网图片、iframe、主动联网等项目标为“需要 Standard 版（Strict 版不联网）”，不可开启。 |
| 5.8 | 通过 | `build/device-1.0/f9-strict-markdown-safety.png`、`f9-standard-markdown-safety.png` | 两个 RC 的 Markdown 页都把 `<script>…</script>` 注入字符串原样显示为文字，正文未被替换。 |
| 6.1 | 通过 | 上述 F4、F7、F9 截图 | EPUB 脚本/导航、沙箱 9/9、Markdown 注入检查均无失败。 |
| 6.2 | 通过 | RC 包 `dumpsys package` 权限检查 | Strict RC 未声明 INTERNET 权限；Standard RC 声明该权限。 |
| 6.3 | 通过（Standard、Strict RC） | `build/device-1.0/f7-standard-network-after.png`、`f7-folder-isolation-standard-new.png`、`f7-strict-permissions.png` | Standard 对 F7 单独开启网络后 WebSocket 已连接，独立文档仍保持默认阻断；Strict 权限项不可开启。Standard 的其他外网请求仍显示失败，6.3 以 WebSocket 的成功连接确认网络权限生效。 |
| 7.1–7.5（首轮） | 部分通过 / 未完成 | `build/device-1.0/fix2-test/motion-slow-5x-followup.mp4`、`motion-web-fade-verified.mp4`、`motion-open-f13-search.mp4`、`motion-settings.xml`、`motion-shelf.xml`、`strict-F4-longpress.png`、`strict-F4-longpress.xml` | 5× Strict RC 重测中，长录屏没有显示实际导航；短录屏从书架搜索结果点开 F13 后仍只记录到书架画面，录制结束后设备已进入 F13。无法据视频判断网页是否跟随页面淡入，7.1 继续保持未确认。F4 正文长按可选中文字并显示系统 Copy / Select all / Web search / Share 工具栏，选字交互通过。章节链接和脚注跳转的复测结果见 4.6.1–4.6.2；链接按压反馈仍未测。其余 7.2–7.5 未完成；7.6 续测见下。动画倍率已恢复为窗口 1、过渡 1、Animator 0.5。 |
| 7.1（续测） | 已记录两个RC的实际淡入；正文较晚首次绘出后跟随剩余淡入 | `build/device-1.0/continuation-20261021/r2-motion5-{strict,standard}.mp4`、对应`-overview.jpg`/`-body-first-frames.jpg`/`-pixel-samples.json`；`r2-motion5-settings-{before,restored}.json` | 显式录制物理显示屏，触摸命令在录制期间执行，两个RC均捕获书架→Markdown→书架→设置→书架。窗口/过渡/Animator临时均5，结束恢复1/1/0.5。原生文字在固定位置变深，未见页面位移。正文首次绘出晚于原生Aa约0.21秒；5倍片中，首次正文文字对比度约为稳定态的76%/79%，同期Aa约74%/78%，此后同步继续变深。因此正文会跟随剩余淡入，但没有从原生淡入起点同步显示。数值为编码帧RGB投影诊断，非内部alpha值；未用主机命令时间或视频平均fps推断首帧耗时/50ms响应。普通倍率片`r2-motion-physical-display.mp4`也保留。 |
| 7.2（r4续测） | 通过（两个RC，已导入小Markdown重开） | `build/device-1.0/continuation-20261022/r4-{strict,standard}-small-F9-open.mp4`、`-transition-all-frames.jpg`、`-transition-pts.json` | 从精确F9筛选书架点击测试.md，逐帧检查实际转换段，两RC均未出现转圈，完整原生顶栏淡入后正文出现。此路径是小文件重开，不冒充首次导入计时。 |
| 7.3（r4续测） | 加载提示行为通过（两个RC，新来源的F3相同副本） | `build/device-1.0/continuation-20261022/r4-{strict,standard}-F3-first-import.mp4`、对应`-overview.jpg`/`-result.png`；`fixture-uris.json` | 在指定测试fixtures目录新建与F3字节相同的R4-first-F3-1500.epub，SHA-256均为4ff96dd06a3d449fe564e0ee6b2d2a09851f6a8d9933061f2db8b24dbd7efa70，新MediaStore URI为1000182975；首次向每个RC导入。录像显示加载期间只有返回书架与转圈，加载结束后才出现完整顶栏与Chapter1正文，没有完整顶栏先出现再消失。原有条目和用户数据未清除；不把这一副本等同原F3历史首次打开耗时。 |
| 7.4（r4链接按压续测） | 通过（两个RC，EPUB及Markdown） | `build/device-1.0/continuation-20261022/r4-{strict,standard}-{EPUB,MD}-link-press.mp4`、`-before-active.png`、`-pixel-samples.json`、`-target.json`；`r4-links5-settings-{before,restored}.json` | 三项动画缩放临时5倍；对F9脚注链接及字节同源的新F4副本章节链接按住220ms再松开。帧对照可见链接文字变淡、周围背景不变，没有灰蓝色高亮块。保持字形几何相同的编码帧RGB投影约为按前的0.59–0.61，背景像素平均绝对差约1.4–1.5/255；这是图像诊断而非内部alpha。链接随后正常跳转，动画恢复1/1/0.5。 |
| 7.5（r4原生复制续测） | 通过（两个RC，五类阅读器的覆盖路径见备注） | `build/device-1.0/continuation-20261022/r4-{strict,standard}-{MD,TXT,HTML,EPUB}-selected.png`、对应`-copied-pasted.txt`/`-pasted.png`；代码见5.9的r2原生复制证据 | 各阅读页长按选字 → 系统原生Copy → RC临时Scratch编辑器原生Paste，实际得到MD的Markdown、TXT的“文”、HTML H03的English、EPUB的“点”（Strict）/END_OF_F4（Standard）。选中前后正文/章计数未发生误翻页；预设字符与实际选字不同的自动化断言已按截图纠正，非复制失败。所有Scratch编辑不保存，未改源文件。代码整页原生复制已在r2两RC验证，代码专属范围空行另行测试。 |
| 7.6 | 通过（两个 RC 的阅读、Aa、书架、设置导航） | `build/device-1.0/continuation-20261021/strict-animation-zero-*.png`、`standard-animation-zero-*.png`、`animation-zero-contact-sheet.jpg` | 窗口、过渡、Animator 三项都临时设为0，两个RC依次打开Markdown阅读页、Aa、书架、设置并返回，页面均完整可操作，没有停在半透明状态。随后恢复原值1 / 1 / 0.5。 |
| 8.1 | 通过（正式包、Standard RC、Strict RC） | `build/device-1.0/main-startup.txt`、`build/device-1.0/fix2-test/rc-cold-start.txt` | 正式 Standard 中位数 399 ms；Standard RC 五次 TotalTime 为 533、353、336、343、297 ms，中位数 343 ms；Strict RC 为 316、288、296、318、315 ms，中位数 315 ms。 |
| 8.2–8.7 | 部分完成 | `build/device-1.0/fix2-test/rc-cold-start.txt`、`standard-F2-scroll-gfxinfo.txt`、`standard-F2-after-2min-meminfo.txt`、`standard-F3-scroll-gfxinfo.txt`、`standard-F3-after-2min-meminfo.txt`、`standard-page-turn-unique-30turn-gfxinfo.txt`、`standard-f3-reopen-0.2s.png`、`standard-f3-reopen-0.8s.png`、`standard-f3-reopen-2.0s.png` | 8.1、8.5 的 F2/F3 连续滚动采样、8.6 两本测试书的 PSS 和 30 次侧边点按后的帧统计已记录。F2 采样 3,731 帧，现代 Janky frames 0.96%、legacy 75.72%；F3 采样 7,455 帧，现代 1.06%、legacy 61.03%。30 次点按样本 228 帧，现代 Janky frames 7.89%、legacy 61.84%，50/90/95/99 分位 11/14/17/23 ms；过程中到达第 4/4 部分。legacy 统计与现代口径差异明显，保留原始输出。F2/F3 PSS 分别约 167.1 / 174.8 MB。Standard RC 打开 F3 后退出书架再打开，点按后约 0.2 秒的采样帧已显示 Chapter 750 正文及下文，0.8 秒和 2 秒帧继续显示正文；这只能说明此热重开至少在首个采样点已出正文，不能当作精确耗时或首次打开计时。8.2 首次/再次打开耗时、8.3 首次打开计时、8.4 精确翻页响应延迟和 8.7 空闲定时器仍未量化。 |
| 8.5–8.6（续测） | 已记录（Strict RC） | `build/device-1.0/continuation-20261021/strict-F2-60s-gfxinfo.txt`、`strict-F3-60s-gfxinfo.txt`、`strict-F2-2min-meminfo.txt`、`strict-F3-2min-meminfo.txt`、`strict-F2-perf-after.png`、`strict-F3-perf-after.png` | F2/F3各连续滑动两分钟，前60秒帧数据分别为7,059帧/0.27%现代Jank、7,082帧/0.82%现代Jank；两分钟后PSS分别266,611/155,525KB。各158次合成上滑，最终页面仍显示正文。没有逐帧视频，未据最终截图排除全程短暂空白。 |
| 9.1 | 通过（Standard、Strict RC） | `build/device-1.0/continuation-20261021/strict-follow-system-dark-reader.png`、`strict-follow-system-dark-shelf.png`、`strict-follow-system-light-shelf.png`、`strict-follow-system-light-aa.xml`、`standard-follow-system-dark-reader.png`、`standard-follow-system-dark-shelf.png`、`standard-follow-system-light-shelf.png`、`standard-theme-paper-restored.xml` | 两个 RC 均明确在 Aa 选“跟随系统”后开启系统深色模式，F4 EPUB 正文/顶栏/胶囊与书架均呈深色；恢复系统浅色后书架回浅色。初始 Strict 为跟随系统、Standard 为纸张，已各自恢复。首轮 Standard 的纸张主题不随系统切换是选定主题的行为，不能据此判失败。 |
| 9.2 | 通过（Standard、Strict RC） | `build/device-1.0/continuation-20261021/system-max-display.png`、`strict-max-shelf.png`、`strict-max-settings.png`、`strict-max-settings-bottom.png`、`strict-max-reader.png`、`strict-max-aa-bottom.png`、`strict-max-aa-font-horizontal.png`、`strict-max-toc-open.png`、`standard-max-verified-{shelf,settings,settings-bottom,aa,aa-bottom,toc}.png` | 在系统“Display size and text”页面设置至最大字体 2.0、最大显示密度 540。两个 RC 的书架、设置、Aa、目录均可操作；Strict EPUB 顶栏也已检查；Aa 底部三开关可滚至，字体选项横向滑动可完整显示。大字号下章节计数变成竖向排布，但仍可辨认、左右章节按钮可用。已恢复 font_scale=1.0、密度无 override（440 dpi）。Standard 首轮宏操作偏离预期页面的采集已改名 attempt1，未作为通过证据。 |
| 9.3 | 通过（Standard、Strict RC 五类阅读器基础往返） | `build/device-1.0/continuation-20261021/rotation-results.json`、`strict-rotation-{txt,epub,markdown,code,html}-{portrait-before,landscape,portrait-restored}.png`、`rotation-contact-sheet.jpg`、`rotation-standard-results.json`、`standard-rotation-*.png`、`rotation-standard-contact-sheet.jpg` | 两个 RC 的 F6 GBK TXT、F4 EPUB、F9 Markdown、F10 C++、F8 HTML 均做竖→横→竖，正文/导航可用且返回原内容附近；Strict C++ 起始约第31行三态一致，Standard C++ 起始第1行三态一致，TXT与EPUB章节计数不变。F4 原脚注跳转后正文位于第二章、顶栏第一章的既有失配在旋转前后均存在，未把它记作旋转通过来掩盖。自动旋转恢复1、user_rotation恢复0。 |
| 9.4 | 按设计通过（用户改判，见17） | `build/device-1.0/continuation-20261021/r2-{strict,standard}-kill-result.json`、`r2-{strict,standard}-kill-reader-before.png`、`r2-{strict,standard}-kill-after-main-resume.png`、`r2-{strict,standard}-kill-manual-reopen.png`；首轮 `build/device-1.0/fix2-test/strict-F4-after-process-kill-resumed.png` | 从书架打开F10-1500-lines.cpp并滚动，Home后等待1.2秒再am kill；两个RC均确认原PID消失。用MAIN/LAUNCHER入口回到原任务，冷启动分别Standard368ms、Strict346ms，均显示书架，未按用例直接回到原书原位置。之后从书架手动打开同一条目，正文区域与回收前一致。首轮Strict F4也仅在手动点继续阅读后恢复，不能据此判9.4通过。 |
| 9.5 | 部分验证 / 阻塞 | `build/device-1.0/continuation-20261021/talkback-enabled.txt`、`talkback-welcome.png`、`talkback-finish-dialog.png`、`strict-talkback-restored.xml` | TalkBack 17.0.1 服务成功绑定，但首次教程反复抢回前台，无法可靠完成应用内实际朗读及目录快速滚动操作。先前/恢复后 UI 树包含“设置”“阅读设置”标签，不能据此代替实际朗读。服务列表已恢复为空、accessibility_enabled=0。 |
| 9.5（r4续测） | 焦点与设置/Aa激活通过（Strict）；实际语音及TalkBack拖动未核验 | `build/device-1.0/continuation-20261022/r4-talkback-{shelf-tab-focus,settings-key-activated,reader-tab4,aa-key-activated,toc-alt-last}.png`、`r4-talkback-status.json`、`r4-talkback-settings-{before,restored}.json` | 已通过教程Finish及二次确认退出；拒绝设备状态的“始终允许”请求，未永久授予。开启期间不使用会抑制服务的UiAutomation。键盘TAB使齿轮和Aa出现绿色无障碍焦点框，Enter分别打开设置页/Aa；Alt+Ctrl+Right使“快速滚动目录”轨道获得绿色焦点框。用户可协助听取，但随后询问此无障碍项的用途，未确认实际名称或拖动；已解释并关闭TalkBack，继续阅读核心测试，不把可访问标签代替实际语音。服务列表/开关恢复为空/0。Standard未进行此轮实际朗读测试。 |
| 9.6 | 部分通过（Strict RC 小窗） | `build/device-1.0/continuation-20261021/strict-multiwindow-activities.txt`、`strict-small-window-reader.png`、`strict-small-window-scrolled.xml`、`strict-window-fullscreen-restored.png` | 系统进入 freeform，逻辑窗口缩至 Rect(100,300–980,1800)，F4 EPUB 正文和顶底导航仍可见。MIUI 小窗另有物理缩放，XML 坐标不等于触摸坐标，未据自动化滑动认定交互通过；分屏及 Standard 未测。已恢复 fullscreen。期间设备自动锁屏，由用户自行解锁后继续，未改锁屏设置。 |
| 9.6（小窗续测） | 小窗阅读/滚动/Aa通过（两个RC）；分屏未测 | `build/device-1.0/continuation-20261021/r2-{strict,standard}-small-{before,after-scroll,aa}.png`、`r2-{strict,standard}-small-window-input.txt`、`r2-{strict,standard}-small-fullscreen-restored.xml` | F10 C++逻辑窗口100,300–980,1800；InputDispatcher给出的实际框100,300–716,1350，缩放0.7。按实际映射触摸后Strict可见首行19→34，两个RC均可滚动并打开Aa，正文/导航可读。仅保存小窗内部裁图，未采集背景应用。两个RC均已恢复1080×2400全屏。 |
| 9.6（r4原生分屏） | 通过（两个RC的阅读/滚动/Aa） | `build/device-1.0/continuation-20261022/r4-split-two-RC-before.png`、`r4-split-{strict,standard}-{scrolled,aa}.png`、`r4-split-{strict,standard}-fullscreen-restored.png`、`r4-split-final-tasks.json` | MIUI最近任务中对Strict选择原生Split screen，再选择Standard；两RC均显示F10 C++。初始上下窗口分别0–1188和1212–2400，两个窗各自可滚动并打开Aa，正文/设置可读。退出拖动只改变分屏比例，未直接退出；最终仅force-stop两个RC后重开，验证所有RC任务为fullscreen。此为系统窗口恢复处理，未记应用失败；无编辑缓冲区或个人应用操作。 |
| 10.1–10.2 | 未完成 | — | 验收尚未通过；两个RC包和测试素材目录保留，以便复现和续测，未执行卸载或正式包最终回看。新增F14两份源文件及两个RC里的对应测试副本/临时分组已在相关用例中移除；F15补充HTML仍保留。 |
| 10.3 | 本轮恢复通过 | `build/device-1.0/continuation-20261021/system-baseline.json`、`system-final.json`、`activities-final.txt` | 12个保存项目逐项匹配：字体1.0、显示密度440且无override、密度secure键恢复原先缺省状态、系统浅色、自动旋转1/user_rotation0、动画1/1/0.5、无障碍服务列表空且关闭、屏幕1080×2400。Strict前台全屏书架F4筛选，Standard也恢复F4筛选。 |

## 5. 性能数据

| 测量 | 结果 | 方法 / 限制 |
|---|---|---|
| 正式 Standard 冷启动 | 510、406、399、414、351 ms；中位数 399 ms | `am force-stop` 后 `am start -W`，5 次。 |
| Standard RC 冷启动 | 533、353、336、343、297 ms；中位数 343 ms | `am force-stop` 后 `am start -W`，5 次；测试仅针对 RC 包。 |
| Strict RC 冷启动 | 316、288、296、318、315 ms；中位数 315 ms | `am force-stop` 后 `am start -W`，5 次；测试仅针对 RC 包。 |
| F2 打开 | Strict 108 ms、Standard 137 ms（Activity `TotalTime`，热启动） | F2 为 30 MiB / 4000 章；约 2 秒时正文可见。不是端到端导入或内容渲染耗时。 |
| F2 连续滚动 | 3,731 帧；Janky frames 36 / 0.96%；50/90/95/99 分位 11/13/14/17 ms | 约 60 秒合成连续滑动；legacy Janky frames 为 2,825 / 75.72%，与新口径并列记录。原始输出 `build/device-1.0/fix2-test/standard-F2-scroll-gfxinfo.txt`。 |
| F3 连续滚动 | 7,455 帧；Janky frames 79 / 1.06%；50/90/95/99 分位 11/13/14/17 ms | 约 2 分钟合成连续滑动；legacy Janky frames 为 4,550 / 61.03%。原始输出 `build/device-1.0/fix2-test/standard-F3-scroll-gfxinfo.txt`。 |
| F2 / F3 PSS | 167,090 KB / 174,752 KB | 两分钟连续滑动后 `dumpsys meminfo`；原始输出在 `build/device-1.0/fix2-test/`。 |
| Strict F2 连续滚动 | 7,059 帧；Janky 19 / 0.27%；legacy 140 / 1.98%；90/95/99 为9/10/14 ms | 每次450ms合成上滑，间隔200ms；取前约60秒gfxinfo。滚至两分钟时158次上滑。原始输出 `build/device-1.0/continuation-20261021/strict-F2-60s-gfxinfo.txt`。 |
| Strict F3 连续滚动 | 7,082 帧；Janky 58 / 0.82%；legacy 222 / 3.13%；90/95/99 为10/10/12 ms | 同上，约60秒帧采样、两分钟连续滑动。`strict-F3-60s-gfxinfo.txt`。与首轮Standard的操作节奏/采样时刻不同，不据此作版本性能优劣结论。 |
| Strict F2 / F3 PSS | 266,611 KB / 155,525 KB | 各两分钟连续滑动后 `dumpsys meminfo`；`strict-F2-2min-meminfo.txt`、`strict-F3-2min-meminfo.txt`、`strict-perf-summary.json`。 |
| F3 打开 | 热重开：首个约 0.2 秒采样点已显示 Chapter 750 正文；0.8 秒、2.0 秒也显示正文 | `build/device-1.0/fix2-test/standard-f3-reopen-0.2s.png`、`standard-f3-reopen-0.8s.png`、`standard-f3-reopen-2.0s.png`；采样从触发书架书目点按后开始，不是精确首帧计时。首次打开时间仍未测。 |
| 翻页响应（r4） | 可见抬手标记至首个滚动帧：Strict同帧、Standard约15.7ms | `r4-page-turn-observations.json`及原始视频；显示pointer的P:1→0状态，源帧间隔约8–18ms，不是内核MotionEvent精确时间。三连各约0.9屏，左侧返回一屏，拖动打断已观察通过。 |
| 连续翻页30次、空闲定时器 | 30次点按帧统计已记录；空闲定时器真机无法验证 | `build/device-1.0/fix2-test/standard-page-turn-unique-30turn-gfxinfo.txt`；release未开启WebView远程调试，未据静态截图推断定时器停止。 |

## 6. 失败、阻塞情况和数据保护

当前仅4.6.2（脚注遮挡）、5.9（代码复制丢空行）等待FIX4修复和验证。其余改判、停止补测及发布门槛以第17节用户最新决定为准；以下保留历史现象和证据。

**截至r9已确认五项失败：4.5.3（F4连续模式目录跳转）、4.6.2（连续模式脚注被顶栏遮挡）、9.4（进程回收后未直接恢复阅读页）、5.9（代码范围复制丢空行），以及r5新增的4.4.1（切换主题时Aa面板与顶栏短暂不同步）；两个RC均受影响**。复现：打开 F4 → Aa 开启“章节连续滚动” → 目录 → Chapter 3。预期到第三章；两次实际均回到第二章大图（截图和日志见结果表）。单章模式同素材可正常到第三章。保留现象，未修改应用代码。


1. 已确认手机上原先没有正式主包，故无法做升级及用户书架数据比较。正式 Standard 1.0.0 已新装，未对主包执行卸载、清数据、导入、删书或改设置。
2. 新建测试目录为 `Download/YHViewer-1.0-check-2026-10-21/`，测试素材只从本地已提供的压缩包和生成脚本制作。文件已推送到其 `staging/` 子目录。另将合成唯一段落 TXT `F2-page-turn-unique.txt` 放入该目录的 `staging/fixtures/` 子目录，通过 MediaStore 内容 URI 导入 Standard RC；三连翻与 30 次点按只操作此合成测试书。测试结束后已恢复该书“章节连续滚动”为开启。
3. RC 包均与正式包并存安装；Strict RC 无 INTERNET 权限，Standard RC 有 INTERNET 权限。书架多选测试在 Standard RC 中创建了 `acceptance` 测试分组，并将 F2 设为收藏；这些修改仅属于 RC 测试数据。
4. 后续 Standard RC 书架抽查发现一项未能对应到已知合成素材清单的书籍条目；没有打开、截图或修改该条目。后续仍只通过精确搜索访问 F2/F3/F4 合成测试书。
5. 用户在 Strict RC 的系统文件夹选择器中两次手动选了同一测试目录。系统授权树为 `content://com.android.externalstorage.documents/tree/primary%3ADownload%2FYHViewer-1.0-check-2026-10-21`，其下只有本次新建的 `staging/`。用户确认文件夹导入为 60 项、带上 4 个资源、跳过 2 项不支持格式。导入目录选择均由用户操作，我没有查看或截图选择器页面。随后测试编辑器“另存为”时，系统 Downloads 选择器短暂显示目录名；我未打开任何目录，立即退出并删除了该截图。该次选择器屏幕曾短暂显示在会话中。最初自动化操作导入选择器时短暂显示过个人文件名的记录仍保留在前述原始记录中。
6. 设备查询的系统设置为深色模式关闭、字体缩放 1.0、`accelerometer_rotation=1` / `user_rotation=0`、动画缩放（窗口 1.0 / 过渡 1.0 / Animator 0.5）、TalkBack 服务未启用。动效测试期间动画临时设为 5.0 / 5.0 / 2.5，随后恢复为 1.0 / 1.0 / 0.5；字体临时设为 1.3 后恢复为 1.0；旋转测试期间临时锁定横屏后恢复自动旋转（`accelerometer_rotation=1`、`user_rotation=0`）。结束复查屏幕回到竖屏，其余上述设置值均恢复。
7. 本轮 Android crash buffer 中有若干 `uiautomator` / `UiAutomation` 服务自身注册失败或 `Bad file descriptor` 栈，不属于 YHViewer 进程；`dumpsys activity exit-info` 未见 YHViewer 崩溃退出记录。Strict RC 唯一退出记录是自动化发起的 force-stop，Standard RC 没有退出记录。日志文件：`build/device-1.0/crash-buffer-current.txt`、`exit-info-standard.txt`、`exit-info-strict.txt`。
8. 本轮一次坐标点击误打开了设备上的无关应用。我立即按返回回到 Strict RC，未继续查看或操作；对应屏幕 XML 和截图已删除，未作为测试证据保留。

### 2026-10-21 续测：Aa 与系统主题

Strict RC 的连续/单章模式均已依次选择四个主题，稳定画面保存在 `build/device-1.0/continuation-20261021/strict-{continuous,single}-theme-*.png`。连续模式字号由100%拖到160%，拖动中仅剩滑杆，松手恢复面板；退出书架重开后仍为160%。行距由1.9拖至2.0、页边距由20拖至36也已采集拖动中/结束画面。随后恢复100% / 1.9 / 20。切换无衬线字体后已恢复跟随主题。单章拖字号的中间/结束截图已保存，尚未逐帧量化应用时刻。系统跟随测试明确先选“跟随系统”，深色时阅读页和书架均变深色，恢复系统浅色后书架回浅色；此前Standard纸张主题未随系统变色不作为失败。Strict初始主题为跟随系统，已恢复；连续滚动和左右点按翻页开启，临时常亮已关闭。


### 本轮续测结束复核

- 安装中两个RC APK的SHA-256与FIX2记录一致，证据为本轮`*-installed-apk-sha256.txt`；未重建或替换应用。
- `strict-exit-info-final.txt`、`standard-exit-info-final.txt`没有Java崩溃、native崩溃或ANR退出记录；本轮按PID及后来按两个RC UID采集的main/crash日志没有FATAL EXCEPTION或ANR行。自动化的force-stop记录与应用崩溃区分。
- 本轮新增了H02的独立无资源副本F15；原始H02和原F1目录未改。F14补充测试文件只在新建测试目录内创建并按哈希校验后删除；两个RC内F14副本和临时分组也已通过删除用例移除。其他RC测试数据、主包、Preview、QA包保留。
- 自动化采集有若干`uiautomator`退出137，已记录到`automation-errors.txt`并仅对该错误重试一次。Standard最大字号第一次宏操作偏离页面的证据标为`attempt1`，重测按标签定位并核实页面后才记通过。恢复显示大小时额外核对secure键，已恢复原先不存在的键状态。
- 本轮没有修改应用实现、提交、推送或发布。当前失败为4.5.3、4.6.2和9.4，两个RC均受影响。

当前验收范围已冻结，以第17节为准。FIX3见第14节；TalkBack取消和三个用户确认项见第15节。历史“未测”已由用户决定不再补测，不再生成新的待办。

## 7. 未验证项与后续

4.1 的自然滚动章节边界经用户确认符合预期；新增 4.5.3 主动目录跳转错误与该边界不同，已单列失败。4.6.2 的连续模式脚注已在两个 RC 确认被顶栏遮挡并判失败；Strict 单章模式从第三章“返回脚注”后可见第一章脚注内容。9.4也已在两个RC确认无法直接恢复阅读页。第 2–9 节仍有未完成用例，本报告不是验收通过结论。当前不执行方案第 12 节的打标签、发布、更新 README 或删除旧 Release；需先由用户审阅问题证据并决定后续。

截图、设备输出和素材均位于 git 忽略目录 `build/device-1.0/`，不随本报告提交。


## 8. 暂停记录（2026-10-21 23:43，UTC+8）

用户要求“当前任务完成就准备暂停，做好记录”。已完成正在进行的三文件原生分享：Strict和Standard均报告成功导入3项，之后停止新增用例。下次接续见 `docs/verification/V1_DEVICE_TEST_HANDOFF.md`。

- 本轮补充：两个RC的HTML字号200%及重开保留；系统选择器10文件；F1独立根34文档/2资源及H02样式；Standard重导入保留收藏/分组/位置；两个RC另存为并加入书架；TXT三个滑杆实时采样/保存/恢复；F3 Aa稳定和底部开关；代码原生复制不带行号；5倍动效实际录屏；小窗滚动/Aa；原生三文件分享。
- 新确认失败9.4，连同4.5.3、4.6.2累计3项，两RC均受影响。未修改应用代码。
- `r2-system-baseline.json` 与 `r2-system-final.json` 的12项逐项一致；字体1.0/密度440无override、浅色、自动旋转1/user_rotation0、动画1/1/0.5、无障碍关闭等均恢复。两个RC的所有任务均为fullscreen；书架均恢复F4筛选，Strict前台；Files选择清空，原网格视图保留。TXT100%/1.9/20、HTML100%已恢复。
- `r2-crash-anr-final-check.json`：9,884行RC UID日志中FATAL/ANR匹配0；两个RC退出记录无reason4/5/6。日志进程82903已停止，临时`/sdcard/yh-acceptance-window.xml`已删除。
- 新导出文件`R2-SaveAs-20261021.md`、`R2-SaveAs-Strict-20261021.kt`及测试录屏留在测试目录；H02临时rc-r2-retain分组和本轮收藏已清理，原源文件哈希一致。RC/正式包/Preview/QA均未卸载或清数据；未提交、推送或发布。


## 9. 续测后暂停记录（2026-10-22，Asia/Taipei）

用户回复“继续”后，仅补当前一组2.6单文件分享与2.7原生打开方式；两RC的F9 Markdown均通过，随后依“当前任务完成就准备暂停，做好记录”停止新增测试。未修改应用代码，原有三个失败继续保留，完整验收仍未通过。

- 新证据在`build/device-1.0/continuation-20261022/`，辅助工具复制到此目录。原始测试目录继续使用2026-10-21目录，没有创建或浏览个人目录。
- 两个已安装RC的SHA-256与FIX2记录一致，`r3-installed-apks.json`保留核对。没有重建/替换安装包。
- `r3-system-baseline.json`与`r3-system-final.json`12项一致；额外屏幕超时300000ms也一致。本次开始自动旋转为0，与昨日不同，保留用户的新基线0；没有恢复成旧基线1。
- 两RC已回书架F4筛选，Strict前台；Files选择清空，网格视图保留。没有改变阅读偏好、屏幕尺寸或默认应用。
- `r3-crash-anr-final-check.json`：本轮833行RC UID日志无FATAL/ANR匹配，两个RC退出记录无reason4/5/6。采集进程13675已停止，临时UI XML已删除。
- 下次优先导入中进度/加载提示，再继续7.4链接按压、7.5其他阅读器复制、代码专属选区空行、分屏/TalkBack和逐帧性能等未完成项。不得清除含未知条目的Standard书架以伪造首次基线。
- 正式包、Preview、QA和个人书籍未操作；RC、素材、输出及复现证据保留。未提交、推送或发布。恢复细节见`docs/verification/V1_DEVICE_TEST_HANDOFF.md`。


## 10. r4续测记录（2026-10-22）

用户要求“继续完成后续的内容”后，本轮补测核心交互；用户随后询问TalkBack无障碍项的用途，已解释原方案9.5并关闭，不再等待听读配合。实际语音及TalkBack拖动仍未核验，不作通过结论。完整验收未通过，未执行发布或清理RC。

- 新完成：两个RC十文件原生导入的进行中进度；F2/新URI同字节F3首次导入加载提示及小F9无转圈；EPUB/Markdown链接按压变淡且无色块；TXT/EPUB/MD/HTML实际原生复制粘贴；代码专属范围复制；墨色换章/目录/书签/MD/代码路径逐帧白闪检查；翻页可见抬手时序、三连及左翻、拖动打断；原生分屏阅读/滚动/Aa；Strict单页书签跨位置跳转。
- 新确认失败5.9：两RC代码选区跨中间空行时复制会丢失空行；不带行号的要求仍通过。加上4.5.3、4.6.2、9.4共四项失败，均未修复。
- 证据集中在`build/device-1.0/continuation-20261022/r4-*`。新同字节F3仅用新URI测试首次导入，未清除旧缓存/Standard未知书架条目，不将其等同历史首次安装基线。
- `r4-system-baseline.json`和`r4-system-final.json`12项一致；额外screen_off_timeout维持300000ms。pointer_location/show_touches与动画另经核对恢复原键状态。主题恢复Strict跟随系统、Standard纸张，连续模式开，TXT100%/1.9/20、代码100%/不换行、HTML100%维持原值；新F3书签和Strict单页C++临时书签均已删除至0。原生粘贴编辑全部不保存。
- 分屏退出拖动仅改变比例；最终只force-stop两个RC后重开，`r4-final-tasks.json`确认所有RC任务均fullscreen。两RC均回书架F4精确筛选，Strict前台；Files选择清空且原网格保留。TalkBack服务列表为空、accessibility_enabled=0，未永久授权设备状态或修改默认应用。
- `r4-crash-anr-final-check.json`：本轮8558行RC UID日志无FATAL/ANR匹配，两个RC退出原因无4/5/6。采集进程15318已验证命令后停止；临时UI XML已删除。测试录屏、新测试副本和复现数据保留在指定测试目录。
- 未改应用代码、构建或替换APK，未提交、推送、卸载或发布。后续按第6节最新剩余范围继续；不能将历史行中的“未测”与后面的补测结果混为一项当前状态。


## 11. r5续测记录（2026-10-22）

继续普通阅读核心验收，不再启动TalkBack。应用实现及安装APK未改，五项已确认失败仅记录，完整验收未通过。以下r5结果补充前面的历史行；用户随后要求完成当前任务后暂停，已停止手机操作；最终恢复/日志检查见本节末。

| 用例 | r5结果 | 证据（均在`build/device-1.0/continuation-20261022/`） | 说明 |
|---|---|---|---|
| 8.2–8.3 | 两RC首次/重开已记录 | `r5-open-time-observations.json`、八个`r5-*-{F2,F3}-{first,second}-shelf-open.mp4`和`-transition-all-frames.jpg` | 新URI字节相同副本经原生两文件导入后，第一次从书架打开与立即重开。Strict F2约295/234ms、F3约425/218ms；Standard F2约249/233ms、F3约414/231ms。可见P:1→0首帧至首个可读正文编码帧，原始VFR PTS逐帧核对；不是内核输入时刻，也不是首次安装或无索引重建。每种仅一对样本，不作统计性能保证。 |
| 4.3.1–4.3.3 | 通过（两RC，所测对照） | `r5-{strict,standard}-continuous-glass-{paper,ink}-comparison.png`、`-single-epub-{paper,ink}-comparison.png`、`-find-opaque.png`、`-glass-results.json` | 纸张/墨色连续TXT栏后背景文字模糊、控件清楚；F4单章背景文字保持锐利但被底色压低，控件可读；查找栏不透明。每对显/隐工具栏的未遮挡正文区域MAD0，排除不同位置导致对照失效。 |
| 4.4.2、4.4.4 | EPUB字号生效时机通过（两RC） | `r5-{strict,standard}-{continuous,single}-epub-font-*`、`-font-comparison.jpg`、`r5-epub-font-comparisons.json` | 同一F3第1章，7秒拖100→160%，约1.8/3.6秒采样时input仍运行；连续模式正文随110/130%变化，单章两次样本正文均与100%基准逐像素一致(MAD0)，松手后才变大。浮动滑杆外无面板/遮罩/顶底栏。已恢复100%/1.9/20和原主题/连续开启。未量化滑杆背后模糊强度。 |
| 4.4.1 | 视觉细节，不阻塞发布（用户改判，见17） | `r5-{strict,standard}-{continuous,single}-theme-switch.mp4`、`-lag.json`、`-lag-frames.jpg`及四主题稳定截图 | F3打开Aa，连续/单章依次选系统、纸张、墨色、终端；面板先换色，顶栏短暂保留旧色。Strict连续纸张→墨色panel PTS18.029056、顶栏背景首变18.037189，约8ms；Standard同路径17.942911/17.975033，约32ms。单章与切终端也复现，采样差约11–42ms。按“同一时刻变色，不分块”判失败，未修复；这是屏幕编码帧观察，未推断内部原因。Aa覆盖大部分正文，不能据此证明隐藏正文的切色时刻。 |

| 3.2 | 通过（两RC，本轮11本F4样本） | `r5-*-cover-scroll-10s.mp4`、`-overview.jpg`、`-cover-check.json`、`-operations.json`、`-gfxinfo.txt` | 精确F4筛选，9份同源副本加既有F4共11本，实际位图为源文件中的纯米色封面。Strict约10.08秒33次/Standard约10.05秒32次上下滑动。全部1246/654个源帧扫描米色封面内部暗字，候选0；概览未见闪文字封面。检测器不能证明所有颜色占位图都不存在，仅对该样本作结论。gfxinfo Strict1242帧/现代Jank0.32%/legacy0.08%/p90/95/99=5/6/9ms；Standard653帧/两口径Jank0%/7/7/8ms。 |
| 3.9 | 按压缩小通过；松手回稳仅Strict充分可见 | `r5-*-cover-press.mp4`、`-all-press-frames.jpg`、`-target.json` | 320ms短按，封面小幅缩小后打开阅读页，无抖动；Strict源帧显示松手后回到原尺寸再切页，Standard切页较快，回稳全过程覆盖有限，未据此宣称所有回弹帧都已检查。裁图脚本初版奇数宽度造成解码错行，改偶数边界后重生成并复核，非应用花屏。 |
| 3.5（多选栏动效） | 通过（两RC） | `r5-*-selection-bar.mp4`、`-overview.jpg`、`-shown.{xml,png}`、`-dismissed.png` | 临时5倍动画，900ms长按进入已选1项，底部胶囊从下方移入并逐步变实；取消后退出。未新增收藏/分组/书签或删除任何书。动画及Standard原列表/Strict原封面已恢复。 |
| 2.8（单文件导入计时） | Strict已记录；Standard未完成 | `r5-strict-F2-native-import.mp4`、`-frame-metrics.json`、`-transition-all-frames.jpg`、`r5-native-import-observation.json` | 新字节相同`R5-import-F2.txt`，原生选择器单击松手可见P:0首帧PTS2.199233，首个正文帧3.283811，约1085ms；包含选择器退场/导入/渲染，不是单独解析耗时。读到1/4000，无卡死；导入提示见r4证据。用户暂停时排队的Strict计时已完成，Standard仍在准备选择器，UiAutomator137后停止，未选择文件导入，不作其计时结论。 |

新增测试素材仅在指定测试目录：`R5-perf-F2.txt`、`R5-perf-F3.epub`分别与F2/F3源SHA相同，原生批导入均成功2项且没有自动打开；TXT导入已建索引、EPUB导入验证解析不代表阅读缓存预热。新文件名不改变EPUB元数据书名，精确F3筛选按lastOpenedAt最新导入项首次打开，读到1/1500，未清缓存/数据。另新增9份16,132字节同源F4封面测试副本，源SHA及手机复制校验见`r5-cover-sources.json`；只用于已知F4筛选，未查看Standard未知条目。


本轮暂停与恢复：

- 用户要求“完成当前任务就暂停，我休息一下，需要把手机拿走”。两RC书架测试组完成；检查停止队列时Strict单文件导入计时已完成，Standard准备阶段UiAutomator退出137，未进行文件点选。退出选择器时恢复脚本最初期待Strict，实际返回Standard，guard立即停止，没有继续对错误前台输入；最终两个RC均回F4筛选。此次并发恢复偏差仅记自动化问题，不判应用失败。没有再启动后续用例。
- `r5-system-baseline.json`与`r5-system-final.json`12项完全一致；`r5-extra-baseline.json`/`r5-extra-final.json`的超时300000ms、pointer_location缺省、show_touches0一致。主题Strict跟随系统/Standard纸张，字号100%/行距1.9/边距20，连续ON；书架Strict封面/Standard列表，Files原网格、无选择；TalkBack关闭。所有RC任务fullscreen，Strict前台。
- `r5-crash-anr-final-check.json`：本轮9025行RC UID日志无FATAL/ANR匹配，两个RC退出记录无reason4/5/6。日志进程37597命令核对后已停止，临时UI XML已删除。最终任务校验第一版把简写Task引用也当完整窗口记录导致断言失败，改为带`* Task{`的完整任务行后通过，没有改变手机窗口。
- `r5-source-baseline.json`/`r5-source-final.json`六个继承改动文件SHA逐一相同；未改应用实现、构建或替换APK，未提交/推送/发布/卸载。正式包、Preview、QA、个人文件与未知书架条目未操作；RC、新素材和证据保留。手机操作已停止，可断开连接，后续仅整理电脑上的文档。


## 12. r6续测记录（2026-10-22）

用户明确“继续”后重建当时基线并接续普通阅读测试，未修改应用或安装包；五项既有失败保留。

| 用例 | r6结果 | 证据（`build/device-1.0/continuation-20261022/`） | 备注 |
|---|---|---|---|
| 2.8（Standard单文件导入） | 已记录，补齐r5待测 | `r6-standard-F2-native-import.mp4`、`-keyframes.jpg`、`-frame-metrics.json`、`r6-native-import-observation.json` | 未导入过的新源`R5-import-F2.txt`，可见松手PTS2.147489（前一P1帧2.137167），正文首帧3.808833，约1661ms。包含选择器退场/导入/渲染；首次已有副本重开数据仍见r5，不将1.66秒套用1秒重开门槛。单次样本，不据此比较两版本总体性能。 |
| 3.3 | 两RC通过所测搜索 | `r6-{standard,strict}-shelf-search-{author,title,filename,missing}.{png,xml}`、`r6-search-results-contact.jpg`、`r6-shelf-results.json` | 作者子串Device（源OPF作者实际为Device Test，含空格）、标题F4、文档文件名F9-content分别返回已知测试条目，不存在词r6-no-such-book-1049返回0。首次误用DeviceTest的0结果不是失败，保护断言在截图前停止；源文件核对后纠正输入。搜索EPUB源文件名与元数据标题不同时的行为未作结论。 |
| 3.6（Strict） | 返回中段视口通过 | `r6-strict-shelf-mid-{before,reader,return}.{png,xml}`、`-contact.jpg`、`-pixel-check.json` | F4精确筛选滑到中段开书、直接点‹书架返回，没有重输筛选词。可见封面边界相同，列表区域(0,650,1080,2200)MAD0。同图同名F4副本只能证明视口没有跳动，不据此证明最近阅读重排后同一条目的身份。 |

| 4.4.7（Strict） | 两分钟常亮通过 | `r6-strict-stay-awake-{enabled,120s,restored}.{png,xml}`、`-results.json` | F3阅读打开常亮，系统超时临时30000ms，30.09/60.18/90.27/120.36秒只读检查均Awake，期间无输入。随后关闭开关并恢复300000ms。 |
| 8.5（Strict 30次翻页） | 已记录 | `r6-strict-30turn-{before,after}.{png,xml}`、`-operations.json`、`-gfxinfo.txt` | F2连续模式合成30次侧边点按，365渲染帧，现代Jank13/3.56%，legacy0/0%，p50/90/95/99=7/9/10/13ms。未录像逐行核对，不据最终画面证明无漏行或重复；连续翻页行为证据仍见r4。 |

自动化与保护：r5交接曾记录Files网格，但本轮实际为列表；旧脚本视图断言在点文件前停止，记录新基线`r6-files-baseline.json`后按列表路径完成并保留列表。未把旧网格强加给当前环境。所有搜索截屏均先按已知测试素材元数据白名单核对，未查看或修改未知条目；正式包、Preview、QA及个人文件未操作。


本轮恢复复核：`r6-system-baseline.json`/`r6-system-final.json`12项和`r6-extra-baseline.json`/`r6-extra-final.json`3项完全相同。超时300000ms、指针缺省、触摸显示0、动画1/1/.5、旋转0/0、字体1.0、密度440无override、系统浅色、无障碍关闭均保留。两RC回F4精确筛选、Strict前台，任务均fullscreen；Files保留本轮实际列表基线，无文件选择。未改变阅读主题、字号/行距/边距或书架视图，无新增书签/收藏/分组。

`r6-crash-anr-final-check.json`记录4080行RC UID日志无FATAL/ANR，退出记录无reason4/5/6；日志进程52508命令校验后停止，临时UI XML删除。两个安装APK哈希不变，`r6-source-baseline.json`/`r6-source-final.json`六文件SHA一致，未改应用代码、构建、提交、推送、卸载或发布。五项既有失败继续保留；报告不构成完整验收通过。


## 13. r7续测记录（2026-10-22）

用户再次“继续”后补八类书架筛选及连续EPUB Aa细项，沿用原两个RC包，仅测试和记录。

| 用例 | r7结果 | 证据（`build/device-1.0/continuation-20261022/`） | 说明 |
|---|---|---|---|
| 3.4（类型） | 两RC八类通过 | `r7-kind-sources.json`、`r7-*-kinds-{selected,imported,all-before,all-restored}.*`、`r7-*-kind-{0..7}.{png,xml}`、`-kind-count-audit.json`、`-kind-contact.jpg` | 在指定测试目录新增R7-kind前缀8个小素材，原生各批导入8项成功。网页/笔记/代码/数据/小说/电子书/纯文本/图片逐类选中，标签checked、计数1及对应唯一内容经XML和画面复核。范围标签仍显示原组总8项，清除类型选择后恢复8项。收藏/分组功能证据沿用此前3.5。未查看未知条目或新建测试分组。 |

| 4.4.3（连续EPUB） | 两RC实时预览/保存/重开通过 | `r7-*-epub-{line,margin}-{before,during-1,during-2,released,persisted}.*`、`-samples.json`、`-contact.jpg`、`r7-aa-body-observations.json`、`r7-*-epub-font-sans{,-persisted}.*` | F3第1章，行距1.9→2.3、边距20→36各拖7秒，约1.8/3.6秒截屏时input进程仍运行。正文在松手前随行距2.0/2.1和边距24/28改变，仅留浮动玻璃滑杆；其后保存目标值、退出书架重开仍保留。无衬线字体同样重开保持选中。两个RC均恢复100%/1.9/20、原跟随主题字体、原主题与连续开启。区域MAD仅作变化诊断，不是排版延迟/内部模糊强度计量。 |
| 4.2.6（末章边界） | 两RC所测EPUB通过 | `r7-*-last-chapter-{before,after}.{png,xml}`、`-result.json` | F3关闭连续，目录筛到Chapter1500，滑到章尾后点右侧。均保持1500/1500、下一章禁用，正文区域(0,300,1080,1950)MAD0，无越界或回绕。短章内容全在当前视口；TXT末章尚未单独补这条。结束恢复连续模式开启。 |

素材与自动化：HTML、MD、KT、CSV、两章TXT、LOG和无脚本SVG为新合成小文件；EPUB沿用F4内容仅改元数据题名为R7-kind-ebook，未编辑应用。源SHA校验相同。Strict小说视觉标题会格式化文件名，封面文件标识保留原名；初版脚本仅按视觉Text匹配导致断言，核对后按标识继续，不判应用失败。横向滚动初版落点在标签外，改实际标签Y后完成。没有重复导入；Strict分段恢复JSON只保留末三项，八项完整结果以`kind-count-audit.json`和0..7的XML/截图为准。Files原列表保留，无活动选择；8个源文件和RC副本保留作复现，不清理旧素材。


结束复核：`r7-system-baseline.json`/`r7-system-final.json`12项与`r7-extra-baseline.json`/`r7-extra-final.json`3项逐一相同，字体1.0/密度440无override/浅色/旋转0/0/动画1/1/.5/无障碍关闭/1080×2400，超时300000ms、pointer缺省、show_touches0保留。两个RC均恢复原阅读偏好：Strict跟随系统、Standard纸张，跟随主题字体、字号100%/行距1.9/边距20、连续ON；两RC书架F4筛选，Strict前台，任务fullscreen；书架Strict封面/Standard列表及Files列表保留，无文件选择。没有新增收藏/分组/书签或删除旧数据。

`r7-crash-anr-final-check.json`：5383行两RC UID日志无FATAL/ANR匹配，退出原因无4/5/6。采集进程55682核对命令后停止，临时UI XML删除。两个安装APK哈希不变；`r7-source-baseline.json`/`r7-source-final.json`六个继承改动文件SHA相同。未修改应用、构建、提交、推送、卸载或发布。五项既有失败仍未修复，完整验收未通过；八份新素材和证据保留在指定测试目录。

## 14. FIX3授权、重建与Markdown重测（2026-10-22–23，最新）

用户明确授权应用`yhviewer-v1.0-fix3-doc-defaults.patch`，完整单测后重建四APK；手机只覆盖两个RC，Markdown基线允许改为1.8/20；仅5.6、5.7用新包重测，其他既有通过结果按用户指定沿用，不重做。

- FIX3单独提交：`48cd991c30884456dcb1349864fc7c80a089b84c`，3文件13增/5删。补丁SHA-256 `6768a709bbeee372c0e08db0f880261be7ac3e21d13f4491c205f97209186156`。改Markdown默认行距1.8、边距20、模板fallback边距20及对应默认值/刻度断言，没有迁移已有偏好。
- 此提交仅含FIX3。原FIX1/FIX2六个继承改动文件（其中两份未跟踪）继续保留，实际构建状态是该提交加这些既有改动，并非干净提交。`build/device-1.0/fix3/source-state.json`、`inherited-working-tree.patch`及`inherited-untracked/`保存精确补丁/副本/哈希，构建后再次核对未变化。
- 先只应用测试hunk：`V13GlassTest.docDefaultsMatchMarkdownTemplate`在旧1.75实现上预期失败，1项1失败；再应用实现。随后`:app:test`完整四变体Strict/Standard/StrictRc/StandardRc各266项，共1064项，失败/错误/跳过均0。`fix3/red-unit.log`、`full-unit.log`、`full-unit-summary.json`。
- 四个Release变体（R8开启）构建成功，`fix3/build-four.log`；四个APK的applicationId、1.0.0/versionCode12、INTERNET边界及Pyodide资源静态检查通过。正式签名私钥本地未配置，正式Strict/Standard是未签名构建，不是可发布的已签名产物；两个RC继续用既有临时测试证书`2677ad64917b19a529be56834234a8bca526f8db176a1025fca24035c121ca86`签名。
- 只对`.rc.strict`与`.rc.standard`执行`adb install -r`，均成功且手机安装路径SHA与新APK逐一相同，无卸载/清数据。`fix3/protected-packages-before.json`/`after.json`确认正式、Preview、QA三包版本/安装及更新时间不变。

| 新APK | 签名状态 | SHA-256 |
|---|---|---|
| strict | 未签名，未安装 | `8092820536966ea6420126e4bfa87559b6f110d97d5e1d01ae0f8d6e9c953a4a` |
| standard | 未签名，未安装 | `ea9129a76490ac35dd18b6279abc6cc519a59b96002923f1e15536ab3b5a594d` |
| strictRc | RC测试证书，已覆盖安装 | `d47d93a724b3d571edbc1449857f51191be1e33561d008da6048b182ccbf7fcc` |
| standardRc | RC测试证书，已覆盖安装 | `882952d20c12d083dc01aad1cd2b691f3fd5c83400352b69d2b67344aa765554` |

四个规范交付路径、applicationId及完整哈希见`build/device-1.0/fix3/four-apks.json`/`SHA256SUMS.txt`，产物在`fix3/artifacts/`。另留四个未签名原始构建作核对，不与规范四APK混计。


### 14.1 新包5.6、5.7重测

仅对新FIX3两个RC重测5.6、5.7，其他已通过用例按用户授权沿用旧结果；不将本轮结果称为所有用例在FIX3上完整重跑。证据统一保存在`build/device-1.0/continuation-20261022/`，10月23日续测仍沿用该目录。

| 用例 | 新FIX3结果 | 证据与范围 |
|---|---|---|
| 5.6 | **两RC通过** | `r9-{strict,standard}-F9-render-{baseline,tail}.{png,xml}`。重新从已知F9源打开，表格两列正常、JS代码有语法高亮、脚注引用及正文可见；`<div>raw html</div>`等原始HTML显示为文字。脚本文本同样可见，正文未被替换。 |
| 5.7（三滑杆） | **两RC通过** | `r9-*-md-{scale,line,margin}-{before,during-1,during-2,released,persisted}.*`、`-samples.json`、`-contact.jpg`。以100%/1.8/20为基线，分别拖到160%/2.3/36，7秒拖动约1.8/3.6秒采样时input进程仍运行，正文在松手前随字号110/130%、行距1.9/2.1、边距24/28改变；仅留浮动滑杆。松手后保存，回书架重开仍保持目标值。像素MAD仅作变化诊断，不代表内部时延或模糊强度。 |
| 5.7（字体/独立保存） | **两RC通过** | `r9-*-md-{all-persisted,independent}`、`r9-*-book-independent`。MD设置160%/2.3/36与无衬线后重开保持；书籍仍100%/1.9/20、跟随主题字体。反向将测试书字号改130%，MD仍保持160%/2.3/36及无衬线。最后测试书字号恢复100%，MD恢复100%/1.8/20和原字体。 |
| 5.7（主题） | **两RC通过所测保存路径** | `r9-*-md-theme-{body,persisted}`、`r9-*-shared-theme-book`、`r9-*-theme-restored`。从Strict跟随系统、Standard纸张临时切为墨色，重开MD保持；书籍沿用同一全局主题，符合当前设计。最终恢复原主题。主题与字号分开描述，不误称主题是MD独立偏好；本轮没有重新测四主题同步帧时序，既有4.4.1失败仍保留。 |

`r9-persistence-final-audit.json`逐一校验两RC的保存值、无衬线、书籍基线、反向独立、主题共享和最终恢复。`r9-md-visual-observations.json`保存六组正文变化诊断，六张拖动对比图已目视核对。

Standard于10月22日完成边距松手后测试中断，没有完整结束/恢复证据；10月23日用户重新连接并解锁后，先保存新系统基线，再重跑Standard的5.6/5.7整组。旧Standard文件另保存在`r9-standard-interrupted-20261022/`，本节以续测后的完整证据为准，不把自动化中断算应用失败。

### 14.2 收尾、保护与暂停记录

- 两段RC UID日志分别3127和4836行，无FATAL/ANR匹配，两个RC退出记录无reason4/5/6；`r9-crash-anr-final-check.json`。旧logger已退出，续测logger核对命令后停止，临时`/sdcard/yh-acceptance-window.xml`删除。断开期间没有连续日志覆盖，不据此作后台全时段保证。
- 重新连接时12+3项系统设置与10月22日基线一致；结束时与10月23日新基线再次逐一相同：字体1.0、440dpi无override、1080×2400、系统浅色、旋转0/0、动画1/1/.5、无障碍关闭，屏幕超时300000ms、指针键缺省、触摸显示0。没有强行覆盖用户隔夜设置。
- MD最终100%/1.8/20、跟随主题字体；Strict主题跟随系统、Standard纸张。测试书100%/1.9/20、跟随主题字体，未更改连续模式。两RC回书架F4精确筛选，Strict前台、所有RC任务fullscreen；书架原Strict封面/Standard列表保留。本轮不用Files选择器，未新增书签、收藏或分组，测试源及RC副本保留。
- 四个交付APK哈希、手机两个已装RC哈希再次核对一致；正式/Preview/QA版本和安装/更新时间不变。FIX3提交未变化，六个继承源码SHA及原tracked二进制diff SHA与构建归档相同，没有追加实现修改。
- 用户授权的FIX3应用、完整单测、四包重建、两RC安装及5.6/5.7新包重测已完成并记录，手机测试停止。未推送、发布、卸载或清数据；正式两个APK仍未签名。既有4.5.3、4.6.2、9.4、5.9、4.4.1五项失败未修，其他历史未测/不可测项未补，**完整1.0.0验收仍未通过，不能据本轮宣布可发布**。

## 15. 用户调整验收范围与补充确认（2026-10-23，最新）

用户在本对话中明确取消TalkBack测试，并确认设置页外链、旧版本覆盖升级、Standard首次空书架。这些决定覆盖历史记录中的相应待测/阻塞状态；历史采集事实和证据仍保留。

| 项目 | 当前状态 | 确认来源与处理 |
|---|---|---|
| 9.5 TalkBack实际朗读、焦点和拖动相关验收 | **已取消，移出本轮范围** | 用户明确“这部分取消”。不再列未完成或阻塞，不再启动TalkBack或要求听读；不代表删除现有无障碍标签或修改应用实现。 |
| 3.8 设置页被手机应用锁挡住的外链跳转 | **用户确认通过** | 用户回复“我确认，这个能够完成”。不再要求自动化解锁应用锁或重复验证；本轮未新增跳转截图。此确认仅覆盖设置页外链，不覆盖5.5的F13脚本主动跳转拦截项。 |
| 旧版本覆盖升级 | **用户确认通过** | 用户明确确认。代理首轮未具备旧版主包基线的记录仍保留；不新增覆盖安装、不伪造升级前后数据或证据。 |
| 2.1 Standard首次空书架 | **用户确认通过** | 用户明确确认。不再因缺少首次界面自动化截图列待测；不清空当前RC书架以重造首次状态。 |

本次仅更新报告与交接，未操作手机、修改应用、重新构建或发布。五项既有失败及其余未完成项不受这次范围调整影响，完整验收仍未通过；手机测试继续暂停。


## 16. r10普通阅读补测（2026-10-23，历史判定见第17节更新）

用户再次要求继续测试。沿用FIX3两RC，不改应用、不重建或安装，不修复已发现问题；TalkBack取消、设置页外链/旧版升级/Standard首次空书架由用户确认通过的决定保持有效。证据仍在`build/device-1.0/continuation-20261022/`。

| 用例 | r10结果 | 证据与范围 |
|---|---|---|
| 4.2.6（Standard TXT末章） | **通过** | `r10-standard-txt-last-{before,after}.{png,xml}`及`-result.json`。已知两章R7-kind-novel关闭连续后到第二章末尾，点右侧仍2/2，正文区域MAD0，没有回绕/越界；原连续模式已恢复。Strict同项已有r8证据，两RC EPUB末章已有r7证据。 |
| 3.9（Standard封面） | **部分验证，完整松手回稳仍未确认** | `r10-standard-cover-{normal,slow}.mp4`、`-source-frames.jpg`、`-keyframes.jpg`、`-frames.json`及`r10-cover-observations.json`。动画1倍/5倍各320ms短按，缩小及离开书架前尺寸恢复可见；慢速片首次可见P:0为PTS2.315911，该帧封面已退场，不能据宽度变化强判完整松手回稳。按源VFR PTS，未按主机命令时间推断UP。原Standard列表和系统动画/触摸指示恢复。 |
| 5.5（无手势脚本跳转） | **两RC通过所测默认权限路径** | 新合成`R10-script-navigation.html`在1500ms后执行`location.href`指向保留域example.invalid，3500ms后留下R10_STILL_IN_DOCUMENT。两截图可见该标记，各自PID日志均`blocked navigation ... gesture=false`，始终在原RC；`r10-script-navigation-result.json`。没有改变文档权限，不将本结果扩大到已授权脚本导航或所有脚本API。原F13真实点击路径沿用旧证据。 |
| 5.15（Standard JS超时） | **通过** | 新`R10-js-timeout.js`仅含`while (true) {}`；`r10-standard-js-timeout.{png,xml}`、`-result.json`显示超时、强制终止及5007ms。结果面板已关闭，没有编辑既有脚本或放宽超时。Strict JS超时、Standard Python正常/Unicode/超时/停止均沿用旧证据。 |
| 3.3（EPUB原文件名搜索） | 按设计通过（用户改判，见17） | 新`R10-filename-alias.epub`沿用F4，仅把OPF题名改为R10 distinct title。搜索distinct可唯一找到这本书；搜索R10-filename-alias均为空并显示无结果。`r10-{standard,strict}-alias-search-{title,filename}.{png,xml}`、`r10-epub-alias-search-result.json`。与方案3.3和搜索框“搜索书名、作者、文件名”预期不符，仅记录未修。 |
| 2.2（LOG原生文件选择器） | **两RC通过所测路径** | 原生Files在指定fixtures目录点`F11 long （日志）.log`，文件名空格/全角括号保留，纯文本100行及LOG marker正确。`r10-*-log-{picker-source,native-opened}.{png,xml}`、`r10-log-picker-results.json`。与此前XML/CSV/Kotlin/无标题TXT原生多选路径合读；不宣称逐一重测F1全部格式组合。Files原列表不改，无遗留选择。 |
| 4.5.5（Strict页内查找） | **所测计数/上下匹配路径通过** | R4同源唯一段落TXT搜索copper，↓后计数按一项前进、↑回原计数；`r10-strict-page-find-{before,next,previous}.{png,xml}`、`-result.json`。高亮截图另经核对；不以此证明查找打开时所有跨章补章行为。 |
| 4.5.6（Strict全书搜索） | **通过** | 同源文件240处copper，滚动结果到第三部分第三个匹配marker-0139后点击；读到3/4且当前高亮计数第3项，截图可见marker-0139。`r10-strict-fullbook-{results,third-chapter-third-result,target-find,target-closed}.*`、`-result.json`。补齐Strict；Standard沿用首轮。 |

### 16.1 r10问题复现与来源（现已按设计改判通过，见第17节）

新EPUB源SHA-256：`52fb52fad00aba5a025f38402343829263a4e7d73229dc5df70ce8fabb685843`。两RC各导入同一文件，回书架先搜distinct确认导入成功，再清除并输入R10-filename-alias；两者均无结果。测试文件名和元数据题名确实不同，未把文字输入错误或不存在的文件当应用失败。该失败追加为第六项；4.5.3、4.6.2、9.4、5.9、4.4.1五项原失败继续保留。

三个新合成源及SHA/MediaStore URI在`r10-sources.json`，只写指定测试目录；本地和手机最终SHA再次核对相同。HTML/JS/EPUB副本保留供复现，不删除个人或未知条目。

### 16.2 自动化偏差与恢复

- 封面组结束后USB短暂断开，后续启动命令退出255；重连并确认解锁后，新12+3系统基线与断开前相同，重新启动UID日志。断开期间没有连续日志覆盖。
- 最终收尾首次返回书架时手机锁屏，guard在UI输入前停止；用户再次解锁后仅重试收尾，没有新增测试。
- DocumentProvider显式URI启动在连接正常时仍返回255，未取得完整stderr，未归为应用失败；改用仅对三份已知测试源扫描并精确查询的MediaStore URI后打开成功。
- HTML脚本页与LOG正文可见，但这次UI树未暴露子frame正文；初版正文Text断言停止后核对截图，改用可见标记与日志/原生标题合证，没有误记成安全失败。JS按钮实际为“▶ 运行”，初版精确标签错误在点按前停止，修正后继续。

- 两段RC UID日志1670与3579行均无FATAL/ANR匹配，退出无reason4/5/6；`r10-crash-anr-final-check.json`。两个logger按实际命令核对，已退出者保留记录，仍运行者已停止；临时UI XML删除。
- 系统12+3项与重连基线完全一致；Standard连续模式/原列表恢复，阅读排版和主题未改、TalkBack未启动。两RC均回书架F4、Strict前台、任务fullscreen；Files仍原列表，无活动选择，没有新书签/收藏/分组。
- FIX3提交、六个继承源码及原tracked diff SHA不变，四交付APK及手机两RC哈希匹配；正式/Preview/QA版本和安装/更新时间不变。未改代码、构建、安装、清数据、卸载、提交、推送或发布。

### 16.3 r10结束时剩余记录（已被第17节范围冻结覆盖）

已确认失败共六项，均未修。Standard封面完整松手回稳仍缺足够时序证据；F1其余格式组合、尚无对应补证的单版本/细分路径仍按历史范围保留。release私有索引重建/空闲定时器及v08/v17历史引用仍有验证限制。脚本跳转默认阻断、Standard JS超时、两RC LOG原生路径、Standard TXT末章以及Strict所测查找/全书搜索已补，不能继续列作这些路径的待测项。TalkBack取消与三个用户确认项不变。完整1.0.0验收仍未通过，正式两个APK仍未签名，未进入发布/清理步骤。

## 17. 用户最终改判与范围冻结（2026-10-23，当前最高优先级）

用户明确要求：“停止扩大测试范围。”剩余未测项不再补测。以下决定覆盖历史失败/待测判定，历史截图、日志和观测事实保留；本次没有新设备测试。

| 用例 | 当前判定 | 用户确认的理由或后续 |
|---|---|---|
| 4.5.3 连续模式目录跳转 | **已知问题，不阻塞1.0** | 最后一章很短且前面有大图；按用户决定接受此情况，不再作为发布阻塞。 |
| 9.4 进程回收后返回书架 | **通过，设计如此** | 用户明确改判；不再要求启动后直接恢复阅读页，不再作为失败或待修项。 |
| 3.3 EPUB原文件名搜索 | **通过，设计如此** | 用户明确改判；原文件名与元数据题名不同时的现象不再记失败，不扩展到其他已通过的搜索路径。 |
| 4.4.1 主题切换短暂不同步 | **视觉细节，不阻塞发布** | 用户接受该视觉细节；历史帧时序保留，不继续量化或修补。 |
| 4.6.2 脚注被顶栏遮挡 | **待FIX4修复后重测** | FIX4到达后，仅在两个RC验证该修复。 |
| 5.9 代码复制丢空行 | **待FIX4修复后重测** | FIX4到达后，仅在两个RC验证该修复。 |

范围冻结同时取消Standard封面完整回稳补证、F1剩余格式组合、Strict单版本覆盖、索引/定时器及历史参考核对等追加测试任务。这些项目保留“未测/部分验证/工具受限”的事实，不虚记通过，但按用户决定不再补测、不再作为本轮发布阻塞。TalkBack已取消；设置页外链、旧版升级、Standard首次空书架的用户确认继续有效。

后续仅执行以下顺序：

1. **现在等待FIX4**，不自行改代码，不构建/安装新包，不发布。补丁到达后的应用与构建按届时提供的FIX4及指示执行，不自行加入其他修复。
2. 在FIX4的两个RC上**仅重测4.6.2、5.9**；其他既有通过项沿用，不追加回归矩阵。
3. 在Strict RC、Standard RC上**各做5分钟日常操作试用**。这是普通使用观察，不拆成新的全面测试计划或扩大覆盖范围。
4. 指定两项重测通过，且日常试用没有新失败，即按用户指示进入原方案第12节发布步骤；4.5.3已知问题、4.4.1视觉细节及已关闭未测项不再阻挡。实际试用若有新失败，记录后按原约定报告，不自行修复或扩测。

当前尚未收到FIX4，未进行其重测或日常试用，发布条件尚未满足。本次只修改报告、交接及本地计划/进度；应用源码、提交、APK和手机均未改动，未发布。
