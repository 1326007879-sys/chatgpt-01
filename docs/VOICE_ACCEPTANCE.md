# 离线语音与提醒验收手册

当前状态：代码检查完成，设备验收待做。用户于2026-11-13确认Qwen候选可作音色模板，不等于安卓实际声音已验收。请先使用独立开发测试包，保留已安装的预览5及其数据。

## 测试文件与安装

本轮本地验收目录：/workspace/deliverables/voice-acceptance。不是GitHub新版本，版本信息仍保留开发基线preview.5，不能当成preview6。

- host-development.apk：AI桌宠·开发测试，包名io.github.chatgpt01.petai.development。
- voice-engine-research.apk：桌宠离线音色·研究版，包名io.github.chatgpt01.voice。
- host-tests.apk、voice-engine-tests.apk：仅自动化设备测试使用，普通手动验收无需安装。
- SOURCE.json、APK_INVENTORY.json、SHA256SUMS.txt：源码状态、体积、签名和文件校验记录。

安装两个应用APK即可；保留预览5，不卸载、不清除数据，不在此阶段覆盖升级。开发包和预览5的数据、模型API配置、授权不会自动同步；先测提醒和语音无需填写API Key。

如果此前安装过同包名的研究/开发包且提示签名冲突，先停止，不为通过安装而卸载或清除旧数据。当前已核对开发宿主和引擎同签名，但不能保证与另一台机器生成的旧调试包同签名。

引擎包包含原生库，不含模型；没有导入模型和音色时不能离线合成。共享模型及音色研究资源尚未完成分发许可核验，不随公共发行包提供。本地研究文件位置见VOICE_ENGINE.md及发布准备记录；不要上传未核清的资源。

## 提醒：优先还原原故障

先在仍保留的预览5记录手机/模拟器型号、Android版本、通知权限和精确闹钟权限。原故障是已经选时间并保存，不能用“只在正文写时间”解释。

在新开发包执行下表；每例记录实际时间、屏幕状态、通知栏截图和提醒页诊断时间。使用不含隐私的测试事项。

| 场景 | 操作 | 通过标准 |
|---|---|---|
| 通知基础 | 允许通知及精确闹钟，建立2分钟后的文字提醒，暂不朗读 | 提醒被保存，接收时间和投递时间更新，通知栏有事项 |
| 退出页面 | 同样建立提醒，再回桌面 | 不依赖提醒页打开，仍有通知 |
| 精确权限关闭 | 关闭精确闹钟后重新保存新提醒 | 显示近似调度，最终有通知；允许系统延迟，记录等待时长 |
| 通知权限关闭 | 关闭通知权限再保存 | 明确提示，不误报投递成功；恢复授权后重新创建验证 |
| 编辑与取消 | 修改时间、取消另一事项 | 按新时间调度，已取消事项不再弹出 |
| 重启恢复 | 建立足够远的提醒后重启、解锁 | 未到期事项仍存在并能投递；记录开机/解锁时间 |
| 系统TTS | 系统引擎先试听，再为提醒勾选朗读 | 通知与语音均可验证；语音失败时保留通知并可见错误 |

“返回桌面/划走界面”与系统设置里的“强行停止”不同。强行停止会受到Android平台调度限制，不能据此要求到点必达。静音、勿扰、音量和厂商省电也需要记录，不擅自关闭用户设置。

诊断解释：无调度时间先查保存与授权；有调度无接收先查平台闹钟/后台条件；有接收无投递查通知与错误；有投递无声音再单独查TTS。诊断显示通知投递不等于用户已听见/看见，仍检查通知栏和频道。

## 离线音色：单独验收实际输出

