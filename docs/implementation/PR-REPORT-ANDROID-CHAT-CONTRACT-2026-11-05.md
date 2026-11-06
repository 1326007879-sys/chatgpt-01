# Android 文字聊天接口契约交付报告

基线：`34d810b1eadd98d00e7cb9b3b51428a45b5bf03e`，分支 `feat/android-ai-phase1`，日期 2026-11-05。
范围：第一阶段批次 1，仅数据、接口、纯状态转换与测试。没有真实模型、加密存储、会话协调器或 UI 实现。
关联：[Android 说明](ANDROID.md)、[本批契约](ANDROID-CHAT-CONTRACT.md)。

## 一、核心特性

- 消息身份、角色、状态、结构化失败与 schema=1 会话；禁止未完成回复进入模型上下文。
- 非敏感配置与 API Key 分离；凭据接口按服务地址隔离，HTTPS 地址校验不回显粘贴内容。
- cold Flow 网络边界、原子会话存储边界和会话/UI 协调接口；本批不提供这些接口的运行实现。
- requestId 隔离的生成状态转换；停止/失败/中断保留部分文本，终态与旧请求回调不改变新状态。

## 二、修改文件说明

下面为本批实际工作区增删；新增文件的行数按完整文件统计，已有文件使用 `git diff --numstat`。

| 文件 | 增删 | 原因 |
|---|---|---|
| `android/app/src/main/java/io/github/dshpet/mobile/chat/ChatModelConfig.kt` | +36 / −0 | HTTPS 配置验证、脱敏密钥类型，分离敏感与非敏感数据 |
| `android/app/src/main/java/io/github/dshpet/mobile/chat/ChatModels.kt` | +61 / −0 | 消息/会话/请求及状态与身份校验 |
| `android/app/src/main/java/io/github/dshpet/mobile/chat/ChatPorts.kt` | +62 / −0 | 网络、配置、凭据、存储和 UI/会话边界 |
| `android/app/src/main/java/io/github/dshpet/mobile/chat/GenerationState.kt` | +55 / −0 | 请求隔离、终态保护与停止/中断转换 |
| `android/app/src/test/java/io/github/dshpet/mobile/chat/ChatContractsTest.kt` | +98 / −0 | 消息、schema、上下文、配置与密钥边界测试 |
| `android/app/src/test/java/io/github/dshpet/mobile/chat/GenerationStateTest.kt` | +71 / −0 | 增量、迟到事件、停止、错误、空回复与中断测试 |
| `docs/ANDROID-CHAT-CONTRACT.md` | +104 / −0 | 模块归属、数据、并发、存储、安全与后续接入规则 |
| `docs/ANDROID.md` | +3 / −0 | 增加正在开发的聊天契约入口，保留原功能说明 |
| `docs/INDEX.md` | +4 / −1 | 登记契约和本批报告并补充阅读条件 |
| `docs/PR-REPORT-ANDROID-CHAT-CONTRACT-2026-11-05.md` | +97 / −0 | 本批修改与验证证据 |

没有修改现有桌宠 Activity、悬浮服务、物理、动画、音效、偏好、AndroidManifest 或 Gradle 依赖。
新增类不由原应用实例化；尚未发布或推送。接口所有权及后续各批共享文件的接入顺序以契约为准。

## 三、实现要点

公共边界集中在 `io.github.dshpet.mobile.chat`，后续实现通过子包依赖这些类型，不复制另一套消息模型。
枚举用名称保存，拒绝未知会话版本；失败原因是稳定错误码和重试策略，不携带原始服务器正文。
`ApiKey` 不使用 data class，诊断字符串脱敏；这不能替代后续真实的 Android Keystore 加密实现。
配置非法时不静默改写，禁止 HTTP、URL 内嵌用户信息、query/fragment 和非法端口。
生成状态没有线程、I/O 或 Android 依赖，由未来会话协调器串行调用，并负责真正取消网络。

测试先加入、运行出现 unresolved reference（`red.log`），再实现并运行成功（`focused.log`）；
该红记录证明当时接口尚不存在，不冒充已有应用缺陷的行为回归。最终全量另见下节。

## 四、性能分析

