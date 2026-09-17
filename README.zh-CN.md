# CJ-ssh-client

[English](README.md) | [简体中文](README.zh-CN.md)

CJ-ssh-client 是一款原生 Android 8.0+ SSH 客户端，使用 Kotlin、Jetpack Compose、Material 3、Hilt、Room、DataStore、Coroutines/Flow 和 mwiede/JSch 构建。

> 截图：在真机安装后，可将手机、平板、终端和 SFTP 截图放入 `docs/screenshots/`。

## 功能

- 新建、编辑、删除和搜索连接，支持滑动删除与长按多选
- 密码、私钥以及私钥加密码三种认证方式
- 严格 SSH 主机密钥校验：首次连接明确显示 SHA256 指纹并要求确认，主机密钥变化时直接阻止连接
- 多标签 ANSI/VT 终端：精确处理 CJK/Nerd Font 单元格、ANSI 16 色、xterm-256 色和 TrueColor 前景/背景
- 终端直接接收输入法输入，支持中文等组合输入、Termux 风格扩展键、文本选择、复制、粘贴、滚动和双指缩放
- 输入法显示、隐藏以及横竖屏切换时自动调整终端行列和远端 PTY 尺寸
- 可选自动重连，以及用于用户主动启动的持久会话的 `specialUse` 前台服务
- SFTP 浏览、SAF 上传/下载、删除和重命名
- 仅绑定回环地址 `127.0.0.1` 的本地端口转发
- Ed25519/RSA 密钥生成、加密导入、公钥导出和删除
- 动态配色、浅色/深色/跟随系统主题、七套终端配色、自定义 TTF/OTF 字体、字号、行高和连字设置
- 在手机、横屏、平板和折叠屏上始终保留左侧导航栏

## 架构

当前版本使用单一可安装的 `app` 模块，在避免早期多模块构建成本的同时保持明确的职责边界：

```text
app/src/main/java/io/github/chenjin/androidsshclient/
├── core/
│   ├── database/       Room 实体与 DAO
│   ├── logging/        脱敏事件日志
│   ├── model/          领域模型
│   ├── security/       Android Keystore AES-GCM
│   ├── ssh/            主机密钥、会话、SFTP、转发和前台服务
│   ├── terminal/       原生 Canvas ANSI 终端
│   └── ui/             字体加载器和 Material 主题
├── data/               Repository 实现
├── di/                 Hilt 依赖提供器
└── feature/
    ├── connections/
    ├── terminal/
    ├── tools/           SFTP、端口转发和密钥管理
    └── settings/
```

界面状态遵循 MVVM。Repository 是数据访问边界，界面不会直接访问 Room、JSch、Keystore 或 DataStore。SSH 会话状态通过 `StateFlow` 暴露。

## 工具链与依赖

- compileSdk/targetSdk 35，minSdk 26
- JDK 17、Gradle 8.9、AGP 8.7.3、Kotlin 2.0.21
- Compose BOM 2024.12.01、Material 3、Navigation Compose
- Hilt 2.52、Room 2.6.1、DataStore 1.1.1
- mwiede/JSch 0.2.21、Bouncy Castle 1.79
- JUnit、MockK、Turbine

依赖版本统一维护在 `gradle/libs.versions.toml`。

## 构建

安装 Android SDK 35 和 JDK 17，按需设置 `ANDROID_HOME`，然后执行：

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

本地正式签名时，将 `keystore.properties.example` 复制为被 Git 忽略的 `keystore.properties`，让 `storeFile` 指向仓库外的私有密钥库，并填写四项签名参数。当前工作区的固定私钥位于 `~/.config/androidssh/`，不会提交到 Git，必须进行安全备份。缺少签名参数时 release 构建不会获得正式签名；debug 构建使用独立的 `.debug` application ID。

## 字体

运行时字体资源位于 `app/src/main/assets/fonts/`：

