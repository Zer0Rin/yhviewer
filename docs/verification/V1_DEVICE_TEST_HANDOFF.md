# 1.0 真机验收接续交接

## 发布准备（2026-10-22，America/Los_Angeles）

用户已授权提交FIX1/FIX2、验证干净源码和RC字节一致后推送release/1.0.0并创建到main的PR。CI全部通过后必须停止，等待用户确认合并；不得提前合并、打标签或发布。FIX1为26902d9，FIX2为a3f122d，应用源码与FIX4真机验收哈希完全相同。正式签名仅使用GitHub Secrets或环境变量。发布后再核验证书、版本、Strict权限与Python资源；RC和测试资料保留，清理另等用户确认。


## FIX4收尾（2026-10-23，当前最高优先级）

- 用户最新收尾要求是通过后停止汇报，等用户决定发布；覆盖此前“无新失败进入发布步骤”。**现在已停，不推送、不打标签、不发布。** 其他已关闭项目不恢复补测。
- 新提交`868a598cd4ec15bd81e7a356b7dfbcabaee3e6bc`仅FIX4三个文件，原六继承改动保留，真实构建来源见`build/device-1.0/fix4/source-state.json`及前后归档。四debug变体各268/共1072单测0失败，flow脚本通过，四Release APK构建成功。
- 四个APK规范路径和SHA见`fix4/four-apks.json`及`SHA256SUMS.txt`，报告最前表已列全。两个RC原测试证书签名并覆盖安装、安装SHA一致；两个正式变体仍未签名，未安装，正式/Preview/QA不动。
- 两RC4.6.2通过：连续F4返回脚注后，在显示顶栏时完整可见。两RC5.9的range与all均通过：实际原生粘贴画面保留空行、不含代码行号，全选带Kotlin · 3行。UI树提取文本连续换行有损，不能据此误判；画面和UI树均留档。Scratch编辑全部不保存。
- Strict日常试用303秒、Standard304秒，无新失败。包含开书/文档、滚动翻页、目录、主题切换恢复和各原生导入R11-daily-note.md。没有录屏或逐帧分析，没有扩测。
- 四项改判和其余未测不补保持有效。原主题/连续模式/视图恢复，两RC书架F4，Strict前台/fullscreen；Files原列表无活动选择，无新书签/分组/收藏，个人和未知条目未动。日志20277经命令校验停止，临时XML删除；系统12+3与源码/安装包/保护包审计通过，`r11-crash-anr-final-check.json`。
- 当前没有待执行验收步骤；等待用户决定发布。历史条目和第17节旧流程不得作为自动继续发布的授权。

---


最新：2026-10-23（Asia/Taipei），用户已冻结测试范围并改判，当前等待FIX4。现在不改代码、不发布、不操作手机。以以下“范围冻结与FIX4后续”为最高优先级，后文r10及更早判定均为历史；不得据旧待办恢复补测。

## 范围冻结与FIX4后续（当前最高优先级）

- 停止扩大范围，所有剩余未测项不再补测，包括封面完整回稳、F1格式、Strict额外覆盖、release索引/定时器和历史参考。保留未验证事实，不虚记通过，不再作为本轮发布阻塞。
- 4.5.3改为已知问题、不阻塞1.0：最后一章短且前面有大图。9.4和3.3（仅EPUB原文件名搜索现象）按用户确认改为通过，属于设计如此。4.4.1改为不阻塞发布的视觉细节。不得再把这四项算作待修阻塞。
- 当前仅4.6.2脚注遮挡、5.9复制丢空行等待FIX4。FIX4到达前不改代码、不构建/安装、不发布；补丁应用与构建按届时提供内容和指示，不自行增加修复。
- FIX4后仅在两个RC重测4.6.2、5.9，然后Strict RC与Standard RC各做5分钟日常试用；不重复其他通过项、不新增测试矩阵。上述重测通过且没有新失败，就按用户指示进入原方案第12节发布步骤。若试用发现新失败，记录并报告，不自行修或扩测。
- TalkBack取消、设置页外链/旧版升级/Standard首次空书架用户确认通过的决定保持有效。正式/Preview/QA和个人数据保护边界不变。
- 本次只更新文档和计划；手机测试/日志此前已停，FIX4尚未收到，发布条件尚未满足。完整决定见报告第17节。

