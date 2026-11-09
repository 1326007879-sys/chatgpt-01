# 独立APK插件原型：协议v1

本轮授权：开放实际插件管理入口，制作本地便签参考插件及开发故障插件。不发布新版本。
已发布preview.3仍不含本轮插件运行能力；需要本轮开发版主应用及单独的插件APK。

## 实现形式

- 主应用、便签插件、故障插件是三个独立APK，不共享UID，不加载第三方DEX到主应用。
- 插件服务使用Android Messenger/Binder，在独立`:worker`进程运行；数据由插件保存在自己的私有目录。
- 主应用发现声明`io.github.chatgpt01.petai.PLUGIN_SERVICE`的服务，读取其JSON声明，动态生成操作和输入界面。
- 新插件只要遵守同一协议和受支持的权限范围，不需要修改主应用中的便签专用代码。
- 第一版只做手动调用；没有聊天工具自动调用、历史读取、模型密钥访问、记忆提炼或网络服务。

## 构建和安装

```sh
cd android
bash gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest \
  :plugin-notes:testDebugUnitTest :plugin-notes:lintDebug :plugin-notes:assembleDebug \
  :plugin-fault:lintDebug :plugin-fault:assembleDebug -PpetPython=/你的构建Python路径
```

生成位置：

- 主应用：`android/app/build/outputs/apk/debug/app-debug.apk`。
- 本地便签：`android/plugin-notes/build/outputs/apk/debug/plugin-notes-debug.apk`。
- 故障样例：`android/plugin-fault/build/outputs/apk/debug/plugin-fault-debug.apk`。

安装流程：

1. 通过系统安装本轮主应用APK。当前普通开发包名为`io.github.chatgpt01.petai`，与已发布preview3独立；不同签名不能覆盖升级，不要删除有重要数据的旧应用。
2. 在“插件管理→安装插件APK”选择示例APK，按系统提示允许安装来源。也可以使用系统文件管理器安装；始终由系统确认安装，不支持静默安装。
3. 打开“桌宠插件·本地便签”，在独立授权页允许本轮桌宠访问。授权绑定包名和签名证书，使用实际Binder调用者UID检查，不能用请求正文冒充调用者。
4. 回到桌宠刷新，点击“启用插件”，核对范围并同意，再点击“打开功能”。
5. 保存便签、搜索（空关键词显示列表，修改页码翻页）、选择记录编号、删除指定便签。
6. 停用后主应用不能继续调用；卸载通过系统确认，插件自己的数据会删除，主应用聊天数据保留。取消卸载后该APK的所有插件服务保持停用。

故障插件同样先在独立授权页允许桌宠。它的操作包括回声、超时、模拟异常返回、退出插件进程和跨UID越权探测。仅供测试设备使用。
跨UID探测只尝试查询便签服务并报告拒绝/放行状态，不向调用方返回便签内容。

## 协议边界

共享协议源码：`android/plugin-protocol/src/main/kotlin`，SDK服务和授权页：`android/plugin-runtime/src/main/kotlin`。
源码分别编译进各自APK，不依赖运行时共享类加载器；通信只传JSON字符串。

插件Manifest声明导出的服务和JSON资源：

```xml
<service android:name=".YourService" android:exported="true" android:process=":worker">
  <intent-filter><action android:name="io.github.chatgpt01.petai.PLUGIN_SERVICE"/></intent-filter>
  <meta-data android:name="pet.plugin.descriptor" android:resource="@raw/descriptor"/>
</service>
```

描述字段：`id`、`name/nameEn`、`version`、`protocol`、`permissions`和`operations`。
每个操作有`id`、中英文标题和若干字符串输入字段；完整示例见便签和故障模块的`res/raw/descriptor.json`。
主应用当前支持`storage.private`（插件自己的数据）与`diagnostics`（手动诊断），其他数据范围拒绝启用。
首版还会拒绝插件APK声明的额外系统权限，示例不申请联网、录音、联系人或共享存储权限。云插件需要后续明确扩展授权设计。

