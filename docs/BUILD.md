# 构建与检查

最低 Android 8.0/API26；编译/目标 SDK36，Build Tools35.0.0，JDK17、Gradle8.11.1（仓库 wrapper 校验 SHA256），Python3.12。
SDK 用 Android Studio 或官方命令行工具安装，配置 ANDROID_HOME/JAVA_HOME；接受官方 SDK 许可。

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements-dev.txt
.venv/bin/python -m pytest -q
.venv/bin/python -m ruff check scripts tools tests
cd android
bash gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest -PpetPython="$PWD/../.venv/bin/python"
```

Windows 使用 `.venv/Scripts/python.exe` 和 `gradlew.bat`，向 petPython 传解释器绝对路径；macOS/Linux 使用上述入口。
APK 位于 android/app/build/outputs/apk/debug/；设备测试包位于 outputs/apk/androidTest/debug/。生成输出均不入 Git。

首次构建会将源 WebM 转成无损透明帧，可能耗时数分钟；缓存 assets/characters/shenshen/frameseq 被忽略，可安全重建。
Python/Qt/ffmpeg 仅在构建机使用，APK 不包含它们。所有构建依赖与素材均来自本仓库及声明的包源，不读取旧桌面仓库。
不需要模型密钥来编译或运行单元测试；真实模型联调需在手机内填写自己的兼容服务配置。

云任务使用已有检出，不另建 worktree。平台当前安装/网络/凭据设置不由本文件自动替换。

## 独立安装的预览验收包

发布v0.1.0-preview.2时额外传入 `-PpetPreviewInstall=true`：包名为 `io.github.chatgpt01.petai.preview`，启动器显示“AI 桌宠·预览”，与旧预览版独立安装，数据和密钥不自动迁移。普通开发构建仍使用原项目包名。

版本号0.1.0-preview.2/code2。当前仍为Debug签名，不承诺不同CI构建之间的覆盖升级；不要为安装测试删除用户的旧版数据。发布工作流先构建、校验附件，再公开预发布；不会修改main或平台云环境。

## 动画分批构建

默认仅包含11段核心动画。初次转换所有素材耗时较长，完整APK和安装占用会显著高于preview.2；以本轮构建记录实测为准。可添加Gradle参数 `-PpetAnimationBatch=0`（原核心11段，默认）、`=1`（核心加12段常用，共23段）、`=2`（再加77段随机，共100段）、`=3`（再加6段余额动画，共106段）。

分批参数用于构建与验收，不是应用内下载功能。切换批次会重新导出，并清理上一批次的多余帧目录，避免小包夹带大包素材。所有构建均从独立项目内的源文件生成，不读取原作者工作目录或联网下载素材。转换器最多并行3个独立进程，各自只写自己的动画缓存；CI冷构建超时上限调整为60分钟。

独立动作包通过 [ANIMATION_PACKS.md](ANIMATION_PACKS.md) 中的离线命令生成，不放入默认APK。

## v0.1.0-preview.3发行候选

本版使用 `-PpetPreviewInstall=true`，包名为 `io.github.chatgpt01.petai.preview3`，启动器名称“AI 桌宠·预览3”，版本0.1.0-preview.3/code3。该参数在历史preview.2标签下对应旧包名；以各标签源码为准。

本版与旧包并存，不要求卸载或自动迁移数据。仍为Debug签名，后续稳定签名及升级方案单独处理。默认动画批次0，仅内置11段；95个单动作包独立上传Release。

## 插件原型

独立模块plugin-notes和plugin-fault生成各自APK；共享协议/SDK只在构建时复用源码。构建与设备验证步骤见 [PLUGIN_PROTOCOL.md](PLUGIN_PROTOCOL.md)。

## 未发布的提醒/动画开发构建

追加 `-PpetDevelopmentInstall=true` 可构建独立包名 `io.github.chatgpt01.petai.development`，名称“AI 桌宠·开发测试”，不覆盖预览4。数据、密钥和插件配对不自动迁移。此属性优先于petPreviewInstall，仅供本地测试，不能据此声称新Release已发布；版本号仍为当前开发基线。

开发版新增通知、重启恢复、可选精确闹钟和前台语音播放能力，真实行为需要Android设备验收。