## r10历史交接（判定已由范围冻结覆盖）

- 沿用FIX3提交48cd991c与同一两RC，无新实现修改、构建、安装、提交或发布。六源码/原diff/四APK/手机两RC哈希和保护三包均核对不变。
- 新增第六项失败3.3：R10-filename-alias.epub元数据题名R10 distinct title，两RC搜distinct找到、搜R10-filename-alias无结果。`r10-epub-alias-search-result.json`及四组搜索截图/XML；新源SHA52fb52fa…，不修代码。
- 新通过路径：Standard两章TXT末章右点无回绕（2/2、正文MAD0、连续模式恢复）；两RC默认权限无手势location.href跳转拦截（各自PID日志gesture=false、截图R10_STILL_IN_DOCUMENT）；Standard JS死循环5007ms强制超时；两RC原生Files打开带空格/全角括号LOG、纯文本100行；Strict所测页内查找↑↓计数、全书第三章第三处marker-0139（3/4且计数3）。已补路径不要重跑。
- Standard封面1x/5x320ms新片已审：缩小及退场前回稳可见，但可见P0首帧已离开书架，完整松手回稳仍部分验证。`r10-cover-observations.json`与两个源视频/逐帧图保留，不强判通过。
- 三份新R10 HTML/JS/EPUB仅在已知测试目录，`r10-sources.json`及`r10-fixture-hashes-final.json`保存SHA/URI，手机和本地一致。使用MediaStore打开；直接DocumentProvider URI曾退出255，不重复尝试。HTML/LOG子frame正文这次未出现在UI树，以截图+日志/原生标题合证，勿把自动化断言当应用失败。
- USB中断后重连基线12+3与先前一致，最终与重连基线逐一相同。`r10-crash-anr-final-check.json`无FATAL/ANR、退出无4/5/6；两段日志按实际进程状态处理并停止，临时XML删除。中断期间没有连续日志覆盖。
- 最终两RC书架F4，Strict前台、fullscreen；Standard原列表、Strict封面，Files原列表无选择。连续模式恢复，无新书签/收藏/分组，主题/排版未改，未启动TalkBack；正式/Preview/QA/个人未知内容未操作。
- 其余：六项失败未修、Standard封面完整时序、F1其余格式/历史单版本细分路径、release索引/定时器和历史数据验证限制。TalkBack已取消；设置页外链、旧版升级、Standard首次空书架用户确认通过，不再作为阻塞。正式两APK未签名，完整验收未通过，未进入发布或清理。

## 用户范围调整与确认（持续有效）

- TalkBack（9.5）已由用户明确取消，从本轮验收范围移除，不再作为未完成或阻塞，也不再要求用户听读。不修改现有无障碍实现。
- 设置页被AppLock挡住的外链（3.8）、旧版本覆盖升级、Standard首次空书架（2.1）均为“用户确认通过”，来源为2026-10-23本对话，不冒称新增自动化证据，不重复验证、不清数据重造首次状态。
- 设置页外链确认仅覆盖3.8。5.5脚本导航默认阻断已由r10补测通过；Standard TXT末章已通过，EPUB源文件名搜索已转为第六项失败。当前剩余按上方r10交接和报告第16.3节，release索引/空闲定时器与历史参考验证限制仍保留。
- 此前范围确认仅改文档；r10后累计六项失败未修。后续不要依照历史段落重新要求TalkBack或把上述三个用户确认项列为阻塞。

## FIX3接续基线（当前）

