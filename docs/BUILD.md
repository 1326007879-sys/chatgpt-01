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
