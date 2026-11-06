# Android AI 第一阶段集成验收与测试包 · 批次 6

日期 2026-11-05；分支 feat/android-ai-phase1；基线 34d810b1eadd98d00e7cb9b3b51428a45b5bf03e。
此前五批源码保留未提交；没有推送、发布 Release 或发布云环境。
使用说明见 [ANDROID.md](ANDROID.md)，接口与生命周期规则见 [ANDROID-CHAT-CONTRACT.md](ANDROID-CHAT-CONTRACT.md)。

## 修改文件说明

- 新增 `ChatIntegrationTest.kt`：实际本机 TLS 请求、生产 OpenAiTransport、DefaultChatConversation 与真实 FileChatSessionStore 串联，验证两轮中文/表情对话、关闭重开、上下文，以及文件不含测试密钥。
- 新增本报告并更新 Android 说明/文档索引，交付目录位于仓库外 `/workspace/deliverables/android-phase1`。
- 交付应用 APK、插桩 APK、校验文件、使用说明、验收结果和源码补丁。源码补丁包含之前各批新增文件，已用 git apply --reverse --check 检查与当前工作区对应。
- 官方软件模拟器和 API35 镜像仅安装于工具目录；未修改应用 TLS 规则、权限或用户手机数据。

## 性能分析

本机 Linux/Temurin JDK17；新增组合测试 1 项实测 1.135 秒，属于测试用时，不是手机吞吐。
截至本批，已有 136 项 JVM 测试通过，前批高负载测试记录保留；本次未重复无变化的压力族。
软件模拟器缺少 KVM，首次启动和安装耗时不能代表手机启动速度，未作任何真机耗电/温升/帧率保证。
APK 体积、SHA-256、测试时间与模拟器结果见 `/workspace/android-batch6/results.json` 和交付目录验收结果。
本批只新增测试和交付文档，没有增加生产帧回调、线程、缓存、网络调用或磁盘写入路径。

## 实机运行记录

本机确实运行 JVM 组合测试及构建。136 项测试 0 失败/错误/跳过；APK 签名和清单/打包内容另有记录。
无真实手机、无服务商凭据绑定；未向外部模型服务发起请求，不把本机测试服务器当成真实模型联调。

额外尝试官方 emulator37.2.13 + API35 x86_64 默认镜像，以 -accel off 软件模拟。
能力探针报告 KVM requires a CPU that supports vmx or svm；没有 /dev/kvm。模拟进程能启动且 ADB 后来在线。
早期安装在系统 boot_completed 尚未置1时失败，堆栈位于 StorageManagerService.freeStorage/PackageManagerInternal 初始化，不是应用代码异常。
初始失败日志与后续启动/安装/插桩结果保存在 `/workspace/android-batch6/`，最终状态以验收结果为准。

## 测试与验证

- 全量 Android：`bash /workspace/android-batch1/gradle.sh testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`。
- 组合测试运行生产 HTTP/SSE 与会话/文件实现，假服务仅在本机使用可信测试证书；未关闭 TLS/主机名验证。
- 代码/资源/许可/文档门禁用现有 pytest 相关套件；最终输出见 python.log。
- 交付 APK 与构建产物逐字节一致，SHA256SUMS.txt 标识实际测试包，版本元数据仍为0.2.1/code3的开发构建。
- 设备测试只在真正执行并输出结果后计为通过；任何超时、未运行、跳过或失败独立记录。

## 交付与未完成边界

交付目录的“使用说明.md”提供安装、模型配置与手机验收步骤；不要在聊天中提供密钥。
本次为第一阶段开发测试包，不是商店正式发行版，不代表真实手机、真实服务或全平台完整验收。
需真实设备补：悬浮交互、菜单长按/滚动、返回栈、旋转、键盘、后台政策、耗电与温升。
需用户在应用内配置可用模型后补：真实服务端多轮/流式/停止/错误/重试。
原有角色素材继续受非商业用途声明限制，完整第三方说明随包。

## 2026-11-06 继续验收结果

- API35 软件模拟器安装应用/测试包成功；Android Keystore 与配置存储真实设备测试 **1 项通过**（device-stores.log）。
- 界面与入口首次测试进程因启动 ANR 退出，系统同时存在 System UI/电话服务 ANR；重启后重新运行仍报告 Process crashed，无通过用例报告，不能声称 UI 验收完成。
- 第二次 crash 缓冲区未给出明确应用异常堆栈，尚不能完全排除应用侧问题；需要稳定设备继续定位。
- 136 项 JVM（含真实 TLS/文件恢复组合）与273项相关 Python 检查通过；APK构建/签名/许可随包检查通过。
- Android NOTICE 已补齐项目自身 MIT 正文，资产重打包未改运行逻辑。
- 交付目录 /workspace/deliverables/android-phase1 含 APK、测试APK、SHA256SUMS、使用说明、验收结果、完整源码补丁；补丁反向检查通过。
- 第一阶段开发测试包已交付；整体验收未全部完成。尚缺稳定设备 UI/后台/键盘/旋转/悬浮验收，以及用户在应用内配置真实服务后的联调。
