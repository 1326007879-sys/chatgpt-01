# Android 独立聊天界面 · 批次 4

日期 2026-11-05，分支 feat/android-ai-phase1；保留此前批次未提交改动。
依据 [聊天契约](ANDROID-CHAT-CONTRACT.md) 实现，现有应用说明见 [ANDROID.md](ANDROID.md)。
本批新增独立内部 Activity，尚不增加桌宠菜单或主界面入口，也不自动请求模型。

## 修改文件说明

- 新增 `chat/ui/ChatScreen.kt`：Material3 明暗主题、可选择纯文本消息、状态/错误提示、输入计数、发送/停止/重试、模型设置对话框；IME 与系统安全区域留白。
- 新增 `chat/ui/ChatActivity.kt`：内部 Activity 与 applicationContext 持有者；进程内复用会话与草稿，普通离开/旋转不重发或停止生成；读取错误保留文件并允许重试。
- 新增 `chat/ui/ChatControllers.kt`：可测试的输入与配置交互；防重复提交、拒绝保留输入、期间重新编辑检测、密钥分离和通用错误提示。
- 新增 `chat/ui/ChatControllersTest.kt`：7 项 JVM 交互测试。
- 新增 `ChatScreenDeviceTest.kt`：真实 Compose 语义/触摸测试，检查空输入、发送、停止和重试；当前仅编译，未在设备运行。
- 修改 Manifest：注册 exported=false 的 ChatActivity，不创建桌面快捷方式或对外导出。
- 修改 Gradle：加入 Compose 界面测试依赖与独立测试 BOM；测试宿主只用于 Debug，release 不依赖 ui-test-manifest。
- 修改 Android 说明、契约、索引，新增本报告。行数与增删记录于 `/workspace/android-batch4/changes.txt`。

没有修改 MainActivity、桌宠菜单、动画、碰撞或已有偏好。没有推送、提交或发布。

## 行为与数据保护

只有模型配置完整且该服务密钥存在时才保存为所选配置。换服务不自动复用原密钥；空密码保留当前服务已有密钥。
密码仅在当前对话框和短期保存任务中使用，不放入 ModelEditor、UI 恢复状态或会话历史，不回显原始异常。
保存与删除失败显示通用提示；两个存储不是跨文件事务，凭据可能已更新但所选配置未保存，此时仍显示失败。

发送等待期间的再次编辑通过草稿版本号保护，包含先清空再输入同样文字的情况；发送失败不清空草稿。
应用级持有者不保存 Activity 引用；普通页面离开后生成可继续，系统终止进程后按上一批 INTERRUPTED 规则恢复。
历史损坏不创建空会话覆盖原件。文字支持系统选择复制，不渲染 Markdown/HTML。

## 性能分析

StateFlow 驱动界面更新，LazyColumn 按稳定消息 ID 展示；用户向上浏览时不强制滚到底部。
没有新增轮询线程；页面离开后 Compose 订阅销毁，会话协程按既定拥有者继续。
界面仍可能因长文本排版产生开销，本机未运行 Android UI，不能声称实际滚动、内存、耗电或键盘性能已验证。
JVM 交互测试的样本数/执行耗时见 results.json；这仅衡量测试执行，不能当作手机 UI 性能。

## 实机运行记录

本机执行真实 Gradle 编译、JVM 测试和 APK 构建。没有连接 Android 手机或运行模拟器，未实际打开页面。
Compose 插桩测试只编译，不计为通过；没有截图、键盘、深色模式、TalkBack 或旋转的实机验收结论。
外部模型与 Android Keystore 的真机联调限制延续前批，未在聊天中收取密钥，也未自动发起请求。

## 测试与验证

命令：`bash /workspace/android-batch1/gradle.sh testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`。
初次插桩依赖失败是 ui-test-junit4 未继承版本 BOM，补上相同 Compose BOM 后重试，日志保留在 validation.log 与 verified.log。
JVM 新增 7 项交互测试，覆盖拒绝/成功发送、连点、重输、重试不消耗草稿、服务切换、密钥保留/删除、保存失败提示。
没有新增依靠真实时间的线程/Qt 时序测试；沿用前批会话回归。界面交互依赖设备的测试未运行。
完整桌面 Python 套件未运行，因为桌面产品逻辑未改；相关素材/许可/文档门禁另见 python.log。

## 下一步与设备验收

等待用户确认批次 5，才把主界面和桌宠长按菜单连接到内部 ChatActivity。
随后验证：首次配置、真实模型流式回复、停止/重试、返回后重新进入、旋转与进程重启、密钥切换、键盘遮挡和长历史滚动。
设备上可执行 `adb shell am instrument -w -e class io.github.dshpet.mobile.ChatScreenDeviceTest io.github.dshpet.mobile.test/androidx.test.runner.AndroidJUnitRunner`。
该 Activity 不对 shell 或其他应用导出；入口接入前不提供可点击启动按钮。
当前页面为中文，未对英文界面做本地化，未发送草稿不跨进程保存。

## 本次最终结果

- Android 全量 135 项通过，0 失败/错误/跳过，含本批新增 7 项 JVM 交互测试。
- 3 项 Compose 设备界面测试已编译，未运行；ADB 设备列表为空。
- Python/文档相关 269 项通过，lint 0 错误、14 条警告、7 条提示，未增加。
- 两类 APK 构建通过，完整验证耗时 1 分 17 秒；应用 APK 签名校验通过，新页面和控制器类已核对位于 DEX。
- APK 166,852,825 字节，SHA-256 `f36be692781db10d7ccd4feac2fea95df68d1f97f0a85dd18d26d7ceea6965c6`。
- 本机 ChatControllersTest：7 个样本测试总耗时 0.012 秒，不是 Android 绘制性能。
