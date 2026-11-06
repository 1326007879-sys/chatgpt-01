# Android 聊天入口接入 · 批次 5

日期：2026-11-05，分支 feat/android-ai-phase1；接续前四批的未提交工作，保留既有文件。
契约：[ANDROID-CHAT-CONTRACT.md](ANDROID-CHAT-CONTRACT.md)；安装与使用：[ANDROID.md](ANDROID.md)。
本批将现有独立页面连接到用户入口，尚不等于已在手机上完成真实模型联调。

## 修改文件说明

- 新增 `chat/ui/ChatEntry.kt`：两个入口统一使用显式 Intent，NEW_TASK/REORDER_TO_FRONT/SINGLE_TOP，不传敏感 extras，不清空任务；打开异常显示通用 Toast，不终止桌宠服务。
- `MainActivity.kt`：+7/−0；新增全宽聊天按钮与说明，位于桌宠启停按钮后且不受悬浮授权/运行状态限制。
- `PetOverlayService.kt`：+14/−4；长按菜单新增“和主宠聊天”；动作先关闭菜单再打开页面。用实际测量高度计算位置、ScrollView 限制为安全区域，避免多一项后底部操作溢出。
- `ChatActivity.kt`：仅更新页面注释；仍为 exported=false，应用级会话持有方式不变。
- 中文与英文 strings：各新增三个入口相关字符串。聊天页面本身仍为中文。
- 新增 `ChatEntryDeviceTest.kt`：设备测试覆盖私有显式目标、无 extras、主界面进入和返回不改变宠物状态、应用上下文重复打开复用页面。
- Android 说明、契约、索引和本报告更新；精确新增文件行数和累计差异见 `/workspace/android-batch5/changes.txt`。

未修改点击/拖动处理、行为引擎、动画解码或暂停/停止命令。没有提交、推送或环境发布。

## 交互规则

任意宠物菜单均明确写“主宠”，沿用第一阶段的一只主宠、一个默认会话，不假装每只小鲸鱼各有记忆。
主界面没有悬浮权限或桌宠未启动时仍能聊天。用户长按并选择聊天才打开页面，不后台自动弹出。
正常返回不主动停止回复；打开页面不发送模型请求，只有用户配置并发送后才请求服务。
系统若阻止后台 Activity 启动可能不抛异常，不能以 Intent 调用返回就声称页面已经显示；这条需设备验收。

## 性能分析

没有新增常驻服务、轮询、定时器、磁盘写入或自动网络请求。菜单仅在用户长按时测量一次并构造滚动容器；
启动页面复用前批应用级会话，不复制模型或历史存储。没有新增每帧计算。
本机测试样本数/耗时见 results.json；没有运行 Android 页面，未测实际启动延迟、长按性能或手机内存，不能以静态代码推断“无性能影响”。

## 实机运行记录

本机执行完整 Gradle/JVM/lint/APK 构建并检查打包内容。未连接手机或运行模拟器。
新增 3 项设备入口测试仅编译，未运行；实际长按菜单滚动、外部点击关闭、返回键、重复启动与混合任务栈都仍需设备验收。
实际设备测试前需安装 app 和 androidTest APK，并在专用测试环境授予悬浮权限验证菜单；主界面聊天不需要该权限。
未获取真实模型凭据或发起外部联调；历史和密钥实现限制延续前批。

## 测试与验证

命令：`bash /workspace/android-batch1/gradle.sh testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`。
日志与汇总位于 `/workspace/android-batch5/verified.log`、`results.json`；Python 相关门禁见 python.log。
没有新增 JVM 测试去重复断言简单按钮拼接，入口和返回行为留给真实 Android 插桩测试；全量已有会话/模型/物理测试仍回归。
未跑完整桌面 Python 套件：本批只修改原生安卓入口与菜单布局。没有新的真实时间/线程测试，无额外时序压力族。

## 下一步

等待用户确认批次 6，再进行集中验收、测试包交付与设备/服务联调。
本批“接入完成”表示源码与打包完成，不表示已经在手机上确认后台启动政策、键盘、旋转、耗电或扬声器。

## 本次最终结果

- Android 全量 135 项通过，0 失败/错误/跳过；本批新增 3 项设备入口测试已编译、未运行。
- Python/文档相关 271 项通过；lint 保持 0 错误、14 条警告、7 条提示。
- 应用及插桩 APK 构建通过，完整验证耗时 1 分 19 秒；应用签名校验通过。
- APK DEX 已含统一 ChatEntry；打包 Manifest 核对 ChatActivity 仍为 exported=false。
- APK 166,852,825 字节，SHA-256 `51657a3feb2e1e19ff2f3d314688f82006884b3b97610abc3b2e2cc594793904`。
- 没有实机启动耗时或菜单滚动性能数字；JVM 回归复用前批测试，不冒充新增入口的触摸验证。
