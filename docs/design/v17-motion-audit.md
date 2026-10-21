# v0.10 动效与交互审计

参照 [emilkowalski/skills](https://github.com/emilkowalski/skills) 的动效标准，包括：

- `emil-design-eng`
- `improve-animations` / `review-animations`
- `animation-performance`
- `mobile-native`
- `apple-design`

审计对象是 main `e9120e4`。每一条都在代码里核对过位置。全应用统一的动效参数见 `Motion.kt`。

## 改了什么

| # | 改之前 | 改之后 | 为什么 |
| --- | --- | --- | --- |
| 1 | 翻页用 `onSingleTapConfirmed` | `onSingleTapUp`，并关闭双击识别 | 确认单击要等约 300ms 排除双击，每次翻页都慢半拍；连点两下会被当成双击，一页都不翻 |
| 2 | 每次翻页都是 `ObjectAnimator(scrollY → scrollY + 一屏)` | `PageTurner`：记住目标并取消旧动画，新目标 = 旧目标 + 一屏；手指一拖动就停 | 动画进行中再点，旧写法从半路算距离，两次加起来不到两屏，会重复读几行；两个动画还会抢 |
| 3 | 翻页 160ms，默认先加速后减速 | 180ms，强 ease-out `cubic-bezier(0.23, 1, 0.32, 1)` | 对点按的响应要一开始就动 |
| 4 | WebView 没设背景色（白色） | 我们的模板用主题底色；原样 HTML 保持白底 | 墨色、终端主题下，每次新建 WebView 都会白闪一下 |
| 5 | 加载时显示单页文档界面（顶栏 + 转圈） | 只显示主题底色，300ms 后才淡入转圈和返回按钮 | 书加载完会突然换成全屏阅读页，布局跳变；加载很快时转圈一闪而过 |
| 6 | EPUB 每次打开都重新解析 OPF | `EpubCache`：按解压目录缓存最近 4 本 | 1415 章的书每次打开都要等 |
| 7 | 按压回弹 `spring(dampingRatio = 0.55)` | 按下 100ms、松手 160ms，强 ease-out，不回弹 | 松手时封面会抖；阅读工具的按压反馈要稳 |
| 8 | 6 处手写的 `tween(160)`，用 Material 默认曲线 | 统一用 `Motion.enter()`（160ms）和 `Motion.exit()`（120ms），强 ease-out | 统一参数；消失比出现快 |
| 9 | 多选栏 `slideInVertically { it }`（默认弹簧，没有淡入） | 220ms 抽屉曲线上滑 + 淡入；退出 160ms | 有方向、有淡入，退出更干脆 |
| 10 | 书架、阅读页、设置页瞬间切换 | 新页面 180ms 淡入，不位移；旧页面立即移除 | 避免跳变；一天几十次的操作，所以要轻，也不让 WebView 多活一段时间 |
| 11 | 点链接时 WebView 闪一块灰蓝色高亮 | `-webkit-tap-highlight-color: transparent`；`a:active` 时变淡 | 灰蓝色高亮是"这是网页"最明显的信号 |
| 12 | 网页模糊层显隐都是 `.16s` 线性 | 出现 160ms、消失 120ms，同一条 ease-out | 和原生的胶囊、顶栏同步 |
| 13 | `produceState(initialValue = null)` | 已缓存的封面直接作为初始值 | 封面滚出屏幕再滚回来时，先闪一帧文字封面 |
| 14 | `flow.html` 每 400ms 检查一次，从不停 | 连续 5 次（2 秒）没变化就停；滚动、加载、改排版时恢复 | 省电 |
| 15 | 每次重组都执行 `chapterTitles(doc)` | `remember(doc)` | 1415 章就是 1415 项，连续滚动每换一章都要重建 |
| 16 | 面板行在组合阶段读透明度 | 在 `graphicsLayer` 的 lambda 里读 | 淡入淡出时只重绘，不重组 |
| 17 | 返回书架时总是回到顶部 | 滚动位置保存在 ViewModel；换分组、类型或搜索词才回顶部 | 书多时不用每次都重新往下找 |

## 看过但不改

- **顶栏和底部胶囊的显隐**：点屏幕中间时出现，频率高。淡入淡出很短，而且网页里的模糊区域要跟着它一起淡入淡出。所以保留，只换成统一的参数。
- **下拉菜单、底部面板、侧边目录**：Material 自带的动效，已经从触发点展开。
- **"移除动画"无障碍开关**：Compose 动画、`ValueAnimator`、WebView 的 `prefers-reduced-motion` 都会跟随系统设置。
- **切主题时的明暗跳变**：Apple 建议深浅主题切换时缓和过渡。但我们刚把切主题改成"同一帧完成"（v14，用户的要求），这里不再加过渡。