- 授权范围：应用用户提供FIX3，完整单测后重建四APK，仅覆盖`.rc.strict`/`.rc.standard`，只用新包重测5.6/5.7；其他已通过用例沿用旧结果。此授权不包含修复其他五项失败或发布。
- 新提交`48cd991c30884456dcb1349864fc7c80a089b84c`只含FIX3三个文件；实际构建仍叠加原六个继承改动。`build/device-1.0/fix3/source-state.json`、`inherited-working-tree.patch`及`inherited-untracked/`保留可复现源码状态，禁止重置这些改动。
- TDD先红后绿；`:app:test`四debug变体各266项、共1064项0失败/错误/跳过，随后四个R8 Release变体构建成功。四个新APK完整SHA和规范路径在`build/device-1.0/fix3/four-apks.json`与`SHA256SUMS.txt`，报告第14节逐一列出。正式Strict/Standard因本地无正式私钥而未签名，不能当已签名发布包；两个RC沿用测试证书2677ad64…，覆盖安装成功，结束安装SHA再核对一致。
- Strict RC SHA `d47d93a724b3d571edbc1449857f51191be1e33561d008da6048b182ccbf7fcc`；Standard RC SHA `882952d20c12d083dc01aad1cd2b691f3fd5c83400352b69d2b67344aa765554`。旧fix2-test的57e0…/e01c…已不是手机当前RC。
- 两RC5.6、5.7通过所测新包路径：F9表格/高亮/脚注/原始HTML文本正常；三滑杆拖动时实时预览、重开保存；无衬线和MD排版保留，书籍100/1.9/20不受影响，书籍字号改130也不改变MD160/2.3/36。主题保存/重开及全局共享另验，未推翻既有主题不同步失败。最终MD恢复100%/1.8/20、跟随主题字体；书籍恢复100%/1.9/20，原连续模式不变；Strict跟随系统、Standard纸张。
- 证据目录仍`build/device-1.0/continuation-20261022/`。Strict主组10月22日完成，Standard中断旧文件在`r9-standard-interrupted-20261022/`，10月23日重新连接后Standard整组补完；两RC主题补测随后完成。主证据`r9-*-md-*`、`r9-*-F9-render-*`、`r9-persistence-final-audit.json`、`r9-md-visual-observations.json`。不重跑已通过其他用例，不清数据伪造首次基线。
- 收尾`r9-crash-anr-final-check.json`：两段UID日志无FATAL/ANR、退出无4/5/6，12+3系统设置与重新连接基线一致；源码/四APK/两已装RC/三个保护包复核通过。旧logger已退出、续测logger核对后停止，临时XML删除。隔夜断开期间没有连续日志覆盖。
- 现场：两RC书架F4、Strict前台、fullscreen，Strict封面/Standard列表；本轮不使用Files，原列表无新选择。无新书签/收藏/分组，测试源和已知RC副本保留，个人/未知条目未操作。手机可拿走。未推送、发布、卸载或清数据。
- 当前授权任务没有待执行步骤。后续用户如要求继续普通验收，先重新记录当时系统基线/确认连接解锁，再按报告中的历史缺口继续；五项失败仅记录，没有新的逐项补丁授权不得自行修改。不要重新启动TalkBack听读。

## 工作目录与保护范围

- 工作树：`/Users/guyue/.codex/worktrees/v1-device-acceptance/YHViewer`，分支`release/1.0.0`，当前FIX3提交`48cd991c30884456dcb1349864fc7c80a089b84c`；原始基线`1e9d294b975a08e3e6e1bf93a06a4b185ab30628`。
- 方案：`/Users/guyue/Downloads/DEVICE-TEST-PLAN.md`；汇总：`docs/verification/V1_DEVICE_ACCEPTANCE.md`。
- 设备`55bff995`：Redmi Note12Turbo，Android15/API35，1080×2400/440dpi。
- adb：`/Users/guyue/Library/Android/sdk/platform-tools/adb`。
- 只写Strict RC `io.github.zer0rin.yhviewer.rc.strict`（UID10394）和Standard RC `io.github.zer0rin.yhviewer.rc.standard`（UID10393）。正式主包、Preview、QA与个人文件受保护，不启动、导入、删除或清数据。
- 手机测试目录：`/sdcard/Download/YHViewer-1.0-check-2026-10-21/`，日期沿用原目录。只操作已知测试素材；Standard有一个未知书架条目，不查看/截图/修改，不清数据以伪造首次基线。
- 原方案0.5：发现失败记录，不自行修复；唯一追加例外是用户明确授权并已提交的FIX3三个文件。安全失败立即停止；其余改动不自行提交，不推送/发布/卸载RC或清理复现资料。

## 历史六项观测（当前判定以范围冻结为准）

