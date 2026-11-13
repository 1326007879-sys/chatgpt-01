# 语音与提醒：验收及发行准备

日期：2026-11-13（北京时间）。仅准备，不公开发布、不合并main、不更新云环境。

## 已确认的音色模板

用户已试听认可qwen-designed-candidate.wav，可作后续音色模板。SHA256：388c1e5cce6eb40c0d299b41df19bf015fa3c41f0ad062641b1eaef7fe86a252。

认可仅针对Qwen生成的4.88秒参考音频。此前ZipVoice从该参考生成的未见文字输出，以及安卓实际输出，都未获得相同听感确认；不得用参考试听替代交叉引擎验收。旧小雅变调与旧真人参考样例继续不使用。

## 已做的检查

- 环境恢复且源码、模型和APK仍在。/dev/kvm仍不存在，不因工作区恢复就认定设备故障已解决；不重复同配置失败启动来充当验收。
- 代码检查沿用3585483本轮构建：197项JVM、13项Python、ruff、宿主和引擎Lint以及应用/设备测试APK构建通过。本轮只新增文档和本地验收资料，没有改应用代码，不重复无变化测试。
- 再核对四包签名一致，宿主包名development、引擎固定包名voice，两套instrumentation包名正确。
- 引擎APK ZIP 16KiB页对齐检查通过；arm64-v8a与x86_64共8个原生库ELF LOAD对齐检查也通过，真正16KiB页设备行为仍待验收。静态对齐通过不能代替设备验收。
- 本地验收目录提供APK、源码说明、SHA256及手册；不包含模型权重、未核清音色包或密钥，也不上传GitHub。

## 资源许可核查结果

| 对象 | 取得的依据 | 发行状态 |
|---|---|---|
| Qwen VoiceDesign原始权重 | 固定revision 5ecdb67327fd37bb2e042aab12ff7391903235d3模型卡标Apache-2.0 | 仅云端生成参考，不装入APK；生成输出与完整条款仍分别核对 |
| ZipVoice代码 | k2-fsa/ZipVoice官方LICENSE为Apache-2.0 | 代码声明已取得，不能据此替代权重授权 |
| ZipVoice官方权重 | k2-fsa/ZipVoice模型仓库revision 889dd609a42f0de9cf9aac335eae42677becb1b9，API未给license字段，文件清单未见LICENSE/NOTICE | 未取得明确完整分发依据，继续阻塞模型公开分发 |
| 当前int8 ONNX转换件 | 来源为sherpa-onnx官方tts-models发布附件；本轮推测的HF同名仓库返回401，没有取得该转换件专属模型卡 | 不能据此推断需登录或授权；需落实转换件到原权重的版本与许可链 |
| Vocos | charactr/vocos-mel-24khz，revision 0feb3fdd929bcd6649e0e7c5a688cf7dd012ef21模型卡标MIT | 还需当前ONNX文件与该权重转换来源对应关系及许可副本 |
| 中英文词典 | 引擎资源包固定文件集合 | 来源、生成工具与分发声明仍待补齐 |
| sherpa-onnx 2.0.1原生AAR | 官方固定SHA，APK内已有sherpa Apache-2.0、ORT MIT主要文本 | 静态依赖及完整第三方notice审查未完，不能标成全部核清 |

读取来源：
- https://github.com/k2-fsa/ZipVoice/blob/master/LICENSE
- https://huggingface.co/k2-fsa/ZipVoice/tree/889dd609a42f0de9cf9aac335eae42677becb1b9
- https://huggingface.co/charactr/vocos-mel-24khz/tree/0feb3fdd929bcd6649e0e7c5a688cf7dd012ef21
- https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1

证据下载与文件哈希保存在/workspace/work/voice-acceptance/license-evidence。部分来源明确标注许可，仅代表对应对象；没有发现权重许可不是断言“不允许使用”，而是目前证据不足以声明可公开分发。未向上游发送消息或擅自申请授权。

## 待发布内容草案（未定版号）

拟新增：可选独立离线语音引擎、共享模型和小型音色包、聊天/提醒选择离线朗读、提醒诊断时间实时更新。主应用不重复内置模型；默认不自动启用、不静默换声。

已知限制必须写入将来Release：原提醒故障未定位；设备验收未通过；单次引擎200 UTF-16字符（宿主分段）；独立进程超时180秒；需要用户安装引擎并导入兼容资源；听感与性能依设备而异。未完成的项目不写成“已修复”或“已支持全部机型”。

正式预览发行身份与签名尚未最终选择。当前验收包仍为preview.5基线的development身份，不改名冒充preview6。下一发行应决定继续沿用预览5身份以升级，还是使用新预览身份并行安装；前者须保有相同签名及更高versionCode，后者须同步引擎宿主白名单，并说明数据不迁移。当前原研究版引擎使用本地debug签名，另一CI机器生成的debug签名不能假定相同。

已有release-preview5.yml绑定旧SOURCE_SHA和已发布Release，不复用、不触发、不修改已发布附件。将来为本轮建立独立的构建/验签/附件清单及人工发布流程。

## 发布门槛

1. 按VOICE_ACCEPTANCE.md取得设备结果；修复并复测任何实际失败，特别是原提醒问题。
2. 用户确认安卓新句子音色；真机补齐延迟、RAM与连续使用；停止、重入和故障恢复通过。
3. 资源与原生依赖分发依据齐全，否则不公开相应资源或研究引擎。
4. 最终包名、版本号、签名、升级与授权配对验收完成；从最终提交重新构建发行附件，不能用开发包代替。
5. README、变更说明、安装手册、已知问题、源码与附件哈希相互一致，然后提交用户审阅发布结果。

当前停在设备验收和许可证据门槛，没有公开新版本。
