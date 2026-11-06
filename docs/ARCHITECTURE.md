# 模块与依赖

运行时：MainActivity/长按菜单 → ChatEntry → ChatActivity → 应用级会话持有者 → ChatConversation。
会话通过 OpenAiTransport 请求 OpenAI 兼容 HTTPS/SSE 服务；配置和按服务隔离的密钥由 Android Keystore 支持；历史在私有目录原子保存。
动画/物理/声音与聊天模块分离，继续使用原生悬浮服务；只支持主宠的一个默认聊天会话。

构建时：Gradle → scripts/export_android_assets.py → tools/asset_pipeline → 本仓库 assets。
没有 pet/ 桌面包，也没有运行时 Python；tools 中保留 QtCore 构建依赖，不建立桌面宠物窗口。
生成缓存与 APK 不入版本管理；源视频与定位/步幅数据应保持成套。