| 用例 | 两RC现象 | 证据 |
|---|---|---|
| 3.3（r10新增） | EPUB题名与原文件名不同，按题名可搜到、按原文件名搜不到 | `r10-epub-alias-search-result.json`及`r10-*-alias-search-*` |
| 4.5.3 | F4连续模式目录主动选Chapter3落到第二章大图；单章模式正常 | 10-21目录`strict-continuous-toc-*`、`standard-continuous-toc-ch3-result.*` |
| 4.6.2 | 连续模式“返回脚注”后第一章脚注被原生顶栏遮挡，隐藏工具栏才可见 | 10-21目录`*-continuous-footnote-bars-hidden.*`及脚注结果 |
| 9.4 | F10 C++阅读中Home→am kill确认PID消失；MAIN/LAUNCHER返回书架，没有直接回阅读页；手动重开位置保留 | 10-21目录`r2-*-kill-result.json`、对应before/resume/reopen图 |
| 5.9（r4新增） | 代码内跨空行选区原生复制丢中间空行；无行号要求通过 | 10-22目录`r4-*-code-range-{selected,pasted}.png`、`-copied-pasted.txt`、`-check.json` |
| 4.4.1（r5新增） | 四主题切换时Aa面板已变色、顶栏短暂保持旧主题，两模式均复现，采样差约8–42ms | 10-22目录`r5-{strict,standard}-{continuous,single}-theme-switch.mp4`、`-lag.json`、`-lag-frames.jpg` |

代码新素材`R4-copy-lines.kt`为`val alpha = "BEGIN"`、空行、`val beta = "END"`。从alpha选到第三行END末尾，实际粘贴只有一个换行，预期两个。安装APK代码模板SHA与工作树一致且有copy listener，不能据此推断原因。Scratch会话均不保存。自然滚动下一章标题尚在顶栏下方时维持上一章，用户已确认符合预期，不要重新判失败。

## 最新r7完成与现场

- 新12+3系统基线、两安装APK哈希和六源码SHA已核验。`r7-crash-anr-final-check.json`：5383行两RC UID日志无FATAL/ANR、退出无reason4/5/6，12+3项与六源码哈希相同；logger55682核对命令后已停、临时XML删除。
- 新增R7-kind前缀8个小素材（HTML/MD/KT/CSV/两章TXT/LOG/无脚本SVG/F4仅改题名EPUB），手机源SHA已核验，`r7-kind-sources.json`。两RC原生各批导入8项，精确R7-kind筛选，8类checked和数量1、唯一对应条目全部通过；清除类型选择回8项。`r7-*-kind-count-audit.json`与0..7截图/XML是完整证据。Strict初版视觉文件名匹配和横滑坐标错误，核对标识/实际Y后分段恢复，无重复导入；末次kind-results只写最后3项，勿误当仅测3类。
- 两RC F3连续模式，行距1.9→2.3、边距20→36各拖7秒，约1.8/3.6秒仍按住时正文实时变化且仅留浮动滑杆。松手后及退出书架重开保留新值；无衬线字体重开保持选中。`r7-*-epub-{line,margin}-*`和`-font-sans-persisted`。已恢复字号100/行距1.9/边距20、原跟随主题字体及原主题、连续ON；基线见`r7-*-epub-baseline.json`，最终见prefs-restored。
- 两RC F3单章目录1500，章尾点右侧后仍1500/1500、下一章禁用，正文MAD0，`r7-*-last-chapter-result.json`。短章全可见，TXT末章未补。
- 最终Strict前台，两RC书架F4、任务fullscreen，Strict封面/Standard列表、Files实际列表无选择；无新增收藏/分组/书签、不删旧书。源8份与RC副本保留，个人/未知条目、正式/Preview/QA未操作，未提交/推送/发布。
- 现在优先剩余：3.8 AppLock外链；5.7 Markdown两RC实时预览与书设置独立保存的完整补证；Standard封面松手完整时序、TXT末章边界及历史无后续证据的单版本覆盖。既有失败、不可测历史基线、TalkBack实际语音/手势继续保留，不清数据/不主动要求听读。不要重复r7已补项。

## 最新r6完成与现场

