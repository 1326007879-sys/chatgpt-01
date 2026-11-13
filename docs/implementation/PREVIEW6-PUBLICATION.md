# 预览6发布回执

用户已明确授权本次先发布、后下载安装测试。2026-11-13北京时间22:20公开为预发布，不是稳定版。

- Release：https://github.com/1326007879-sys/chatgpt-01/releases/tag/v0.1.0-preview.6
- 发布ID：444305907；公开状态draft=false、prerelease=true。
- 应用源码标签：v0.1.0-preview.6 → 3565e2a89464e62b8c2c955e3e5978d80bfeaff7。
- 独立功能分支：feat/voice-plugin-reminder-diagnostics；未合并main，未发布云环境，旧Release未改。
- CI：https://github.com/1326007879-sys/chatgpt-01/actions/runs/43440186963，全步骤成功。直接上传凭据返回401后，改用仓库Actions令牌构建并上传。
- 主APK：chatgpt01-petai-0.1.0-preview.6-debug.apk，162608001字节（155.08MiB）。
- SHA256：d0b6ff0cef6225c716e468d2a1dc9beb328b119f68164b9e8d4168f80c66b023。
- 包名io.github.chatgpt01.petai.preview6，code6，Android8.0+，CI Debug签名，与预览5并存；不自动迁移数据。

主APK及9份配套附件均由CI验证服务端大小与SHA256后公开。发布后重新从公开URL读取SOURCE.json、SHA256SUMS.txt，与GitHub资产digest交叉核对；APK公开HEAD返回200。没有重新下载整份APK进行设备安装，不能将公开下载检查当作设备验收。

本次197项JVM、13项Python、ruff、宿主/研究引擎Lint、APK和设备测试包构建通过。原提醒故障仍未定位，最终发行APK实际设备验收未完成。独立引擎APK、模型和音色包未公开分发，新卡通音色不能直接在本版体验；系统TTS仍可测试。资源许可和安卓效果继续收尾。
