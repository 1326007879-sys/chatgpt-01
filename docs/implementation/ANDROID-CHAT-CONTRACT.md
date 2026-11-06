# Android 文字聊天接口契约

基线：2026-11-05，Android 0.2.1，第一阶段批次 1。现有功能与构建入口见
[Android 说明](ANDROID.md)，本批验证见 [交付报告](PR-REPORT-ANDROID-CHAT-CONTRACT-2026-11-05.md)。
本文件约定模块如何组合。批次 2 已实现模型传输与安全配置；批次 3 已实现会话协调和历史保存；批次 4 已实现独立聊天页面，批次 5 已接入主界面和桌宠长按菜单入口。

## 范围与模块所有权

第一阶段新增主宠的一个默认会话，聊天使用应用内页面。保留已有多宠，暂不为伙伴创建聊天身份。
角色身份用稳定 `characterId`，不以显示名为存储键；`session.id` 由会话模块生成，不能直接当任意文件路径使用。

| 边界 | 本批类型 | 后续实现负责什么 | 不持有什么 |
|---|---|---|---|
| 数据 | `ChatMessage`、`ChatSession`、`ChatRequest` | 会话快照与版本校验 | Android View、播放器、网络连接 |
| 模型适配 | `ChatTransport`、`GenerationEvent` | HTTPS、SSE、错误分类、关闭连接 | UI、磁盘历史、宠物动画 |
| 非敏感配置 | `ChatModelConfig`、`ChatSettingsStore` | 独立聊天配置文件/偏好 | API Key、原有 `PetPreferences` 键 |
| 凭据 | `ApiKey`、`ChatCredentialStore` | Android Keystore 保护的密文存储 | 明文偏好、会话 JSON、日志 |
| 历史 | `ChatSessionStore` | 应用私有目录中的原子保存/恢复 | API Key、网络请求对象 |
| 会话协调 | `ChatConversation`、`ConversationState` | 单次生成、上下文、重试、持久化、UI 状态 | 悬浮窗、动画解码器 |
| UI 与桌宠接入 | 消费 `ChatConversation` | 展示、输入、长按打开页面 | 直接调用 HTTP 或读写密钥 |

代码统一位于 `io.github.dshpet.mobile.chat`。后续按 `transport`、`storage`、`conversation`、`ui`
新增子包；保持本批公共类型的定义位置。修改接口应同时更新本文件与契约测试，避免各模块复制一套模型。

## 消息与会话

- `ChatMessage`：`id`、`role`、原始 `content`、毫秒时间戳、`status` 和结构化 `failure`。
  只有 FAILED 消息携带错误分类与重试策略，便于重开应用后恢复；消息 ID 稳定，时间戳不作为去重键或请求身份。
- 角色仅 `USER`、`ASSISTANT`；system prompt 属于单次请求，不伪装成可见消息。
- 用户消息必须完整且非空；助手在等待/流式过程中允许空文本。完整回复必须含非空白文本。
- 状态为 `REQUESTING`、`STREAMING`、`COMPLETED`、`CANCELLED`、`FAILED`、`INTERRUPTED`。
  停止、失败和进程中断均保留已有文字，不能伪装成完整回复。
- `ChatSession.schemaVersion=1`，消息按会话顺序保存；枚举保存名称，禁止保存 ordinal。
  同一会话拒绝重复消息 ID；未知版本不当作空会话覆盖。
- 所有 `List` 为调用方持有的只读快照：从可变集合构造时先复制，创建请求后不再修改其列表。

## 请求与配置

`ChatRequest` 包含唯一 `requestId`、非敏感配置快照、system prompt 和选取后的完整上下文。
上下文必须以完整用户消息结束，拒绝重复 ID 和未完成回复。
批次 3 负责上下文裁剪、遗漏未完成回复及清理其配对关系，不能简单把历史全部送给网络层。

