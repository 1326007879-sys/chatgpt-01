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