本地 Linux x86_64 云执行器，Temurin JDK 17.0.21+9；微测使用 `-Xmx128m`。
命令为 `javac -cp <app debug Kotlin classes>:<kotlin-stdlib-2.1.21.jar> GenerationBenchmark.java`，
随后 `java -Xmx128m -cp <相同 classpath>:/workspace/android-batch1 GenerationBenchmark`。
完整微测源码与原始输出在 `/workspace/android-batch1/GenerationBenchmark.java`、`benchmark.log`。

预热 2,000 次，7 组各 1,000 次请求，每次 64 个 16 字符 ASCII 增量，最终 1,024 字符；
每次验证 COMPLETED 与最终长度，checksum=9,216,000。各组总耗时（纳秒）：
37,627,188 / 10,308,375 / 4,153,739 / 6,507,647 / 3,998,489 / 5,007,901 / 3,912,192。
组中位每请求约 5.007 微秒。样本受 JIT/GC 影响，这是 JVM 纯转换微测，不是手机帧率、网络吞吐或优化对比。

本批没有接入应用调用路径，未增加逐帧回调、后台任务、磁盘写入或网络请求。
纯状态转换在实际接入后会随增量创建新状态和字符串，长回复拼接成本、驻留内存和后台耗电本批未测；
不能以这次短测声称内存无增长。没有新增常驻缓存；系统调用/线程/网络/磁盘的运行实现留待后续批次。

## 五、实机运行记录

本机执行了真实 Gradle 编译、JVM 测试和 lint。当前 `/workspace/android-tools/sdk/platform-tools/adb devices -l`
仅输出 `List of devices attached`，无设备；`/dev/kvm` 不存在。
因此没有手机/模拟器安装运行记录，没有执行 Android 插桩测试，也没有任何聊天界面或真实模型调用成功的声明。
现有界面未接线，不要求用手机证明一个尚未提供的聊天入口；后续集成批次仍需执行设备验证。

## 六、测试与验证

工具包装脚本：`/workspace/android-batch1/gradle.sh`，固定 JDK、SDK、Gradle 和当前云环境代理参数。

| 门 | 命令/证据 | 结果 |
|---|---|---|
| 先红 | `gradle.sh testDebugUnitTest --tests 'io.github.dshpet.mobile.chat.*'`，`red.log` | 缺少类型时编译失败 |
| 聚焦 | 同命令，`focused.log` | 首轮 14 项通过；最终增补后新增契约测试为 16 项 |
| Android 全量 | `gradle.sh testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` | 104 项通过，0 失败/错误/跳过 |
| lint | `app/build/reports/lint-results-debug.xml` | 0 错误，14 条 Warning、7 条 Hint，与批次 0 相同 |
| 构建 | 上述 Gradle 命令 | 应用与插桩测试 APK 编译通过，设备测试未运行 |
| 文档与素材门禁 | `pytest -q tests/test_android_assets.py tests/test_android_acceptance.py tests/test_packaging_keyring_notices.py tests/test_pr_report_discipline.py` | 263 项通过，见本机 `python-validation.log` |
| diff 卫生 | `git diff --check` | 通过 |

本批应用 Debug APK 为 161,466,950 字节；签名校验通过，SHA-256 `e69271ee112f569344671cbbfb0da59b432ca1f538a3e8f12fff7de1438fd3e3`。

最终构建日志：`/workspace/android-batch1/final-validation.log`；结构化汇总：`results.json`。
原桌面 Python 产品代码未改，未运行无关桌面全量测试；本批无新增真实线程/Qt 时序测试，不运行高负载时序族。

## 七、已知限制与后续

本批是契约和可执行数据/状态基础，不代表已经有 AI 聊天能力。所有 Flow、密钥隔离与加密、
原子保存、串行生成及重试行为的接口约定必须在各自实现批次再次测试。
下一批为模型接入与安全配置，需用户明确确认后才开始；不提前增加网络权限、依赖或设置 UI。

## 八、风险与回滚

没有已落盘新数据或迁移，没有默认启用聊天。风险主要是后续实现偏离公共契约；通过先更新契约测试
和文档控制接口变动。回退时移除本批新增 chat 源码、测试和文档，并恢复 Android 说明与索引中的本批行；
不能用 `git reset --hard` 覆盖未来用户的其他改动。