`baseUrl` 表示 API 根，例如 `https://host/v1` 或 `https://host/custom/v1/`。
模型适配器去掉末尾斜杠后追加 `/chat/completions`，保留已有路径；设置页应明确不是填写完整接口路径。
仅接受 HTTPS，禁止 URL 用户信息、查询参数和 fragment，凭据只能走受控 Authorization 请求头。
配置校验失败的错误文字不回显用户粘贴的 URL。

默认 temperature=1、maxOutputTokens=1024、timeoutSeconds=60；温度限定 0–2，
token 上限必须为正，超时为整个 HTTP 调用预算（1–300 秒，含流式读取）。非法值报错，不静默改写。
不同模型支持的参数由适配器处理，不能假设任意服务都完全兼容。

`ApiKey` 与配置、请求、历史均分离；`toString()` 脱敏，仅凭据存储和网络适配器调用 `reveal()`。
这不表示 String 已安全擦除；禁止记录请求、响应正文、消息或密钥。批次 2 才实现加密存储，
本批接口声明不等于已经具备密钥落盘能力。HTTPS 重定向不能把 Authorization 转发到其他来源。
凭据存取显式传入配置，以规范化 API 根为隔离键（域名大小写与默认 443 端口归一化、
去掉末尾斜杠，保留路径大小写）；模型切换不改变凭据归属，换服务地址不能自动沿用旧服务密钥。

## 流式与生成状态

`ChatTransport.stream` 返回 cold Flow，每次收集只执行一条请求：零到多条 `Delta`，
随后恰好一条 `Completed` 或 `Failed`。每条事件都携带原 `requestId`。
SSE 的成功结束必须有协议结束信号（如 `[DONE]` 或已确认的完成字段）；无完成信号就 EOF
属于 `INVALID_RESPONSE`。空白回复即使收到结束事件，也转换为 `EMPTY_RESPONSE`。

`GenerationState` 的转换：

```text
REQUESTING → STREAMING → COMPLETED
     └────────┴───────→ CANCELLED / FAILED / INTERRUPTED
```

空增量不改变状态；requestId 不匹配、或已经进入终态的所有事件均丢弃。
`ChatFailure` 只包含 `FailureCode` 和 `retryable`，UI 自行映射本地化提示。
HTTP/异常正文不能塞进错误对象。

## 并发、停止、重试与生命周期

- 一个会话最多一个生成任务。`send` / `retry` 返回接收结果后在会话拥有的协程中生成，
  不让 UI 等待完整回答。忙碌时返回 `BUSY`，不偷偷发起第二条请求。
- `ConversationState` 一次发布历史与生成状态；更新同一助手消息，不在 UI 中重复拼接回复。
- `stop(requestId)` 先更新可见状态，再取消对应任务；Flow 的取消必须关闭连接并传播
  `CancellationException`，不转成 NETWORK。纯状态方法本身不会关闭网络。
- 重试仅允许最近一个可重试的失败/停止/中断回答；复用其用户消息和助手消息 ID，生成全新
  requestId，以重试内容替换旧内容，不重复添加用户轮次。较早消息及完整回复返回 `INVALID_RETRY`。
- 页面旋转重建不应重发请求；普通离开页面不自动停止，会话持有者与页面生命周期分离。
  进程死亡后恢复的 REQUESTING/STREAMING 消息标为 INTERRUPTED，保留部分文本，不自动消耗额度重发。
- UI/会话状态修改按单一顺序串行执行；HTTP 和磁盘 I/O 不阻塞主线程。

## 存储约定

批次 3 实现 `ChatSessionStore`：原子替换整个快照，串行写入，流式中间写入合并，终态及时落盘。
先可靠保存用户轮次再发请求；无法保存时返回 `STORAGE_UNAVAILABLE`，保留输入让用户重试。
`load=null` 只表示文件不存在。损坏、未知 schema、文件身份不匹配和 I/O 失败必须可见，保留原文件，不能静默覆盖。
用户会话 ID 要映射成安全文件名，拒绝路径穿越。聊天配置、凭据、历史分别存储。

## 分批集成与验证

