# 发布手册

当前准备版本：`0.3.0`（versionCode `4`），正式 applicationId：`io.github.zer0rin.yhviewer`。

正式密钥和 GitHub Secrets 已配置，v0.2.0 签名 R8 包已完成 API 35 真机冒烟；v0.3.0 需要重新验收；记录见 [真机验收](docs/verification/RELEASE_DEVICE_ACCEPTANCE.md)。推送版本 tag 才会触发发布工作流。临时验证密钥不能用于正式发布。代码 namespace 保持 `com.example.yhviewer`。

## 一次性准备

### 创建并备份正式密钥

在仓库外生成密钥，交互输入密码，勿把密码写入命令或聊天。以下命令在 Android Studio 的 JDK 可用时运行：

```bash
mkdir -p ~/keys
chmod 700 ~/keys
keytool -genkeypair -v -storetype JKS \
  -keystore ~/keys/yhviewer-release.jks -alias yhviewer \
  -keyalg RSA -keysize 4096 -validity 10000
chmod 600 ~/keys/yhviewer-release.jks
```

备份密钥文件、alias、store password 和 key password 到可靠的离线存储/密码管理器。若两个密码不同，分别保存。丢失正式密钥后，直接分发的 APK 无法用新密钥覆盖升级。不要覆盖已有密钥或把 debug/本次临时密钥作为正式密钥。

`.gitignore` 已排除 `*.jks`、`*.keystore`、`keystore.properties`。不要创建含凭据的提交文件。签名配置只读四个 `SIGNING_*` 环境变量；全部未设时允许未签名构建，部分缺失时直接报错。

### 设置 GitHub Secrets

在仓库目录执行（需要 `gh` 登录并有仓库权限）。密码命令不带值，会交互输入；不要使用 `--body PASSWORD`。

```bash
base64 -i ~/keys/yhviewer-release.jks | gh secret set SIGNING_KEYSTORE_BASE64
gh secret set SIGNING_STORE_PASSWORD
gh secret set SIGNING_KEY_ALIAS         # 输入 yhviewer
gh secret set SIGNING_KEY_PASSWORD
```

工作流临时解码密钥，使用后清理。签名构建禁用 configuration cache，避免保存含签名参数的配置缓存。构建工具和 actions 版本与 CI 对齐，actions 固定完整 SHA。

## 本地构建与验证

以下在 **Bash** 中运行（macOS 默认 zsh 可先运行 `bash`），避免不同 shell 的 `read` 语法差异：

```bash
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME="$HOME/Library/Android/sdk"
export SIGNING_STORE_FILE="$HOME/keys/yhviewer-release.jks"
export SIGNING_KEY_ALIAS=yhviewer
read -r -s -p 'Store password: ' SIGNING_STORE_PASSWORD; echo
read -r -s -p 'Key password: ' SIGNING_KEY_PASSWORD; echo
export SIGNING_STORE_PASSWORD SIGNING_KEY_PASSWORD
./gradlew :app:testStrictDebugUnitTest :app:testStandardDebugUnitTest :app:assembleStrictRelease :app:assembleStandardRelease --no-configuration-cache
scripts/verify-release.sh app/build/outputs/apk/strict/release/app-strict-release.apk v0.3.0 io.github.zer0rin.yhviewer strict
scripts/verify-release.sh app/build/outputs/apk/standard/release/app-standard-release.apk v0.3.0 io.github.zer0rin.yhviewer standard
unset SIGNING_STORE_FILE SIGNING_STORE_PASSWORD SIGNING_KEY_ALIAS SIGNING_KEY_PASSWORD
```

校验脚本使用 SDK build-tools `36.0.0` 的 `apksigner` / `aapt2`：拒绝未签名或损坏的 APK、错误的 applicationId、tag/versionName 不匹配，以及不符合 flavor 的网络权限（Strict 禁止 INTERNET，Standard 必须包含）。不要只检查源码 Manifest。

### 安装前确认应用身份

