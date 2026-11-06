# 来源与迁移范围

- 初始来源：https://github.com/1326007879-sys/dsh-pet-indesktop
- 溯源提交：34d810b1eadd98d00e7cb9b3b51428a45b5bf03e
- 安卓 AI 开发成果的本地提交：ba61d893e6d8ea5d2e80c6edf3b5355b2aef4e45
- 新项目仓库：https://github.com/1326007879-sys/chatgpt-01

本项目复制必要源码独立维护，不是 Git submodule，不通过符号链接读取旧仓库，也不向旧仓库推送。
只保留 Android 应用、构建用核心素材、测试及必要转换器；桌面 UI/服务、桌面打包工作流、Android 工具链和缓存不纳入新项目源码。

tools/asset_pipeline/frameseq.py 源自上游 pet/frameseq_provision.py，保留无损 v6 算法和构建互斥逻辑；唯一导入适配是将桌面 shutdown latch 替换为本地构建帮助函数。
CLI 构建没有桌面 GUI 会话结束事件，终止进程由普通进程信号处理。图标函数源自 scripts/make_icon.py；默认台词从 pet/config.py 提取为 JSON。
原始版权、MIT 许可及角色/音效声明保留在 LICENSE、THIRD_PARTY_NOTICES.md、android/NOTICE.txt。未改变素材许可。

历史开发记录在 implementation/，记录旧路径、旧基线和当时的测试结果，仅供溯源；当前构建规则以 BUILD.md 为准。

新应用身份为 io.github.chatgpt01.petai，版本0.1.0-preview.1/code1；Kotlin namespace亦独立重命名，不覆盖原应用或读写其私有数据。原版0.2.x到本项目不作覆盖升级；升级测试仅用于将来本项目同包名版本。