- 新基线`r6-system-baseline.json`/`r6-extra-baseline.json`，两个APK哈希与FIX2一致，六源码哈希已保存；结束12+3项逐一相同。当前Files实际为列表，r5称网格与本次实测不同，按r6列表基线保留，不切旧网格。
- Standard新R5-import-F2原生单选导入已完成：可见UP2.147489→正文3.808833，约1661ms；原始VFR源视频/帧指标/`r6-standard-F2-native-import-keyframes.jpg`已审。包含选择器/导入/渲染，单样本，不是热重开或纯解析；该待测项已补，不重新测首次。
- 两RC作者Device、标题F4、文件名F9-content、无结果词通过，证据`r6-*-shelf-search-*`和`r6-shelf-results.json`。最初错误输入DeviceTest无结果，源OPF实际作者Device Test含空格；校正后通过，不判bug。所有截图前已知元数据白名单，未知条目未查看/操作。EPUB原源文件名搜索另未作结论。
- Strict书架中段开书后直接点‹书架返回，封面bounds相同，(0,650,1080,2200)区域MAD0。素材同图同名，只证明视口位置，不证明最近阅读重排后条目身份。
- Strict F3常亮：超时临时30秒，无输入静置30/60/90/120秒均Awake；关闭开关并恢复300秒。`r6-strict-stay-awake-results.json`。Strict F2连续模式30次侧边翻页，365渲染帧，现代Jank13/3.56%，legacy0，p50/90/95/99=7/9/10/13ms；没有新逐行录像证明无重复/漏行，行为证据用r4。
- `r6-crash-anr-final-check.json`：4080 UID日志行无FATAL/ANR，退出原因无4/5/6。logger52508命令校验后停止，临时XML删除；系统12+3项与六源文件SHA不变。两RC书架F4，Strict前台，fullscreen；书架Strict封面/Standard列表，Files列表无选择。阅读偏好未改，临时常亮关闭，无新增收藏/分组/书签。
- 只新导入Standard RC已知R5-import-F2副本；没有新建/删除源文件或操作正式/Preview/QA/个人文件。没有提交/推送/发布。剩余：3.4其余类型、3.8 AppLock阻挡外链、部分Aa细项、Standard封面松手完整时序及不可测历史/无障碍细项。不要重复r6已补部分。

## 最新r5完成与暂停

- 两RC原生批导入新URI同字节F2/F3各两项，未自动打开；首次从书架打开及立即重开，Strict F2约295/234ms、F3约425/218ms，Standard F2约249/233ms、F3约414/231ms。`r5-open-time-observations.json`及八个源视频/逐帧图。计时是可见pointer P:1→0至首个可读正文帧、VFR实际PTS；单对样本，不代表首次安装/无索引/内核事件耗时。
- 两RC纸张/墨色连续TXT模糊背景和单章F4较实底色均对照通过，查找栏不透明。未遮挡正文显/隐栏MAD0。`r5-*-{continuous-glass,single-epub}-{paper,ink}-comparison.png`。
- 两RC F3字号100→160拖7秒，约1.8/3.6秒仍按住时连续正文随110/130%变化，单章正文两样本与100%区域MAD0，松手后才变大，符合预期。字号/行距/边距100/1.9/20已恢复。四主题录像确认新失败4.4.1；Aa遮住大部分正文，未声称隐藏正文的同帧切色。
- 两RC精确F4筛选11本：新9份同字节F4副本、源位图为纯米色；十秒33/32次滑动，1246/654源帧未发现封面内文字。Strict1242渲染帧/现代Jank0.32%/p90/95/99=5/6/9ms；Standard653帧/0%/7/7/8ms。检测器限米色内部暗字，概览另已审；不作所有占位颜色的普遍证明。
- 320ms封面短按均小幅缩小，Strict松手回稳再切页可见，Standard切页较快、回稳覆盖有限。5倍长按多选栏两RC滑上/淡入可见，取消后退出；原封面/列表及动画恢复。未增删收藏/分组/书签。
- 用户要求暂停时，队列的Strict新F2原生单文件导入计时已完成：P:0 PTS2.199233→正文3.283811，约1085ms，包括选择器退场/导入/渲染。Standard仍准备选择器，uiautomator137后停止，未点文件。尝试取消时原Strict子进程已结束，PID校验拒绝杀进程；恢复选择器最初期待Strict实际回Standard，guard停止。全部队列已结束，无后续手机测试。详见`r5-pause-requested.txt`、automation-errors。
- `r5-system-baseline.json`/`r5-system-final.json`12项、`r5-extra-baseline.json`/`r5-extra-final.json`3项完全相同；字体1.0、440无override、浅色、旋转0/0、动画1/1/.5、TalkBack空/0、超时300000ms、pointer缺省、show_touches0。主题Strict跟随系统/Standard纸张，连续ON、100/1.9/20；Strict封面/Standard列表，Files网格无选择，F4筛选、Strict前台，所有RC任务fullscreen。
- `r5-crash-anr-final-check.json`：9025 UID日志行无FATAL/ANR，退出原因无4/5/6；logger37597命令验证后停止，临时UI XML删除。任务校验初版误纳入无mode的简写引用，改完整Task行后通过。`r5-source-baseline.json`/`r5-source-final.json`六个应用文件SHA相同。没有代码修改/重建/提交/推送/发布/卸载。手机可断开。
- 新源`R5-perf-F2.txt`、`R5-perf-F3.epub`、`R5-cover-01..09.epub`、`R5-import-F2.txt`及副本/录像保留在指定测试目录；Standard的R5-import-F2尚未导入。不要为重新测历史首次清空书架。