1. 批次 2 只新增网络与凭据/配置实现；统一在该批修改依赖与 INTERNET 权限，测试取消、异常和流解析。
2. 批次 3 新增会话协调与存储，验证重复发送、旧事件、恢复、重试和写入失败。
3. 批次 4 新增聊天页面；批次 5 再统一修改 MainActivity 和悬浮菜单入口。
4. `PetEngine`、`FrameLoader`、碰撞与音效不引用 chat，聊天不驱动原有帧回调。

本批聚焦命令：`testDebugUnitTest --tests 'io.github.dshpet.mobile.chat.*'`。
所有接口实现行为须在各自批次验证；本批的模型/状态测试不能替代真实联网、加密存储或真机验收。

## 批次 3 实现与接入说明

- `DefaultChatConversation.open` 先读取并校验身份；损坏/未知版本抛出读取错误，不能捕获后用空历史覆盖。
- 应用级 owner 负责协程生命周期，页面只订阅 StateFlow；旋转或普通离开页面不调用 close。
  `close` 是应用拥有者关闭命令，等待任务取消和最终保存。
- `ConversationState.storageError` 表示最近保存失败；已有文本保留在内存，下一次保存成功后消除提示。
- `INPUT_TOO_LONG` 表示输入超过 12,000 UTF-16 字符。上下文最多 12 个完整问答对及当前输入，总量
  12,000 字符，不含 system prompt；这是字符预算，不是 tokenizer 精确计数，超长 system prompt 由调用方控制。
- 单次输出超过 32,000 字符则停止并保留限额内文本，返回 INVALID_RESPONSE。历史文件上限 5 MiB。
- 会话文件目录由 Android 调用方传入 `context.noBackupFilesDir.toPath().resolve("chat/sessions")`。
  文件名为会话 ID 的 SHA-256；写入临时文件并 force，再 ATOMIC_MOVE 替换，不支持原子移动则保留原文件并报错。
- 流式增量最多每 500ms 保存一次，终态/停止/关闭保存最新快照；进程被强杀可能损失最近不足 500ms 的增量。
- 详见 [批次 3 报告](PR-REPORT-ANDROID-CHAT-SESSION-2026-11-05.md)。

## 批次 4 页面接入说明

`chat.ui.ChatActivity` 为 exported=false 的内部页面。通过用户操作显式 Intent 打开；普通退出和旋转不销毁会话。
`ChatRuntime` 只持有 applicationContext，应用级协程与会话/输入控制器按进程复用；进程死亡后未发送草稿不持久化。
`ChatScreen` 是可注入状态与回调的 Compose 组件，消息以可选择的纯文本展示，不执行 HTML，不提供 Markdown 排版。
`ChatInputController` 只在请求被接受且草稿未再次编辑时清空输入；拒绝、异常或期间重输同样内容均保留草稿。
`ModelSettingsDialog` 的密码不使用 rememberSaveable，旋转/关闭时清空未保存密钥；配置保存在独立后端。
保存按“先确保该服务的凭据可用，再更新所选配置”的顺序，两个 store 不是跨文件事务；配置保存失败时凭据可能已安全更新，UI 必须显示失败，不能冒充全部回滚。
当前 UI 为中文；真实设备上的键盘、滚动、深色模式和页面恢复仍需验收，详见 [批次 4 报告](PR-REPORT-ANDROID-CHAT-UI-2026-11-05.md)。

## 批次 5 入口规则

`ChatEntry` 统一创建 explicit Intent，不传消息、配置或密钥 extras；NEW_TASK 支持服务上下文，
REORDER_TO_FRONT/SINGLE_TOP 用于复用同一任务中的现有页面，不使用 CLEAR_TASK。
主界面入口独立于悬浮授权和桌宠运行状态，长按菜单从任意宠物打开主宠会话，名称明确标注“主宠”。
菜单关闭后才启动页面，不暂停或停止宠物。菜单以实测内容高度定位，超出安全显示区域时滚动。
入口逻辑与菜单布局已编译/回归；Android 后台启动限制、返回栈与长按手感仍需设备验收，见 [批次 5 报告](PR-REPORT-ANDROID-CHAT-ENTRY-2026-11-05.md)。
