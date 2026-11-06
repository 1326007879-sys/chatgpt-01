# Android 模型接入与安全配置 · 批次 2

基线：分支 feat/android-ai-phase1，2026-11-05，接续批次 1 的未提交改动，保留既有文件。
契约见 [ANDROID-CHAT-CONTRACT.md](ANDROID-CHAT-CONTRACT.md)，应用现状见 [ANDROID.md](ANDROID.md)。
本批实现基础设施，尚无会话协调、历史存储或聊天 UI，应用不会自动发起模型请求。

## 修改文件说明

- `android/app/build.gradle.kts`：加入 OkHttp 4.12.0、Gson 2.13.1；MockWebServer 和 okhttp-tls 仅测试依赖。
- `android/app/src/main/AndroidManifest.xml`：声明 INTERNET 权限；不启用明文流量。
- `chat/transport/OpenAiTransport.kt`：HTTPS POST、cold Flow、SSE 增量、终止判定、错误分类、超时、取消与响应关闭；默认关闭重定向和隐式重试，不记录正文或凭据。
- `chat/storage/AndroidChatStores.kt`：独立配置 JSON、AtomicFile 原子写；Keystore AES-GCM 密钥、随机 IV、服务地址 AAD 与密文文件隔离。不回退为明文。损坏和密钥失效报安全错误，保留已有文件。
- `TransportTest.kt`：本机真实 TLS 连接、请求格式、SSE 中文、畸形/超限/中断、重定向、超时与取消测试。
- `CredentialCipherTest.kt`：真实 JVM AES-GCM 往返、随机性、错误密钥/服务地址/篡改拒绝，以及服务地址规范化。
- `ChatStoresDeviceTest.kt`：真实 Android Keystore、AtomicFile 与损坏配置保留的插桩测试；需设备执行。
- `THIRD_PARTY_NOTICES.md`：新增运行依赖的来源与 Apache-2.0 声明，随既有资源导出流程加入 APK。
- `docs/ANDROID.md`、`INDEX.md`、本报告：更新实现状态、入口和证据。

新增文件的行数与已有文件增删记录在本机 `/workspace/android-batch2/changes.txt`。
未修改原有动画、碰撞、桌宠偏好或 UI。未发布、推送或创建提交。

## 性能分析

网络读取限制单行/单 SSE 事件 64 KiB，流使用有界通道，慢消费者产生背压。
生产网络连接由 OkHttp 调度，取消会调用 Call.cancel，所有响应均 use 关闭；底层客户端共享连接池。
配置和凭据磁盘 I/O 在 Dispatchers.IO 执行，实例内 Mutex 串行；应用应复用单实例。
没有逐帧回调、主动轮询或常驻模型请求。首次实际接入会话后才产生网络和存储负载。

本机真实 TLS/AES 测试的样本数和耗时见 `/workspace/android-batch2/results.json`（来自当前 JUnit XML）。
这是 Linux/JDK17 数据，不是手机帧率、内存、后台耗电或温升结论；未做这些性能测量。

## 实机运行记录

本机 Gradle/JVM 实际执行 HTTPS 测试服务器与 TLS 校验，测试证书仅由注入的测试客户端信任，未关闭证书或主机名验证。
服务器使用本机回环地址，不生成用户预览链接；请求只使用假密钥。
设备列表为空，Android Keystore 与配置落盘插桩测试仅编译，未运行。
没有可用模型凭据的联调记录，未向外部模型发起请求；因此不声称真实服务商接口已验证。

## 测试与验证

完整命令：`bash /workspace/android-batch1/gradle.sh testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`。
保留初次/隔离失败日志与最终结果于 `/workspace/android-batch2/`。
取消测试的节流夹具曾无法及时交付首条事件，排查记录分别为 focused、stream-check、stream-fixed、http1-check 和 buffer-check 日志。
最终结果见该目录的验证日志与 results.json；失败或未运行项目不算通过。

## 后续与限制

下一批为会话协调、上下文和历史保存，须用户确认后开始。
本批尚未有设置界面可输入凭据；稍后在应用内安全输入，不能在聊天中提供密钥。
Android 厂商 Keystore 行为、损坏恢复、设备端连接取消以及真实模型兼容仍需设备/凭据验收。
Keystore 不可用或密钥失效时拒绝保存/读取，不静默降级；没有自动销毁所有服务密钥的恢复操作。
存储的多实例并发不属于支持用法，各 store 应为应用级单实例。

## 本次最终结果

- Android 全量 116 项通过，0 失败/错误/跳过，含本批 12 项新增 JVM 测试。
- Python/文档相关检查 265 项通过。
- lint 0 错误、14 条警告、7 条提示，与批次 1 相同。
- 应用与插桩 APK 编译通过（delivery.log，1 分 14 秒），应用 APK 签名验证通过；插桩未运行。
- APK 166,852,609 字节，SHA-256 `1e9359007943d0e8619dd4fea9c92c966f70b2c354de345dce2108025645ad18`。
- 8 项网络/SSE 测试及 4 项加密/配置测试的耗时分别见 results.json 的 new_suites；为真实本机 TLS/AES 执行，不是外部服务联调。
- 临时探针证明首增量已送达，最后一项失败由测试服务器十秒节流队列阻塞关闭引起；最终使用一秒节流并保留取消及 HTTP callEnd/callFailed 同步断言。字符缓冲替换本身未解决所有失败，不据此认定它是唯一根因。