## 最新r4完成范围

- 两RC原生选择器10个指定文件，录像捕获“正在导入6/10”“7/10”及F2名，完成10项；Files恢复原网格。
- F2和新URI同字节`R4-first-F3-1500.epub`首次导入：加载期间转圈/返回，完整阅读顶栏加载后才出现；小F9实录无转圈。F3副本SHA`4ff96dd06a3d449fe564e0ee6b2d2a09851f6a8d9933061f2db8b24dbd7efa70`，URI1000182975；不代表历史安装首次基线，未测精确端到端首次耗时。
- 两RC五类阅读器长按复制覆盖：TXT“文”、MD“Markdown”、HTML“English”、EPUB“点”/“END_OF_F4”，实际粘贴到临时编辑器核对；代码整页复制r2通过无行号，r4范围空行失败。
- 两RC EPUB/MD链接按下变淡、无灰蓝色块，5倍录屏后恢复动画1/1/0.5。RGB对比度投影约.59–.61是编码帧诊断，不是内部alpha。
- 两RC墨色单章新F3连续五次到第6章、目录第10章、书签回第1章、MD/代码打开；Strict750帧、Standard436帧逐帧无宽幅白背景，最亮帧为正常白字。F7原样HTML在墨色原生栏下保持白网页，符合预期。主题/连续模式恢复，新F3临时书签删除。
- 两RC单次侧边约0.9屏，三连额外约3×2060px、左翻回约1×2060px；5倍翻页中拖动可反向跟手并打断旧目标。可见pointer P:1→0后Strict同帧起动、Standard约15.7ms起动，源帧间隔约8–18ms；这是显示状态/编码帧观察，不是内核输入时间。详见`r4-page-turn-observations.json`。
- 两RC原生MIUI分屏：上下各C++，均可滚动/开Aa。退出拖动只变比例；最终只force-stop两个RC重开，全部任务恢复fullscreen。
- Strict单页C++3%处新增书签，三次上滑离开后点击跳回；正文和添加前逐像素一致MAD0，远处对照MAD9.028。删除本轮书签至0，补足5.16跨位置；Standard首轮已测。
- TalkBack Strict焦点/Enter激活设置与Aa通过，目录轨道可获绿色焦点。用户原愿协助听读，随后询问无障碍用途；已说明原方案9.5并关闭，未收到名称或物理拖动确认。不把UI标签或焦点代替语音通过；Standard未做本轮语音测试，不主动重新要求听读。

r2/r3已有完成：两RC原生单/三文件分享、原生打开方式；F1独立根34文档/2资源；Standard重导入保留收藏/分组/位置；HTML字号保留；另存为加入书架且原源SHA一致；TXT三滑杆实时调整并保存；F3 Aa稳定及底部开关；小窗；5倍页面淡入。主报告保留对应细节。JavaScript单文件原生Share不提供RC，manifest SEND未声明该类型，Markdown单文件和三文件路径通过不代表所有类型支持。

## r4历史现场与恢复核查（以r5新记录为准）

