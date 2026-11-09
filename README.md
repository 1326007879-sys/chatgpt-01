# Android AI 桌宠

<p align="center">
  <a href="https://github.com/1326007879-sys/chatgpt-01/releases/tag/v0.1.0-preview.3"><img alt="独立动作包预览版" src="https://img.shields.io/badge/版本-v0.1.0--preview.3-blue"></a>
  <img alt="Android 8.0 及以上" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84">
  <a href="LICENSE"><img alt="代码许可 MIT" src="https://img.shields.io/badge/代码许可-MIT-orange"></a>
  <a href="https://github.com/1326007879-sys/chatgpt-01/issues"><img alt="问题反馈" src="https://img.shields.io/github/issues/1326007879-sys/chatgpt-01"></a>
</p>

一个基于 **Kotlin + Jetpack Compose** 的安卓悬浮桌宠，提供动画互动和 OpenAI Chat Completions 兼容的流式文字聊天。
基于 [dsh-pet-indesktop](https://github.com/1326007879-sys/dsh-pet-indesktop) 的安卓实现独立开发，源码、安装包和后续更新由本仓库维护。

> **当前版本：v0.1.0-preview.3 · 独立动作包预览版**
>
> 已提供 Android APK，供安装测试和反馈。自动化测试与构建通过；**新包名的真机、完整界面稳定性和真实模型服务仍待验收**。当前为 Debug 签名，并非稳定正式版。

<p align="center">
  <a href="https://github.com/1326007879-sys/chatgpt-01/releases/download/v0.1.0-preview.3/chatgpt01-petai-0.1.0-preview.3-debug.apk"><strong>下载安卓安装包</strong></a> ·
  <a href="https://github.com/1326007879-sys/chatgpt-01/releases/tag/v0.1.0-preview.3">发布说明与全部附件</a> ·
  <a href="https://github.com/1326007879-sys/chatgpt-01/issues">反馈问题</a>
</p>

## 目录

- [下载与版本选择](#下载与版本选择)
- [安装、升级与卸载](#安装升级与卸载)
- [快速开始](#快速开始)
- [功能概览](#功能概览)
- [AI 对话使用教程](#ai-对话使用教程)
- [配置、隐私与数据](#配置隐私与数据)
- [已知限制与后续方向](#已知限制与后续方向)
- [反馈与求助](#反馈与求助)
- [项目来源与许可证](#项目来源与许可证)
- [版本与变更历史](#版本与变更历史)
- [开发者与贡献](#开发者与贡献)

## 本版新增功能

- 语音输入调整为手机键盘听写，移除独立识别窗口。
- 默认简体中文，提供统一的中文 / English 界面切换。
- 默认仅内置11段核心动画，其余95段以独立动作包导入，支持安装管理和单个删除。
- [独立动作包使用与制作](docs/ANIMATION_PACKS.md)。

验证进度与限制见 [本轮实施记录](docs/implementation/KEYBOARD-LANGUAGE-ANIMATIONS.md)。

## 下载与版本选择

| 项目 | 当前发布内容 |
|---|---|
| 版本 | `v0.1.0-preview.3`，开发预览版 |
| 系统 | Android 8.0 / API 26 及以上 |
| 安装文件 | [chatgpt01-petai-0.1.0-preview.3-debug.apk](https://github.com/1326007879-sys/chatgpt-01/releases/download/v0.1.0-preview.3/chatgpt01-petai-0.1.0-preview.3-debug.apk) |
| 大小 | 约 155 MiB |
| 应用名称 / 包名 | AI 桌宠·预览3 / `io.github.chatgpt01.petai.preview3` |
| 签名 | 云工作区构建的 Debug 签名，非正式发行签名 |

也可打开 [Releases](https://github.com/1326007879-sys/chatgpt-01/releases)，进入对应版本，展开 **Assets** 下载 `.apk`。
`Source code (zip)` 和 `Source code (tar.gz)` 是源码压缩包，不能直接安装到手机。

发布附件同时提供 `INSTALL.md`、`KNOWN_ISSUES.md`、`SHA256SUMS.txt`、`SOURCE.json`、签名和 APK 元数据，以及许可证与素材声明。校验值以该版本附件为准。
当前仅提供安卓预览安装包；桌面原版请前往来源仓库。

## 安装、升级与卸载

### 首次安装

1. 在安卓手机上下载上述 APK，点击打开。
2. 如果系统提示未允许安装，按提示为下载所用的浏览器或文件管理器授权“安装未知应用”。
3. 安装完成后打开 **AI 桌宠·预览3**。
4. 需要悬浮宠物时，按应用提示授予“显示在其他应用上层 / 悬浮窗”权限；各品牌系统名称可能不同。
5. 需要 AI 聊天时，进入“和主宠聊天”配置模型，详见下方教程。

聊天入口本身不要求先授予悬浮权限。不同品牌的后台限制、耗电和长时间运行表现仍需真机反馈。

### 升级与卸载

本次发布包采用独立包名 `io.github.chatgpt01.petai.preview3`，与旧预览版及原版并存，不读取它们的私有配置和聊天数据；模型配置需在新包单独填写。
当前使用 Debug 签名，**不保证不同构建或未来正式版能覆盖升级**。若遇到签名不一致，不要在未考虑数据保留的情况下直接卸载。
通过系统“设置 → 应用 → AI 桌宠 → 卸载”删除应用；卸载或清除应用数据会删除本机配置、密钥存储和聊天历史。本版没有应用内历史导出或云同步功能。

## 快速开始

1. **桌宠互动**：授予悬浮权限后使用桌宠，体验动画、拖动、抛掷、碰撞和伙伴互动。
2. **打开聊天**：主界面点击“和主宠聊天”，或长按主宠，在菜单中选择同名入口。
3. **配置模型**：在聊天页打开“模型设置”，填写兼容服务的地址、模型和 API Key。
4. **发送文字**：保存设置后发送消息；生成过程中可停止，失败后可按界面提示重试。

## 功能概览

| 功能 | 首个预览版范围 |
|---|---|
| 悬浮桌宠 | 动画播放、拖动、抛掷、碰撞、伙伴互动与点击音效 |
| 聊天入口 | 主界面和长按菜单进入同一个主宠会话 |
| AI 对话 | HTTPS Chat Completions / SSE 流式文字回复 |
| 生成控制 | 停止生成、失败反馈和手动重试 |
| 模型设置 | 服务地址、模型与 API Key |
| 动画管理 | 默认11段核心，95个扩展动作可分别导入和删除 |
| 界面语言 | 简体中文 / English，页面与菜单同步 |
| 本地恢复 | 已保存的聊天历史和中断状态恢复，不自动重发中断请求 |
| 密钥存储 | Android Keystore 支持的加密存储，按服务地址隔离 |

以上描述的是已实现范围，不代表已完成全部手机适配。验证细节见 [验收状态](docs/STATUS.md)。

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

完整说明见 [当前版本已知问题](https://github.com/1326007879-sys/chatgpt-01/releases/download/v0.1.0-preview.3/KNOWN_ISSUES.md)。
后续优先根据真机反馈完善稳定性、模型兼容和签名升级方案；新功能在实际实现和验证后再补充到本页，暂无发布日期承诺。

## 反馈与求助

请在 [Issues](https://github.com/1326007879-sys/chatgpt-01/issues) 提交问题，尽量提供：

- 应用版本、手机型号、Android 版本。
- 操作步骤、预期结果和实际表现，是否稳定复现。
- 已遮挡敏感内容的截图或错误提示。
- 模型相关问题可注明服务名称和模型 ID，**不要提交 API Key、密码或完整私人聊天记录**。

## 项目来源与许可证

本项目源自 [1326007879-sys/dsh-pet-indesktop](https://github.com/1326007879-sys/dsh-pet-indesktop) 的安卓实现，保留来源、版权及素材声明，在本仓库独立维护。来源提交与迁移范围见 [PROVENANCE.md](docs/PROVENANCE.md)。

代码采用 [MIT 许可证](LICENSE)。**角色素材另有个人、非商业使用限制，代码开源不等于素材可以商用**；详细内容见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 和 [Android 声明](android/NOTICE.txt)。感谢原项目及相关素材作者。

## 版本与变更历史

| 版本 | 日期 | 内容 |
|---|---|---|
| [v0.1.0-preview.3](https://github.com/1326007879-sys/chatgpt-01/releases/tag/v0.1.0-preview.3) | 2026-11-09 | 核心安装包、95个独立动作包、安装管理、键盘听写和统一语言 |
| [v0.1.0-preview.2](https://github.com/1326007879-sys/chatgpt-01/releases/tag/v0.1.0-preview.2) | 2026-11-09 | 六模块验收预览版 |
| [v0.1.0-preview.1](https://github.com/1326007879-sys/chatgpt-01/releases/tag/v0.1.0-preview.1) | 2026-11-06 | 首个独立安卓预览版 |

## 开发者与贡献

应用采用原生 Kotlin / Jetpack Compose。Python、QtCore 与 ffmpeg 仅用于构建时素材转换，APK 不依赖桌面 Python 应用。

| 目录 | 职责 |
|---|---|
| `android/` | 安卓应用、单元测试及设备测试 |
| `assets/` | 构建所需核心动画、定位/移动元数据与音效 |
| `tools/asset_pipeline/` | 独立的构建用素材转换器 |
| `scripts/`、`tests/` | 构建入口与素材契约测试 |
| `docs/` | 构建、来源、架构、验收及历史记录 |
| `.github/workflows/` | 自动检查和构建；发布流程在独立开发分支中维护 |

- [构建与检查](docs/BUILD.md)
- [模块与依赖](docs/ARCHITECTURE.md)
- [当前验收状态](docs/STATUS.md)
- [本版发布说明](docs/releases/v0.1.0-preview.3/RELEASE.md)

修改在独立分支开发，通过 PR 审阅后合入。APK、工具链、缓存、模型密钥和签名私钥不提交到源码目录。
平台云环境配置由平台管理；修改本仓库文档不会自动修改或发布云环境。
