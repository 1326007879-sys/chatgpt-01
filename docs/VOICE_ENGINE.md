# 独立离线音色引擎（研究版）

本模块是独立 APK，使用 sherpa-onnx 的 Android 原生 ZipVoice 推理，不在宿主进程加载原生模型，不将系统已有 TTS 音色包装成生成的新音色。Android TTS Binder/PCM 协议承担合成、排队和停止，不扩展原有仅文本便签插件协议 v1。

Qwen3-TTS VoiceDesign 用于云端研究试听和生成候选参考音频，其约 4.2 GiB 的原始权重不进入 APK，也不能直接作为本引擎模型导入。导入合成参考后，经另一个引擎生成的新句子未必与 Qwen 原音一致，必须分别试听验收。此前被否定的小雅变调和旧 ZipVoice 样例没有设为默认音色。

## 构建

先按 BUILD.md 准备 Python、JDK17、Android SDK36 和 Gradle8.11.1，再从仓库根目录运行：

```sh
python scripts/prepare_voice_engine.py runtime
cd android
bash gradlew :voice-engine:testDebugUnitTest :voice-engine:lintDebug :voice-engine:assembleDebug :voice-engine:assembleDebugAndroidTest
```

运行库固定为官方 sherpa-onnx 2.0.1 AAR，SHA256 为 `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`；下载器和构建前置任务都校验。AAR 不入 Git；不执行远程脚本，不修改下载域名或关闭 TLS。APK 含引擎代码和原生运行库，不包含语音模型或参考音频。支持官方 AAR 提供的 arm64-v8a、armeabi-v7a、x86、x86_64；具体设备仍需验收。

开发宿主与引擎使用同一调试签名，发布前必须制定正式签名和升级方案。原有用户安装包不需卸载。引擎 APK 包名 `io.github.chatgpt01.voice`，可与宿主并存；不是已发布的新预览版。

## 模型包和音色包

两种资源均为 ZIP，首项必须是 `manifest.json`，路径和文件集合固定；不能夹带代码、任意目录或外部路径。模型包最多 512 MiB，单个模型文件最多 256 MiB；参考音频仅接受 1–15 秒、单声道 24kHz PCM16 WAV。导入在临时目录中流式限额、校验 SHA256，全部通过后原子安装。已有同名资源不覆盖，失败不删除已安装资源。每个音色只有参考音频和元数据，共用一套底座模型。

资源清单 schema=1，engine=`sherpa-zipvoice-v1`，model=`zipvoice-distill-int8-v1`，kind 为 model 或 voice；另含 id、name、referenceText、license 和 files（文件名到 SHA256）。模型包含 encoder.int8.onnx、decoder.int8.onnx、vocos_24khz.onnx、tokens.txt、lexicon.txt、lexicon-en-us.txt。音色包含 reference.wav，必须提供准确的参考文字。

本地制作示例（路径自行替换，许可说明必须真实）：

```sh
python scripts/prepare_voice_engine.py model --model-dir /path/to/zipvoice --vocoder /path/to/vocos_24khz.onnx --english-lexicon /path/to/lexicon-en-us.txt --license '来源、使用和分发限制' --output /path/to/shared.petmodel
python scripts/prepare_voice_engine.py voice --reference /path/to/reference.wav --text '参考音频的准确台词' --id candidate-v1 --name '候选音色' --license '来源、授权和限制' --output /path/to/candidate.petvoice
```

制作包只记录许可说明，不认定或授予权利。当前研究模型的完整分发许可审查未完成，测试资源仅留本地研究；不上传公开 Release，不直接打进安装包。参考录音不能未经授权进入源码或公开包。引擎 APK 随附 sherpa-onnx 与 ONNX Runtime 主要许可文本；完整原生依赖清单与许可核验仍属于公开发布前工作。

## 使用

1. 安装同签名宿主和独立引擎，打开引擎应用。
2. 通过系统文件选择器导入共享模型包和音色包，选择音色，再手动启用。引擎没有联网权限，不自动下载或启用。
3. 在宿主“音色设置”选择独立离线引擎。管理音色后重新进入音色页刷新。缺模型、缺音色、停用、签名不一致时显示错误，不静默换成其他引擎声称成功。
4. 提醒页勾选朗读后，可勾选独立离线引擎；默认使用引擎中选定的音色。提醒通知与合成独立，语音失败时保留通知。锁屏、静音和勿扰限制继续生效。
5. 选择新音色会先停用引擎，需要再次手动启用；删除音色也会停用，避免继续使用已删除的参考。模型不按每个音色重复安装。

## 隔离、取消与限制

服务在独立 `:synthesis` 进程运行，平台 signature 权限与调用 UID 的宿主包名/签名检查同时约束调用。仅支持当前工程的同签名宿主；不是开放第三方语音市场。宿主只发送本次朗读的文字及所选音色，不发送模型密钥、完整聊天历史或参考音频。用户需显式启用引擎并在宿主选择。

单次合成最多 200 个 UTF-16 字符；宿主保留中文/emoji 完整性分段。PCM 分块传递，不通过 Binder 发送整段大音频。停止会取消当前播放和后续排队，并用任务代次忽略旧回调；原生计算在回调处响应停止，不能承诺即时释放 CPU。合成最长 180 秒，超时结束引擎合成进程；主应用仍保留文字和通知。宿主另有播放超时提示。

Linux 试听基准不能作为安卓性能数据；软件模拟器也不能作为手机发热、功耗或真实时延基准。公开发布前仍需实际 ARM 设备的冷启动、首句延迟、内存、长句、数字、多音字、停止/重入、进程死亡和连续使用验收。测试结果与未完成项见 implementation/VOICE-PLUGIN-RESUMPTION.md。

## 当前验收安排

用户已认可新Qwen参考可作音色模板，安卓实际ZipVoice输出仍需单独确认。安装/提醒/语音的验收步骤见[验收手册](VOICE_ACCEPTANCE.md)，资源许可与发行门槛见[发布准备记录](implementation/VOICE-RELEASE-PREPARATION.md)。本轮不自动发布，不改变当前公开预览5下载。
