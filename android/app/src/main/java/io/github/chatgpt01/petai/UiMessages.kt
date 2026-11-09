package io.github.chatgpt01.petai

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Translate known app notices, never model replies, user drafts or provider text. */
object UiMessages {
    private val resources = mapOf(
        "请等待素材处理完成，或先停止悬浮桌宠，再重试。" to R.string.packs_busy,
        "Wait for the current operation or stop the overlay, then retry." to R.string.packs_busy,
        "请先允许显示在其他应用上层" to R.string.permission_required,
        "无法启动桌宠，请重新打开应用后重试" to R.string.start_failed,
        "动画资源无法读取，请重新安装此测试版" to R.string.assets_failed,
        "Allow display over other apps to start your pet." to R.string.permission_required,
        "Could not start the pet. Open the app and try again." to R.string.start_failed,
        "Animation files could not be read. Reinstall this test build." to R.string.assets_failed,
        "无法读取模型配置，请检查或重新保存。" to R.string.chat_notice_0,
        "请输入此服务的 API Key；不同服务的密钥不会自动沿用。" to R.string.chat_notice_1,
        "请检查 HTTPS API 根地址、模型名称和数值范围。" to R.string.chat_notice_2,
        "保存失败；密钥未回退为明文，请重试。" to R.string.chat_notice_3,
        "无法删除此服务的密钥，请检查地址或重试。" to R.string.chat_notice_4,
        "发送失败，输入已保留，请重试。" to R.string.chat_notice_5,
        "重试失败，请稍后再试。" to R.string.chat_notice_6,
        "已有回复正在生成，请先停止或等待完成。" to R.string.chat_notice_7,
        "请输入消息。" to R.string.chat_notice_8,
        "消息过长，请缩短到 12,000 字符以内。" to R.string.chat_notice_9,
        "请先在模型设置中保存服务地址、模型和密钥。" to R.string.chat_notice_10,
        "只能重试最近一条可重试的未完成回复。" to R.string.chat_notice_11,
        "无法保存消息，未发起请求；输入已保留。" to R.string.chat_notice_12,
        "等待回复…" to R.string.chat_notice_13,
        "正在回复…" to R.string.chat_notice_14,
        "已停止，保留部分回复" to R.string.chat_notice_15,
        "回复已中断，可重试" to R.string.chat_notice_16,
        "认证失败，请检查密钥和服务权限。" to R.string.chat_notice_17,
        "请求过于频繁，请稍后重试。" to R.string.chat_notice_18,
        "请求超时，请检查网络后重试。" to R.string.chat_notice_19,
        "网络连接失败，请重试。" to R.string.chat_notice_20,
        "模型服务暂时不可用，请稍后重试。" to R.string.chat_notice_21,
        "模型未返回文字。" to R.string.chat_notice_22,
        "回复格式异常或超过长度限制。" to R.string.chat_notice_23,
    )
    @Composable fun text(value: String): String = resources[value]?.let { stringResource(it) } ?: value
}
