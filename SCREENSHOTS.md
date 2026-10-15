# 截图清单

正式名称：**YHViewer**；仓库名：**yhviewer**；许可证：**MIT**。

## 本次拍摄记录

2026-10-15，使用同一台 `Medium_Phone_API_37.0` 模拟器，1080×2400、420 dpi、浅色模式。状态栏使用 Demo Mode：09:30、电量 100%、隐藏通知。四张均为 `adb screencap` 原生 PNG，没有裁切、合成或修改页面结果；文件已低于 300 KB，无需再压缩。

| 截图 | 实际内容 | 大小 |
|---|---|---:|
| [shelf.png](docs/screenshots/shelf.png) | 6 个 HTML/MD 文档、2 个收藏、从刚打开到两天前的时间层次 | 139 KiB |
| [html.png](docs/screenshots/html.png) | 虚构周报：统计卡片、状态表格、本地 JS 按钮 | 189 KiB |
| [markdown.png](docs/screenshots/markdown.png) | 虚构技术笔记：标题、表格、Kotlin 高亮代码；长代码行可横向滚动 | 221 KiB |
| [sandbox.png](docs/screenshots/sandbox.png) | 原始沙箱测试页实际运行：全部 9 项通过，9 行 PASS 可见 | 204 KiB |

书架内容和最近打开时间是为展示临时布置的数据，周报和技术笔记均为虚构示例。拍摄使用正常的无联网权限 APK；临时加联网权限的验证证据见 [STEP3_VERIFICATION.md](STEP3_VERIFICATION.md)，不要将截图当成那一轮的证据。拍摄后恢复了原数据库、文档副本、显示设置和夜间模式，并核对书架记录与副本字节一致。可选截图和 GIF 尚未拍摄。

保存到 `docs/screenshots/`，文件名和 README 里的一致。

## 通用设置

- **设备**：API 37 模拟器，1080×2400。所有截图用同一台
- **系统状态栏**：开启 Demo Mode，让时间、电量统一干净：
  ```bash
  adb shell settings put global sysui_demo_allowed 1
  adb shell am broadcast -a com.android.systemui.demo -e command enter
  adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
  adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
  adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
  ```
- **截图命令**：`adb exec-out screencap -p > docs/screenshots/xxx.png`
- **测试数据要像真的**：用英文文件名、有意义的内容（比如让 AI 生成一份周报 HTML、一篇技术笔记 MD），别用 `a.md`、`test1.html`
- 每张控制在 300 KB 以内；需要压缩时优先无损处理，并保留原始截图

## 必拍 4 张（README 顶部表格）

| 文件名 | 内容 | 要点 |
|---|---|---|
| `shelf.png` | 书架 | 5–7 个文档，HTML/MD 混合，**至少 2 个带星标**；时间显示有"刚刚""2 小时前""昨天"的层次 |
| `html.png` | 打开一份 AI 生成的 HTML | 选有表格、配色、卡片的页面，体现"HTML 比 MD 信息密度高" |
| `markdown.png` | 打开 `render-test.md` 或真实技术笔记 | 画面里要同时有**标题、表格、代码高亮** |
| `sandbox.png` | `sandbox-test.html` 跑完 | 顶部显示"全部 9 项通过"，下方绿色 PASS 列表可见 |

## 加分 3 张（放在 README 对应章节）

| 文件名 | 内容 | 放在 |
|---|---|---|
| `dark.png` | 同一份 Markdown 的深色模式 | Features，可和 `markdown.png` 做左右对比 |
| `open-with.png` | 文件管理器"打开方式"列表里出现你的 App | Features 第一条 |
| `search.png` | 书架搜索 `report` 的结果，加上"只看收藏"筛选 | Features 的 Shelf 部分 |

## 可选：一个 GIF

8–10 秒的流程：文件管理器 → 打开方式 → 渲染完成 → 返回书架 → 点星标。
放在 README 截图表格上方，比静态图更能说明"从任何地方打开"。

```bash
adb shell screenrecord --size 720x1600 --time-limit 10 /sdcard/demo.mp4
adb pull /sdcard/demo.mp4
# 转 GIF（需要 ffmpeg）：
ffmpeg -i demo.mp4 -vf "fps=12,scale=360:-1:flags=lanczos" -loop 0 docs/screenshots/demo.gif
```

## 发布前检查

- [x] 正式名称统一为 `YHViewer`
- [x] 仓库名选定为 `yhviewer`，根目录已添加 MIT LICENSE
- [x] 本地四张图片存在，README 路径已核对
- [ ] 推到 GitHub 后确认图片在网页上显示
- [x] 测试表按实际 XML 报告核对：15 JVM + 25 设备，含 2 个模板 smoke tests
- [ ] Mermaid 图在 GitHub 网页上渲染正常