旧 `com.example.yhviewer` 测试版与新版是两个 App，可并存；旧书架不会自动迁移。**无需卸载旧版**，卸载会删除其私有书架。先保留旧版，确认重要文档有备份。

同一 applicationId 的 debug 和正式 release 通常签名不同，无法直接覆盖。不要用卸载来绕过冲突并丢掉书架；可在空白模拟器/测试手机验证。正式版后续升级必须沿用 applicationId 和正式密钥，versionCode 持续递增。

```bash
adb devices -l
# 多设备时加 -s SERIAL，选择真实手机完成以下检查
adb -s SERIAL install -r app/build/outputs/apk/strict/release/app-strict-release.apk
```

### R8 release 真机冒烟（打 tag 前）

- [ ] HTML / Markdown / 代码 / TXT / EPUB 各打开一个；EPUB 图片和内部跳转正常
- [ ] `sandbox-test.html` 打开和重开均 9/9；自动跳转拦截，手动链接移交浏览器
- [ ] Markdown 原始 HTML 和 `</script>` 测试不执行；EPUB 章节脚本不执行
- [ ] 四个主题、代码高亮、复制不带行号、换行开关正常
- [ ] 阅读位置、收藏在后台保存/重启后保留；源文件删除后副本仍可打开
- [ ] 最终 release APK 通过版本、applicationId、签名和各 flavor 的 INTERNET 权限检查
- [ ] 后续版本：使用相同正式密钥覆盖安装上一正式版本，书架/收藏/进度保留

v0.2.0 已在 Redmi 23049RAD8C / API 35 真机完成格式、安全和副本重启冒烟。后续发布仍需重复真机清单；模拟器检查不能替代真机项。旧版本到新版本的 Room 迁移测试也不能证明不同 applicationId 之间的数据迁移。

## 正式发布

确认上述检查、备份与 Secrets 完成后，修改版本并提交。versionName 必须与 tag 去掉 `v` 后完全相同；versionCode 必须比此前正式发布版本大。确认工作区干净且普通 CI 通过，再显式发布：

```bash
git tag v0.3.0
git push origin v0.3.0
```

仅 `v*` tag push 触发 release workflow；普通 main push 不会发布。顺序：单元测试 → tag/版本检查 → 检查 Secrets → 签名并优化构建 → 校验最终 APK → API 34 模拟器冷启动 R8 包并检查书架渲染 → 创建 GitHub Release。附带 `yhviewer-v0.3.0-strict.apk`、`yhviewer-v0.3.0-standard.apk` 和 `SHA256SUMS.txt`。本地 APK 校验与 CI 使用同一个脚本。另有 `scripts/smoke-release.py APK` 可在专用测试设备验证 R8 冷启动；它不卸载旧包，签名冲突时直接失败，不替代真机完整冒烟。

下载后可在 macOS 用 `shasum -a 256`、Linux 用 `sha256sum -c SHA256SUMS.txt` 核对。校验和用于验证文件完整性；更新身份由 APK 签名保证。

## 故障处理

- 缺少 Secrets / 密码错误：更新凭据后重新运行失败 workflow，不要输出密码调试。
- 签名冲突：核对 applicationId 和证书，保留旧数据，不要直接卸载。
- 未发布的 tag 配置错误：先确认没有 Release/用户下载，再修复提交并处理 tag；不要随意移动已发布 tag。
- 已发布版本有问题：修复并递增 versionCode，发布新版本（如 `0.2.1`），不要复用版本号。可标记旧版有问题或停止分发，保留版本历史。

## v0.3.0 双版本验收

Strict / Standard 使用同一 applicationId 和正式签名，可覆盖安装并保留书架。先在专用模拟器验证两种方向的覆盖安装；真机只用新建的测试文档。验收 TXT/EPUB 沉浸阅读、翻页和排版；HTML 默认沙箱 9/9；每份文档存储隔离与撤权；权限预设及 iframe 警告。信任不含 iframe 和主动联网，两项必须单独打开。未经本版本真机验收，不要将历史真机记录作为发布依据。
