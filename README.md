# Android AI 桌宠

<p align="center">
  <a href="https://github.com/1326007879-sys/chatgpt-01/releases/tag/v0.1.0-preview.4"><img alt="插件测试预览版" src="https://img.shields.io/badge/版本-v0.1.0--preview.4-blue"></a>
  <img alt="Android 8.0 及以上" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84">
  <a href="LICENSE"><img alt="代码许可 MIT" src="https://img.shields.io/badge/代码许可-MIT-orange"></a>
  <a href="https://github.com/1326007879-sys/chatgpt-01/issues"><img alt="问题反馈" src="https://img.shields.io/github/issues/1326007879-sys/chatgpt-01"></a>
</p>

一个基于 **Kotlin + Jetpack Compose** 的安卓悬浮桌宠，提供动画互动和 OpenAI Chat Completions 兼容的流式文字聊天。
基于 [dsh-pet-indesktop](https://github.com/1326007879-sys/dsh-pet-indesktop) 的安卓实现独立开发，源码、安装包和后续更新由本仓库维护。

> **当前版本：v0.1.0-preview.4 · 插件测试预览版**
>
> 已提供 Android APK，供安装测试和反馈。自动化测试与构建通过；**插件授权、真实调用和故障恢复等设备端到端验收尚未完成**。当前为 Debug 签名，并非稳定正式版。

<p align="center">
  <a href="https://github.com/1326007879-sys/chatgpt-01/releases/download/v0.1.0-preview.4/chatgpt01-petai-0.1.0-preview.4-debug.apk"><strong>下载安卓安装包</strong></a> ·
  <a href="https://github.com/1326007879-sys/chatgpt-01/releases/tag/v0.1.0-preview.4">发布说明与全部附件</a> ·
  <a href="https://github.com/1326007879-sys/chatgpt-01/issues">反馈问题</a>
</p>

## 目录

- [本版新增功能](#本版新增功能)
- [下载与版本选择](#下载与版本选择)
- [安装、升级与卸载](#安装升级与卸载)
- [快速开始](#快速开始)
- [功能概览](#功能概览)
- [动画与插件使用](#动画与插件使用)
- [AI 对话使用教程](#ai-对话使用教程)
- [配置、隐私与数据](#配置隐私与数据)
- [已知限制与后续方向](#已知限制与后续方向)
- [反馈与求助](#反馈与求助)
- [项目来源与许可证](#项目来源与许可证)
- [版本与变更历史](#版本与变更历史)
- [开发者与贡献](#开发者与贡献)

## 本版新增功能

**预览4：独立 APK 插件原型**

- 插件管理：安装入口、发现、授权、启用、手动调用、停用和系统卸载。
- 本地便签插件：保存、关键词查询、分页和删除；**目前不会定时提醒**。
- 故障测试插件：用于异常、超时、插件进程退出及权限拒绝测试，普通用户可不安装。
- 插件运行于独立进程，不将第三方代码加载进主应用；当前不开放聊天记录、模型密钥或自动调用。

**沿用预览2、3的功能**

- 手动选择内置动画，11段核心动画随主应用安装，95段扩展动作可以分别导入、删除。
- 语音输入使用手机键盘的听写功能；回复朗读、试听和音色选择使用设备系统 TTS。
- 默认简体中文，可切换中文 / English；主应用页面、菜单和通知同步切换。

插件的设备端到端测试仍待完成；“已经实现”不代表“所有手机已验收”。

## 下载与版本选择

| 项目 | 当前发布内容 |
|---|---|
| 版本 | `v0.1.0-preview.4`，开发预览版 |
| 系统 | Android 8.0 / API 26 及以上 |
| 安装文件 | [chatgpt01-petai-0.1.0-preview.4-debug.apk](https://github.com/1326007879-sys/chatgpt-01/releases/download/v0.1.0-preview.4/chatgpt01-petai-0.1.0-preview.4-debug.apk) |
| 大小 | 约 155 MiB |
| 应用名称 / 包名 | AI 桌宠·预览4 / `io.github.chatgpt01.petai.preview4` |
| 签名 | GitHub Actions 构建的 Debug 签名，非正式发行签名 |

也可打开 [Releases](https://github.com/1326007879-sys/chatgpt-01/releases)，进入对应版本，展开 **Assets** 下载 `.apk`。
`Source code (zip)` 和 `Source code (tar.gz)` 是源码压缩包，不能直接安装到手机。

发布附件同时提供 `INSTALL.md`、`KNOWN_ISSUES.md`、`SHA256SUMS.txt`、`SOURCE.json`、签名和 APK 元数据，以及许可证与素材声明。校验值以该版本附件为准。
当前仅提供安卓预览安装包；桌面原版请前往来源仓库。

## 安装、升级与卸载

### 首次安装

1. 在安卓手机上下载上述 APK，点击打开。
2. 如果系统提示未允许安装，按提示为下载所用的浏览器或文件管理器授权“安装未知应用”。
3. 安装完成后打开 **AI 桌宠·预览4**。
4. 需要悬浮宠物时，按应用提示授予“显示在其他应用上层 / 悬浮窗”权限；各品牌系统名称可能不同。
5. 需要 AI 聊天时，进入“和主宠聊天”配置模型，详见下方教程。

聊天入口本身不要求先授予悬浮权限。不同品牌的后台限制、耗电和长时间运行表现仍需真机反馈。

### 升级与卸载

本次发布包采用独立包名 `io.github.chatgpt01.petai.preview4`，与旧预览版及原版并存，不读取它们的私有配置和聊天数据；模型配置需在新包单独填写。
当前使用 Debug 签名，**不保证不同构建或未来正式版能覆盖升级**。若遇到签名不一致，不要在未考虑数据保留的情况下直接卸载。
通过系统“设置 → 应用 → AI 桌宠 → 卸载”删除应用；卸载或清除应用数据会删除本机配置、密钥存储和聊天历史。本版没有应用内历史导出或云同步功能。

## 快速开始

1. **桌宠互动**：授予悬浮权限后使用桌宠，体验动画、拖动、抛掷、碰撞和伙伴互动。
2. **打开聊天**：主界面点击“和主宠聊天”，或长按主宠，在菜单中选择同名入口。
3. **配置模型**：在聊天页打开“模型设置”，填写兼容服务的地址、模型和 API Key。
4. **发送文字**：保存设置后发送消息；生成过程中可停止，失败后可按界面提示重试。

## 功能概览

| 功能 | 最新预览版范围 |
|---|---|
| 悬浮桌宠 | 动画播放、拖动、抛掷、碰撞、伙伴互动与点击音效 |
| 聊天入口 | 主界面和长按菜单进入同一个主宠会话 |
| AI 对话 | HTTPS Chat Completions / SSE 流式文字回复 |
| 生成控制 | 停止生成、失败反馈和手动重试 |
| 模型设置 | 服务地址、模型与 API Key |
| 动画管理 | 默认11段核心，95个扩展动作可分别导入和删除 |
| 界面语言 | 简体中文 / English，页面与菜单同步 |
| 语音与音色 | 键盘听写、系统 TTS 回复朗读、设备可用音色选择 |
| 插件原型 | 独立 APK、本地便签、手动调用与授权；可选故障样例 |
| 本地恢复 | 已保存的聊天历史和中断状态恢复，不自动重发中断请求 |
| 密钥存储 | Android Keystore 支持的加密存储，按服务地址隔离 |

以上描述的是已实现范围，不代表已完成全部手机适配。验证细节以 [预览4已知问题](https://github.com/1326007879-sys/chatgpt-01/blob/v0.1.0-preview.4/docs/releases/v0.1.0-preview.4/KNOWN_ISSUES.md) 为准。

## 动画与插件使用

### 按需安装动画

主应用约155 MiB，仅内置11段核心动画。其余95个动作继续使用 [预览3动作包目录](https://github.com/1326007879-sys/chatgpt-01/blob/v0.1.0-preview.3/docs/releases/v0.1.0-preview.3/ANIMATIONS.md)，与预览4兼容。
下载需要的单个 `.petanim` 文件，在应用动画管理中导入；不必整包安装。导入和删除前按界面提示停止悬浮桌宠，完成后可重新启动。
动画包是素材资源，不是可执行插件。全部安装会额外占用约1.25 GiB，按需选择即可。详见 [动作包使用与制作](https://github.com/1326007879-sys/chatgpt-01/blob/v0.1.0-preview.4/docs/ANIMATION_PACKS.md)。

### 安装本地便签插件

| 附件 | 用途 |
|---|---|
| [本地便签 APK](https://github.com/1326007879-sys/chatgpt-01/releases/download/v0.1.0-preview.4/petai-notes-plugin-0.1.0-test-debug.apk) | 约0.92 MiB，存储和查询自己的便签 |
| [故障样例 APK](https://github.com/1326007879-sys/chatgpt-01/releases/download/v0.1.0-preview.4/petai-fault-plugin-0.1.0-test-debug.apk) | 约0.92 MiB，仅供开发测试，可不安装 |

1. 安装主应用和便签插件，系统可能要求允许该安装来源。
2. 从手机应用列表打开“桌宠插件·本地便签”，允许“AI 桌宠·预览4”访问。
3. 回主应用“插件管理”，刷新 → 启用本地便签 → 确认权限 → 打开功能。
4. 输入便签并保存；查询关键词留空可看列表，选择返回的记录编号可以删除。

便签最多200条，每条1000字符，每页10条。停用保留数据；卸载或清除插件数据会删除便签。超时后先查询保存结果，避免重复写入。
**写下“下午一点提醒我”只会保存文字，当前不会到点通知。**

插件授权页跟随系统语言，主应用插件页跟随应用语言。完整操作及测试限制见 [安装指南](https://github.com/1326007879-sys/chatgpt-01/blob/v0.1.0-preview.4/docs/releases/v0.1.0-preview.4/INSTALL.md) 和 [插件协议 v1](https://github.com/1326007879-sys/chatgpt-01/blob/v0.1.0-preview.4/docs/PLUGIN_PROTOCOL.md)。

## AI 对话使用教程

### 准备服务配置

你需要自行准备支持 **OpenAI Chat Completions 兼容接口和 SSE 流式输出**的服务，以及该服务的模型名称和 API Key。本应用不附赠共享密钥；服务是否收费以服务商为准。

在“和主宠聊天 → 模型设置”中填写：

| 配置项 | 填写说明 |
|---|---|
| 服务地址 | 服务商提供的 HTTPS API 根地址，通常类似 `https://api.example.com/v1`；不要自行追加 `/chat/completions` |
| 模型 | 服务商实际支持的模型 ID，按其文档填写 |
| API Key | 对应服务的密钥，仅在手机应用内输入 |

上面的域名仅为格式示例，不能直接调用。并非所有标称“OpenAI 兼容”的服务或模型都支持本版接口。

### 开始与恢复对话

保存后发送一条简短文字，确认回复能够逐步显示。生成中点击停止可取消本次请求；遇到错误时先核对服务地址、模型、密钥、网络及服务额度，再手动重试。

当前为**主宠单会话、纯文字显示**，不渲染 Markdown。返回聊天页可恢复已保存的内容；进程退出后未发送的草稿不会保留，中断回复不会自动继续请求。

## 配置、隐私与数据

- API Key 使用 Android Keystore 支持的加密存储，不以明文回退保存，并按规范化服务地址隔离。
- 聊天历史保存在应用私有目录，**历史文件本身不是应用层加密文件**；历史不包含密钥字段。
- 发起聊天时，系统提示词、当前消息及选取的历史上下文会发送到你配置的服务。请按该服务的隐私政策使用。
- 本版无聊天云同步。卸载或清除应用数据会删除本地内容；不能将自动保存视为备份。
- 历史采用合并写入，突然终止进程可能丢失最近尚未写入的内容；未发送草稿不跨进程保存。

## 已知限制与后续方向

当前需注意：

- 新包名的真机安装、界面、键盘、旋转、后台稳定性及真实模型调用尚待完整验收。
- 迁移前软件模拟器的界面测试曾出现进程退出，尚未完成定位。
- 正式签名及升级迁移方案未确定；预览版不作生产环境兼容承诺。
- 键盘语音由输入法提供，回复朗读依赖设备TTS与语音数据，真实效果尚待验收；无连续语音通话、唤醒词、声音克隆、主动识屏、多会话、历史导出或云同步。
- 历史文件有大小上限，暂未提供分页和清理界面。
- 当前插件仅支持手动文本操作；没有在线插件市场、自动更新、云记忆或定时提醒。便签数据为插件私有目录内的明文 JSON。

完整说明见 [当前版本已知问题](https://github.com/1326007879-sys/chatgpt-01/releases/download/v0.1.0-preview.4/KNOWN_ISSUES.md)。
后续优先根据真机反馈完善稳定性、模型兼容和签名升级方案。

**已记录、尚未实现的候选方向**：

- 便签定时/重复提醒、自然语言创建待办，逐步探索 AI 小秘书。
- 独立语音引擎插件与自定义音色包，改善朗读自然度；支持同一音色合成未见过的新句子。
- 本地/用户自建云端的记忆插件及数据接口。

完整待讨论事项见 [下一次更新需求清单](docs/NEXT_UPDATE.md)。以上没有交付日期承诺，需确认范围后分批实施。

## 反馈与求助

请在 [Issues](https://github.com/1326007879-sys/chatgpt-01/issues) 提交问题，尽量提供：

- 应用版本、手机型号、Android 版本。
- 操作步骤、预期结果和实际表现，是否稳定复现。
- 已遮挡敏感内容的截图或错误提示。
- 模型相关问题可注明服务名称和模型 ID，**不要提交 API Key、密码或完整私人聊天记录**。

## 项目来源与许可证

原作者项目：[MerZlin/dsh-pet-indesktop](https://github.com/MerZlin/dsh-pet-indesktop)。本项目最初源自 [1326007879-sys/dsh-pet-indesktop](https://github.com/1326007879-sys/dsh-pet-indesktop) 的安卓实现，保留来源、版权及素材声明，在本仓库独立维护。来源提交与迁移范围见 [PROVENANCE.md](https://github.com/1326007879-sys/chatgpt-01/blob/v0.1.0-preview.4/docs/PROVENANCE.md)。

代码采用 [MIT 许可证](LICENSE)。**角色素材另有个人、非商业使用限制，代码开源不等于素材可以商用**；详细内容见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 和 [Android 声明](android/NOTICE.txt)。感谢原项目及相关素材作者。

## 版本与变更历史

| 版本 | 日期（北京时间） | 内容 |
|---|---|---|
| [v0.1.0-preview.4](https://github.com/1326007879-sys/chatgpt-01/releases/tag/v0.1.0-preview.4) | 2026-11-10 | 独立APK插件原型、本地便签和故障测试插件；设备验收待补 |
| [v0.1.0-preview.3](https://github.com/1326007879-sys/chatgpt-01/releases/tag/v0.1.0-preview.3) | 2026-11-09 | 核心安装包、95个独立动作包、安装管理、键盘听写和统一语言 |
| [v0.1.0-preview.2](https://github.com/1326007879-sys/chatgpt-01/releases/tag/v0.1.0-preview.2) | 2026-11-09 | 六模块验收预览版 |
| [v0.1.0-preview.1](https://github.com/1326007879-sys/chatgpt-01/releases/tag/v0.1.0-preview.1) | 2026-11-06 | 首个独立安卓预览版 |

## 开发者与贡献

**主页说明更新不等于主分支源码已合入全部功能。** 默认分支保留项目基线；构建最新已发布版本请检出 [`v0.1.0-preview.4`](https://github.com/1326007879-sys/chatgpt-01/tree/v0.1.0-preview.4)，不要用旧基线的代码推断新版功能。
本页的版本文档链接固定到发布标签，后续计划链接指向默认分支。

应用采用原生 Kotlin / Jetpack Compose。Python、QtCore 与 ffmpeg 仅用于构建时素材转换，APK 不依赖桌面 Python 应用。

| 目录 | 职责 |
|---|---|
| `android/` | 安卓应用、单元测试及设备测试 |
| `assets/` | 构建所需核心动画、定位/移动元数据与音效 |
| `tools/asset_pipeline/` | 独立的构建用素材转换器 |
| `scripts/`、`tests/` | 构建入口与素材契约测试 |
| `docs/` | 构建、来源、架构、验收及历史记录 |
| `.github/workflows/` | 自动检查和构建；发布流程在独立开发分支中维护 |

- [构建与检查](https://github.com/1326007879-sys/chatgpt-01/blob/v0.1.0-preview.4/docs/BUILD.md)
- [模块与依赖](https://github.com/1326007879-sys/chatgpt-01/blob/v0.1.0-preview.4/docs/ARCHITECTURE.md)
- [当前版本验收边界](https://github.com/1326007879-sys/chatgpt-01/blob/v0.1.0-preview.4/docs/releases/v0.1.0-preview.4/KNOWN_ISSUES.md)
- [本版发布说明](https://github.com/1326007879-sys/chatgpt-01/blob/v0.1.0-preview.4/docs/releases/v0.1.0-preview.4/RELEASE.md)

主页更新规则见 [主页维护流程](docs/HOMEPAGE_MAINTENANCE.md)。发布每一版时应同步版本入口、功能表、安装指南、已知问题和历史记录；GitHub 不会根据 APK 自动改写 README。

修改在独立分支开发，通过 PR 审阅后合入。APK、工具链、缓存、模型密钥和签名私钥不提交到源码目录。
平台云环境配置由平台管理；修改本仓库文档不会自动修改或发布云环境。