1. 打开引擎，按VOICE_ENGINE.md导入共享模型与已授权音色包，选择后显式启用。
2. 宿主音色设置选择独立离线引擎，先试听，再测试聊天朗读和提醒朗读。
3. 使用参考音频中没有的文字：“现在是下午一点，记得带上小水壶哦！”、“明天早上七点半，我们一起去公园吧！”、“银行门口有一行小字，写着快乐出发。”
4. 对照已认可的Qwen参考，分别记录稚嫩感、清晰度、自然度、电音感、音色稳定；不因只生成了声音就判通过。
5. 测试朗读中停止、连续两次朗读、切换音色、删除所选音色、停用引擎。删除/停用后应明确报错，不自动换其他声音，文字和提醒通知仍可用。
6. 仅在专用测试设备结束引擎合成进程，验证宿主不崩溃、有可见错误，重新启用后能恢复。不要对日用设备执行破坏进程的自动化步骤。

记录冷启动至首音、暖启动至首音、完成耗时、连续10次是否错误；RAM和发热应以ARM真机为准。软件模拟器只可辅助检查安装/行为，不能代表手机功耗。

## 自动化设备用例（开发者）

仅连接专用测试设备，先用adb devices确认设备序列号。以下以SERIAL为占位符，替换后再执行；不要使用未限定设备的adb命令。

```sh
adb -s SERIAL install -r host-development.apk
adb -s SERIAL install -r host-tests.apk
adb -s SERIAL install -r voice-engine-research.apk
adb -s SERIAL install -r voice-engine-tests.apk
adb -s SERIAL shell pm grant io.github.chatgpt01.petai.development android.permission.POST_NOTIFICATIONS
adb -s SERIAL shell appops set io.github.chatgpt01.petai.development SCHEDULE_EXACT_ALARM allow
adb -s SERIAL shell am instrument -w -r -e class io.github.chatgpt01.petai.ReminderDeliveryDeviceTest io.github.chatgpt01.petai.development.test/androidx.test.runner.AndroidJUnitRunner
adb -s SERIAL shell appops set io.github.chatgpt01.petai.development SCHEDULE_EXACT_ALARM deny
adb -s SERIAL shell am instrument -w -r -e class 'io.github.chatgpt01.petai.ReminderDeliveryDeviceTest#inexactAlarmWakesReceiverWithoutExactPermission' io.github.chatgpt01.petai.development.test/androidx.test.runner.AndroidJUnitRunner
```

记录原appops状态，测试完成恢复。精确权限两种状态需分别跑；因Assume跳过的用例不能记为通过。API26–32没有POST_NOTIFICATIONS运行时权限，不执行对应授权行。无精确权限用例180秒超时仅为测试观察窗口，不能证明所有近似闹钟都应在180秒内到达。

语音用例先启动引擎以创建外部files目录，然后把已授权的共享模型及音色包放入/sdcard/Android/data/io.github.chatgpt01.voice/files。文件名和音色ID必须与实际manifest一致：

```sh
adb -s SERIAL shell am instrument -w -r -e class io.github.chatgpt01.voice.NeuralEngineDeviceTest -e modelFile shared-model.petmodel -e voiceFile qwen-candidate-v2.petvoice -e voiceId qwen-candidate-v2 -e keepOutput true io.github.chatgpt01.voice.test/androidx.test.runner.AndroidJUnitRunner
adb -s SERIAL pull /sdcard/Android/data/io.github.chatgpt01.voice/files/unseen-test.wav ./android-unseen-test.wav
```

资源缺失会跳过，不能计为合成成功。上述用例覆盖合成及停用后拒绝，不代替主动停止、进程死亡恢复、性能与人工听感验收。未成功生成输出时不要把先前文件当成本轮结果；运行前移走专用测试目录中的旧unseen-test.wav。

## 反馈格式

设备/Android版本：
安装结果与包名：
测试事项设置时间/实际等待时长：
通知/精确闹钟权限：
调度时间/接收时间/通知投递时间/错误提示：
通知栏是否出现：
系统TTS是否正常：
离线引擎是否正常、使用的模型与音色ID：
听感/延迟/连续使用问题：

不发送API Key、私人便签、完整聊天记录或未经筛选的全量logcat。