- 最新证据：`build/device-1.0/continuation-20261022/`；上轮`continuation-20261021/`，更早`fix2-test/`。
- 两已装APK未变：Strict SHA`57e0395e3d7970b78156b98dc4747d9088f4e42838f52cdadde35862e00d0dc3`，Standard SHA`e01c844f1fe098096ec5bd60b0f40299edec584f6d497a95ab0a65c52035e215`。
- `r4-system-baseline.json`与`r4-system-final.json`12项一致：字体1.0，密度440/无override且secure密度键缺省，浅色，自动旋转0/user_rotation0，动画1/1/.5，无障碍服务空/0，1080×2400。额外超时300000ms一致；pointer_location原键缺省、show_touches0恢复。下次必须重新保存当时基线，不覆盖用户新设置。
- 两RC书架精确F4筛选，Strict前台，所有任务fullscreen；Files选择清空且网格；未修改默认应用。主题Strict跟随系统/Standard纸张，连续ON，TXT100%/1.9/20、代码100%/不换行、HTML100%。Scratch全部不保存，本轮临时书签已清。
- `r4-crash-anr-final-check.json`：8558行UID日志无FATAL/ANR，两个RC退出原因无4/5/6；按`\breason=(\d+)`解析，避免误读subreason。日志进程15318命令校验后已停止；临时`/sdcard/yh-acceptance-window.xml`删除。
- 新素材`R4-first-F3-1500.epub`、`R4-Scratch.kt`、`R4-copy-lines.kt`、`R4-links-F4.epub`、`R4-timing-unique.txt`及录像都在已知测试目录中保留；不卸载RC/清理证据。

## r5历史剩余范围（以r6已补结果为准）

1. 先确认设备已连接并解锁，保存当时新基线，启动新的两RC UID日志；不恢复成旧用户设置，不绕过锁屏。当前所有队列/日志已停，不自动重跑。
2. Standard新F2单文件导入计时未完成，文件`R5-import-F2.txt`已在已知fixtures内；首次/重开书架计时和两RC毛玻璃、字号、封面组已补，不重复。Standard封面松手回稳源帧仍有限。
3. 3.3作者搜索、3.4其余类型筛选、3.6 Strict中段书架返回、3.8系统AppLock阻挡外链及部分Aa细项/Strict常亮、Strict30翻页统计未补；历史表与后面续测须合读。9.4 C++路径已失败，其他阅读器未逐一复测。
4. 历史首次空书架证据不可补，不清Standard未知条目。release私有索引重建/空闲定时器不可测，v08/v17历史引用本轮未核验。TalkBack实际语音/手势仍未验证，用户关注普通阅读，不主动要求听读。
5. 五项失败仅记录，禁止进入发布步骤12；未经明确授权不修代码。保持个人数据保护范围。

## 工具与防误操作

- `device.py`用于精确搜索、URI及guard。全屏guard读取第一个topResumed；分屏可能两个topResumed，必须改用`dumpsys input`的FocusedApplications核实焦点，且检查Keyguard。UIAutomator只返回当前活动窗口，不能据缺第二个包判分屏失败。
- 原生Copy/Paste浮动菜单常不在UI树中；按截图确定位置。空编辑区先单击聚焦再长按，避免误点Done；EPUB选中字符必须以截图核实，不以预设字符判失败。
- uiautomator退出137只重试一次，记录automation-errors。开启TalkBack期间不用uiautomator，避免抑制服务。
- screenrecord必须后台并发输入，显式display-id4630946370515662722，540×1200/8Mbps；按源帧PTS解码，不按平均fps或主机命令时间推断输入时序。FFmpeg在`build/device-1.0/video-tools/imageio_ffmpeg/binaries/ffmpeg-macos-aarch64-v7.1`。
- Python：`/Users/guyue/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3`。
- FilesActivity可直接打开已知fixtures目录：data `content://com.android.externalstorage.documents/document/primary%3ADownload%2FYHViewer-1.0-check-2026-10-21%2Fstaging%2Ffixtures`，type `vnd.android.document/directory`。退出选择器时多次Back可能上溯目录，只核实前台，不检查/截图个人父目录。
- 原有未提交应用改动保留：flow.html、CharsetDetector.kt、ReaderScreen.kt、CharsetMemoryRegressionTest.kt，以及新增TextScore.kt、V1EncodingTest.kt；不重置。验收报告/本交接未提交。
