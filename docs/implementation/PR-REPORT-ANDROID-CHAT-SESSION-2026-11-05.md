# Android 会话管理与历史保存 · 批次 3

日期：2026-11-05；分支 feat/android-ai-phase1；接续批次 1/2 的工作区，保留此前改动。
契约与接入规则见 [ANDROID-CHAT-CONTRACT.md](ANDROID-CHAT-CONTRACT.md)，现有应用说明见 [ANDROID.md](ANDROID.md)。
本批尚未创建聊天页面，也未修改桌宠菜单或 Activity，不向外部模型自动发起请求。

## 修改文件说明

- 新增 `conversation/DefaultChatConversation.kt`：单请求协调、发送前保存、停止/重试、迟到事件隔离、终态保存、恢复和保存错误状态。
- 新增 `conversation/ContextWindow.kt`：以完整相邻问答为单位，选择最近最多 12 对及当前用户消息，排除未完成回复及其用户轮次。
- 新增 `storage/FileChatSessionStore.kt`：schema 校验、5 MiB 文件上限、SHA-256 文件名、临时文件 force 与原子替换，读取损坏时保留原件。
- 修改 `ChatPorts.kt`：ConversationState 新增 storageError，SendRejection 新增 INPUT_TOO_LONG，供下一批 UI 明确提示。
- 新增 `ConversationTest.kt`、`SessionStoreTest.kt`：会话状态组合、失败/恢复、写入合并与真实文件测试。
- 修改 `ANDROID.md`、`ANDROID-CHAT-CONTRACT.md`、`INDEX.md`，新增本报告：更新接入边界、预算和验证证据。

本批源文件行数/增删见本机 `/workspace/android-batch3/changes.txt`；此前两批未提交文件保持原样。
没有新增依赖、网络权限或第三方素材；没有提交、推送或发布。

## 实现与保护边界

请求必须先保存用户轮次与助手占位消息，再启动网络任务；保存失败时不发请求、保留旧会话和 UI 输入。
StateFlow 一次发布历史与生成状态。stop 同步标记 CANCELLED，再取消实际任务；新请求有新 requestId。
重试仅替换最近可重试的终态助手消息，复用用户和助手 ID，不复制用户提问。

磁盘写入串行执行，并在获取写锁后读取最新快照，避免旧任务 finally 覆盖新轮次。
流式写入按单调时钟合并为最多每 500ms 一次，终态和关闭保存最新文本。
恢复 REQUESTING/STREAMING 为 INTERRUPTED，不自动重发。会话拥有者在应用层，页面旋转仅重新订阅。

上下文限制 12,000 UTF-16 字符，不含 system prompt；这是字符预算，不是 tokenizer 计数。
单次输出上限 32,000 字符，超限终止并保留已有部分。输入超限拒绝发送，不静默截断。
文件写入不支持 ATOMIC_MOVE 时直接报错，不降级为覆盖旧文件。文件级 force 不等于整机掉电无损保证。

## 性能分析

真实执行环境：Linux 云机器、Temurin JDK17；JUnit XML 为本批实际测试耗时来源。
100 条增量的确定性测试使用固定单调时钟：完成前只写入一次占位快照，关闭时再保存最新文本。
这是写入合并的验证，不是手机每秒吞吐或 UI 流畅度测试。

会话新增受拥有者管理的协程；网络和磁盘仍通过已有传输与存储边界执行，未接入帧回调、动画或碰撞。
纯上下文按历史扫描并保留完整轮次；大历史与长回复会分配列表和字符串，未测手机长期内存/耗电，不声称零开销。
存储读取/写入与真实文件往返测试均实际运行；样本数与耗时、满载复跑命令见 results.json、stress.py 和 stress.log。

## 实机运行记录

本机实际运行真实文件创建、写入、force、原子替换与重开读取；路径穿越形式的会话 ID 仅生成目录内哈希文件。
损坏 JSON、未知/非整数 schema、身份错误及字段类型错误的读取均失败，原字节保留。
会话测试通过真实协程和可控制的网络边界验证组合，不将其称为真实模型服务联调。
没有连接安卓设备，因此未验证 Android 文件系统上的 ATOMIC_MOVE 或 UI/后台生命周期，也没有实际模型凭据请求记录。

## 测试与验证

- 聚焦：`gradle.sh testDebugUnitTest --tests '*ConversationTest' --tests '*SessionStoreTest'`，第一轮 9 项通过。
- 全量：`bash /workspace/android-batch1/gradle.sh testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`。
- 最终日志 `/workspace/android-batch3/verified.log`，结果汇总 `/workspace/android-batch3/results.json`。
- 新增 12 项 JVM 测试；停止/重试与写入失败等使用 Channel/StateFlow 同步，不用固定 sleep 猜测事件。
- 高负载：`python3 /workspace/android-batch3/stress.py`，4 个 CPU 繁忙进程下连续执行新测试族 3 次。
- Python 相关/文档：素材、验收脚本、许可和报告纪律；结果见 python.log。
- 未运行完整桌面 Python 套件：仅新增独立安卓会话逻辑，不修改桌面产品代码。设备测试仍未执行。

关闭与发送准备共享串行门；关闭先标记 INTERRUPTED，再等待任务和最终写入，避免尚未调度的任务漏掉 finally。

## 限制与下一步

尚无历史清理、分页或用户界面；5 MiB 上限后保存会报错，保留已有文件，未来 UI 需显示 storageError。
进程强杀可能丢失不足 500ms 的增量；原子写入确保文件替换边界，不保证突然断电绝对无丢失。
历史为应用私有目录明文数据，不含 API Key；卸载应用会删除本地历史。
本实例要求独占同一会话的写入；不支持多个协调器/进程同时编辑同一历史文件。
下一批为聊天界面，需用户确认后再开始；真实设备和外部模型联调仍待后续验收。

## 本次最终结果

- 安卓全量 128 项通过，0 失败/错误/跳过，含本批新增 12 项。
- Python/文档相关检查 267 项通过。
- lint 0 错误、14 条警告、7 条提示，与批次 2 一致。
- 应用及插桩 APK 构建通过，最终完整构建 1 分 19 秒；应用签名验证通过。
- 4 个 CPU 繁忙进程下，新测试族 12 项连续三遍全部通过，原始记录见 stress.log。
- 关闭竞态红记录见 close-race-red.xml；显式中断状态及串行关闭修复后最终回归通过。
- APK 166,852,667 字节，SHA-256 `af7cfc6ebd40d3f2d045ae19b3d537c2ee196b32a1062d88dc57ac6f9e4b7fa0`。
- 本机测试组 `io.github.dshpet.mobile.chat.storage.SessionStoreTest`：3 项，JUnit 实测 0.123 秒（非手机性能指标）。
- 本机测试组 `io.github.dshpet.mobile.chat.conversation.ConversationTest`：9 项，JUnit 实测 0.201 秒（非手机性能指标）。
