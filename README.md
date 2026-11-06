# 安卓 AI 桌宠 · 云环境项目入口

本仓库保存项目关联信息，继续沿用已发布的云环境设置，不替换其安装脚本、启动说明、网络或凭据配置。

- 源码仓库：[dsh-pet-indesktop](https://github.com/1326007879-sys/dsh-pet-indesktop)
- 独立安卓开发分支：[feat/android-ai-phase1](https://github.com/1326007879-sys/dsh-pet-indesktop/tree/feat/android-ai-phase1)
- 已保存的开发提交：`ba61d893e6d8ea5d2e80c6edf3b5355b2aef4e45`
- 最初源码基线：`34d810b1eadd98d00e7cb9b3b51428a45b5bf03e`。源码仓库 main 保留溯源，本次不合并或推送 main。
- 机器可读映射：[project.json](project.json)

## 后续开发

先读取 project.json，再检查 `/workspace/dsh-pet-indesktop` 的分支和 git status；保留已有检出与未提交改动，不另建 worktree，也不直接重置或覆盖。
本仓库与源码仓库是同一项目的环境入口和实现；关联索引不自动执行安装或更改云环境配置。

## 当前状态

安卓第一阶段为开发测试版。构建、自动化和一项模拟器 Keystore 测试已有通过记录；界面设备验收及真实模型联调仍有未完成项目。
具体证据见开发分支的 docs/ANDROID.md 与 docs/PR-REPORT-ANDROID-CHAT-ACCEPTANCE-2026-11-05.md。
密钥仅在应用或安全配置界面输入，不保存到此仓库。