- Fira Code Nerd Font Mono v3.3.0：Regular 和 Bold 来自 [Nerd Fonts](https://github.com/ryanoasis/nerd-fonts/releases)，包含 PUA 图标字符。Fira Code 上游没有独立 italic 字体，应用会对相应 regular/bold 字体使用 Android 合成斜体。
- Source Han Sans SC：Regular 和 Bold 来自 [Adobe Source Han Sans](https://github.com/adobe-fonts/source-han-sans/releases)，使用 `pyftsubset` 保留拉丁字符、标点、CJK 扩展、统一汉字和全角字符。
- `LICENSE-FiraCode.txt` 与 `LICENSE-SourceHanSans.txt` 保留 SIL Open Font License 声明。

`core/ui/font/AppFonts.kt` 在 `Application` 中异步加载字体。API 29+ 使用 `Typeface.CustomFallbackBuilder`，优先 Fira Code，再回退到 Source Han Sans SC；API 26-28 使用 Fira Code 与 Android 字形回退。设置页面可导入最大 20 MB 的 TTF/OTF 文件到应用私有目录。终端按固定单元格坐标绘制，将 CJK/Emoji 视为双宽字符、Nerd Font PUA 字符视为单宽字符，并通过 `Paint.fontFeatureSettings` 控制连字。

## 安全模型

- SSH 通信由 JSch 协商的加密传输保护，不会强制启用已弃用的 `ssh-rsa`。
- 首次连接会在认证前捕获并拒绝未知主机密钥，界面展示主机、端口、算法和 OpenSSH 格式的 SHA256 指纹。用户接受后写入可信记录，再以严格模式重新连接。
- 已保存的主机密钥发生变化时连接会直接失败，不提供危险的一键替换。
- 密码、私钥口令和导入的私钥只以 AES-256-GCM 密文保存到 Room，并由不可导出的 Android Keystore 密钥保护。DataStore 只保存偏好设置。
- 日志仅包含事件名称和经过哈希处理的主机标识，不记录密码、密钥材料或终端明文。
- SFTP 使用 Storage Access Framework，不申请宽泛的存储权限；上传先使用临时远端文件名，完成后再重命名。
- 本地端口转发只绑定到 `127.0.0.1`。
- 应用备份已禁用。剪贴板内容仍由 Android 和用户负责保护。

前台服务使用 `specialUse`，因为交互式 SSH shell 和隧道属于用户主动启动、持续时间不确定的加密会话，而不是有限的数据同步任务。通过 Google Play 发布时，需要在审核资料中声明并解释该 subtype；Android 后台启动限制仍然适用。

## 固定签名与 CI/CD

`.github/workflows/android.yml` 会在推送到 `main`/`master`、Pull Request、手动触发和 `v*` 标签时运行不接触 Secrets 的 debug 构建、单元测试和 Lint。

只有 `v*` 标签会启动独立的 `release` Environment 任务。该任务使用步骤级签名 Secrets 构建 release APK，并在上传前将证书 SHA-256 与以下固定值比较：

```text
5f37f56f960f88a49b4a7e7ed3c0348307b47b81da6bdac35e07a87b756706d7
```

签名缺失或指纹不一致时会停止发布。请在 GitHub 中创建受保护的 `release` Environment，按需启用部署审核，并配置：

| Secret | 内容 |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w 0 androidssh-release.jks` 的输出 |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 签名密钥别名 |
| `KEY_PASSWORD` | 签名密钥密码 |

发布新版本：

```bash
git tag v1.0.2
git push origin v1.0.2
```

普通构建可从工作流运行页面的 **Artifacts** 下载；正式版本可从仓库的 **Releases** 页面下载。

## 发布仓库

`scripts/publish.sh` 会在需要时创建初始提交。如果已安装并登录 GitHub CLI，它会创建或推送 `CJ-ssh-client`；如果已有 `origin`，则直接推送 `main`。否则脚本会输出需要手动执行的命令。

```bash
chmod +x scripts/publish.sh
./scripts/publish.sh
```

## 已知限制

内置终端实现面向 shell 的 VT/ANSI 子集，包括光标移动、擦除命令、SGR 属性、xterm-256 色、TrueColor、OSC 过滤、bracketed paste、应用光标模式和动态 PTY 尺寸。终端本身是 Android 文本编辑目标：点击可打开输入法，确认后的文本直接发送到 SSH；扩展键行会位于输入法上方或屏幕底部；长按拖动可选择文本并执行复制、粘贴和全选。

当前终端尚未实现全部 DEC 私有模式和完整 alternate-screen 行为，复杂的全屏终端程序未来可能需要接入完整终端引擎。主机密钥轮换目前需要显式清理相关可信记录。正式部署前建议使用一次性 SSH 服务器以及 API 26/35 真机进行集成测试。

`v1.0.1` 及以前的 APK 使用了每次运行生成的临时 debug 证书。从这些版本切换到固定签名版本时需要卸载旧版一次；从固定签名基线开始，后续版本可以正常覆盖升级。

## 许可证

应用源代码使用 `LICENSE` 中的 Apache License 2.0。内置字体继续使用其各自位于 assets 目录中的 SIL OFL 1.1 许可证。
