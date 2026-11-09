# v0.1.0-preview.3：核心安装包与独立动作包

这是供安装测试的预览版，真机完整流程和稳定性仍待验收。

- APK只内置11段核心动作，约155 MiB；其余95段改为可选的单动作包。
- 完善动画管理：本地导入、完整性/兼容性校验、容量显示、单个删除、重复安装保护。
- 语音输入改用手机键盘听写；默认中文，支持简体中文/English界面同步。
- 保留文字聊天、系统回复朗读及音色选择。插件管理仍是预备入口。

[下载APK](https://github.com/1326007879-sys/chatgpt-01/releases/download/v0.1.0-preview.3/chatgpt01-petai-0.1.0-preview.3-debug.apk)

[按中文名称选择动作包](https://github.com/1326007879-sys/chatgpt-01/blob/v0.1.0-preview.3/docs/releases/v0.1.0-preview.3/ANIMATIONS.md)

动作使用：先停止悬浮桌宠 → 动画管理 → 导入下载的单个`.petanim` → 重新启动 → 动作选择。

本版安装为“AI 桌宠·预览3”，与旧版并存，无需卸载旧版；旧数据和模型配置不自动迁移。

自动化验收：159项JVM、13项Python、lint及应用/设备测试包构建通过；素材读写与播放回归在CPU负载下重复3轮通过。
设备测试仅编译，不能代替真机验收；原有启动ANR尚未完成定位。详见INSTALL.md、KNOWN_ISSUES.md、SOURCE.json和SHA256SUMS.txt。

本版不含在线素材商店、合集安装、压缩实验、插件运行时或记忆系统。原有发布保留，未改动原作者仓库或云环境配置。
