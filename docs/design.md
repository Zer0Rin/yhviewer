# YHViewer 视觉设计

## 一套颜色，从书架到正文

外壳（书架、面板、对话框、编辑器顶栏）和阅读页用**同一套色板**，由阅读主题决定：

| 主题 | 底色 | 表面 | 正文 | 强调 |
|---|---|---|---|---|
| 纸张 | `#faf9f5` | `#f0eee6` | `#2b2a27` | `#b5532f` |
| 墨色 | `#262624` | `#30302d` | `#e8e5dc` | `#d97757` |
| 终端 | `#1d2a32` | `#172229` | `#d3dae0` | `#e9a23b` |
| 跟随系统 | 浅色用纸张，深色用墨色 | | | |

色值和 `app/src/main/assets/shared/themes.css` 一致，原生代码在 `ReaderPalette`。
v0.4 的外壳曾用一套独立的暖纸色（`#eee8dc`），从书架进入 Markdown 时顶栏偏黄、正文偏白，v0.5 合并成一套。
`V8VisualTest.palettesAreReadable` 检查每个主题的正文、次要文字、强调色对比度（WCAG 4.5:1 / 7:1）。

## 参考的三套设计系统

参考了 [ruiqichenbiec/design-systems](https://github.com/ruiqichenbiec/design-systems) 里的三套系统。三套都是网页 / WebGL 展示，不能照搬到原生阅读器，所以从每套里挑和"读东西"相关的元素：

| 来源 | 原系统里的做法 | 在 YHViewer 里 |
|---|---|---|
| **Overture**（舞台、纸张） | 细线分隔；直角"印刷品"带轻微投影；大写字距小标签 | 列表分隔线；直角封面 + 投影；"继续阅读 / 书籍 · 4 本"小标签 |
| **Lattice**（点阵） | 一切由点构成；输入框下是点线，聚焦时点变色流动；进度由点结晶 | 搜索框和目录筛选框下是一排点，聚焦时点变成强调色并收紧；阅读进度是一排点，读过的点亮起 |
| **Lens**（玻璃、液体） | 可交互部分由同一种"透镜"承载；按住轻微下沉，松手回弹；不做持续晃动 | 多选操作栏和章节栏是浮在内容上的胶囊；封面和主按钮按下缩到 0.97 再弹回 |

没有采用的：Lattice 的 GPU 粒子场和点阵字体、Lens 的实时折射。前者在列表里是噪声，后者需要读取下层像素，而正文是 WebView，Compose 拿不到它的像素做模糊。胶囊用 94% 不透明的表面色代替玻璃。

## 规则

- **内容直角，控件圆润**：封面、列表行、类型方框是直角；按钮、筛选片、胶囊栏是全圆角；对话框和底部面板 16dp。
- **强调色只标记动作、选中、进度**；冷色只用于焦点（输入光标）和信息。
- **字体**：书名和章节名用系统衬线（Noto Serif / Noto Serif CJK），和正文阅读字体一致；界面文字用 Manrope；文件名用界面字体，因为它不是书名。v0.4 用过 Bodoni Moda，它的西文细线在 16sp 下太细，"1" 和 "l" 难以区分，和中文混排也不协调，已移除。
- **间距**：4dp 网格（4 / 8 / 12 / 16 / 24 / 32）。
- **系统栏**：状态栏和导航栏全透明，图标深浅跟随色板；三键导航不加白色遮罩。

## Contrast verification

Native secondary labels meet 4.5:1 against both page and raised surface backgrounds. Paper uses `#6e6c65` for muted/subtle text and `#ac4d29` for link/action text; Ink action text uses its existing CSS link color `#e08a6c`. Paper CSS muted/quote/link tokens match the native palette. Decorative dots retain the softer border token. Grid progress exposes an accessible numeric range.