通信：

- request：`Message.what=1`，`Bundle["json"]`含`protocol=1`、唯一`id`、`operation`、字符串键值`params`及`language`，`replyTo`为本次回复Messenger。
- response：`Message.what=2`，相同`id`和协议，`ok=true`时有`result`，失败时有受限的错误码。
- `result`只支持文字`message`和记录数组`items`，记录含`id/text`；点击记录编号会填入操作中的`id`字段。
- 主应用验证回复的实际UID、请求编号、协议及结构。取消、超时、失联后解绑，迟到或重复回复不会写回当前界面。每次调用使用独立回复线程解析未信任数据，结束时关闭回复线程，不把插件消息队列挂到界面主线程。
- 单次超时3秒，无自动重试。取消或超时不代表插件一定没有完成写入，重试写入前应先查询。
- 请求/回复最多32 KiB；声明最多16个操作、每个最多4个字段；输入最多1000字符；结果最多20条，文本和编号也有上限。

## 信任与数据

- 主应用保存按服务组件、APK版本、安装/更新时间和签名生成的授权指纹；更新、重装或声明不再符合规则时需要重新启用。
- 插件自己的授权页发现声明`io.github.chatgpt01.petai.PLUGIN_HOST`的主应用，显示包名和签名摘要；再次核对签名后保存允许列表。
- SDK服务逐次检查真实发送UID所对应的已授权包名与签名；即便两个示例APK用相同Debug证书签名，UID不同也不能越权读取便签。
- 用户只把手动填写的数据传入插件。本轮没有任何获取主应用聊天、模型设置、密钥或文件的插件接口。
- 独立APK受Android UID沙箱隔离；这并不把任意第三方APK自动变成可信应用，插件还需要自身的正确实现及用户选择。
- 便签最多200条，每条1000字符，每页10条；原文以JSON保存在插件私有目录，未做应用层加密。采用按文件共享锁、临时文件、fsync及原子替换，避免取消/服务重建时丢失并发写入。损坏文件不会被静默清空。
- 主应用插件页跟随应用语言，独立插件授权页跟随系统语言；插件自己的文件随系统卸载删除。

## 测试

JVM覆盖协议、伪造UID、未知权限/版本、超限/畸形结果、取消与迟到回复的事件竞争、便签持久化、损坏保护和多实例并发写入。
设备测试`PluginIpcDeviceTest`要求先安装并在两个插件中授权本轮主应用，执行便签存查删、跨UID拒绝、异常、超时、进程退出及后续恢复；只创建并删除带随机编号的测试便签。

## 本轮验证结果

- 主应用166项JVM、便签插件3项JVM、原项目13项Python测试通过；所有模块lint为0错误。
- 主应用、便签、故障APK与主应用设备测试APK全部构建成功；协议事件竞争和便签多实例并发在模拟器CPU负载下重复3轮通过。
- 便签和故障APK分别约0.93 MiB；没有增加动画内置数量。
- 专用官方API35软件模拟器无KVM。首轮安装发生包管理器Broken pipe、系统组件DeadSystemException；系统恢复后确认三个APK与测试包均已安装。
- 插件授权页面的UI树被系统“System UI isn't responding”弹窗覆盖，真实授权和Binder端到端测试未执行完成，不能标记验收通过；专用模拟器已关闭。
- 尚需在稳定硬件加速模拟器或真机执行PluginIpcDeviceTest，并补验系统安装/卸载、授权撤销、插件页实际操作、故障后聊天仍可使用。
- 开发产物、校验值与构建/重复测试记录在云工作区`/workspace/deliverables/plugin-prototype/`；模拟器日志在`/workspace/plugin-acceptance/`。

当前仅为本地开发原型，未推送、未发布；GitHub的preview.3没有这些功能。
