package io.github.chatgpt01.petai.chat.ui

import io.github.chatgpt01.petai.chat.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Non-secret editor fields only; API keys never enter saved state or diagnostic data classes. */
data class ModelEditor(val url: String = "https://api.openai.com/v1", val model: String = "",
                       val temperature: String = "1", val tokens: String = "1024", val timeout: String = "60") {
    fun config() = ChatModelConfig(url.trim(), model.trim(), temperature.toDouble(), tokens.toInt(), timeout.toInt())
    companion object {
        fun from(c: ChatModelConfig) = ModelEditor(c.baseUrl, c.model, c.temperature.toString(), c.maxOutputTokens.toString(), c.timeoutSeconds.toString())
    }
}
data class SettingsUiState(val editor: ModelEditor = ModelEditor(), val loading: Boolean = true,
                           val busy: Boolean = false, val hasKey: Boolean = false,
                           val error: String? = null, val saved: Int = 0)

class ModelSettingsController(private val settings: ChatSettingsStore, private val keys: ChatCredentialStore,
                              private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(SettingsUiState())
    val state = mutable.asStateFlow()
    fun load(): Job = scope.launch { mutex.withLock {
        mutable.value = mutable.value.copy(loading = true, error = null)
        try {
            val config = settings.load()
            mutable.value = mutable.value.copy(editor = config?.let(ModelEditor::from) ?: ModelEditor(),
                hasKey = config != null && keys.load(config) != null, loading = false)
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { mutable.value = mutable.value.copy(loading = false, error = "无法读取模型配置，请检查或重新保存。") }
    } }
    fun save(editor: ModelEditor, keyInput: String): Job {
        if (mutable.value.busy) return Job().apply { complete() }
        mutable.value = mutable.value.copy(busy = true, error = null)
        return scope.launch { mutex.withLock {
            try {
                val config = editor.config()
                if (keyInput.isNotBlank()) keys.save(config, ApiKey(keyInput.trim()))
                else if (keys.load(config) == null) {
                    mutable.value = mutable.value.copy(error = "请输入此服务的 API Key；不同服务的密钥不会自动沿用。")
                    return@withLock
                }
                // Save the selected endpoint only after its scoped credential is available.
                settings.save(config)
                mutable.value = mutable.value.copy(editor = ModelEditor.from(config), hasKey = true,
                    saved = mutable.value.saved + 1)
            } catch (e: CancellationException) { throw e
            } catch (_: IllegalArgumentException) { mutable.value = mutable.value.copy(error = "请检查 HTTPS API 根地址、模型名称和数值范围。")
            } catch (_: Exception) { mutable.value = mutable.value.copy(error = "保存失败；密钥未回退为明文，请重试。")
            } finally { mutable.value = mutable.value.copy(busy = false) }
        } }
    }
    fun clearKey(editor: ModelEditor): Job {
        if (mutable.value.busy) return Job().apply { complete() }
        mutable.value = mutable.value.copy(busy = true, error = null)
        return scope.launch { mutex.withLock {
            try { keys.clear(editor.config()); mutable.value = mutable.value.copy(hasKey = false) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "无法删除此服务的密钥，请检查地址或重试。") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        } }
    }
}

data class ChatInputState(val draft: String = "", val submitting: Boolean = false, val notice: String? = null)
class ChatInputController(private val conversation: ChatConversation, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(ChatInputState())
    val state = mutable.asStateFlow()
    private var revision = 0L
    fun edit(text: String) { revision++; mutable.value = mutable.value.copy(draft = text, notice = null) }
    fun send(): Job {
        val current = mutable.value
        val sentRevision = revision
        if (current.submitting || current.draft.isBlank()) return Job().apply { complete() }
        mutable.value = current.copy(submitting = true, notice = null)
        return scope.launch {
            try {
                val result = conversation.send(current.draft)
                mutable.value = mutable.value.copy(draft = if (result is SendResult.Accepted &&
                    revision == sentRevision) "" else mutable.value.draft,
                    notice = (result as? SendResult.Rejected)?.reason?.let(::rejectionText))
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutable.value = mutable.value.copy(notice = "发送失败，输入已保留，请重试。") }
            finally { mutable.value = mutable.value.copy(submitting = false) }
        }
    }
    fun retry(id: String): Job {
        if (mutable.value.submitting) return Job().apply { complete() }
        mutable.value = mutable.value.copy(submitting = true, notice = null)
        return scope.launch {
            try { mutable.value = mutable.value.copy(notice = (conversation.retry(id) as? SendResult.Rejected)?.reason?.let(::rejectionText)) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(notice = "重试失败，请稍后再试。") }
            finally { mutable.value = mutable.value.copy(submitting = false) }
        }
    }
}

fun rejectionText(reason: SendRejection): String = when (reason) {
    SendRejection.BUSY -> "已有回复正在生成，请先停止或等待完成。"
    SendRejection.EMPTY_INPUT -> "请输入消息。"
    SendRejection.INPUT_TOO_LONG -> "消息过长，请缩短到 12,000 字符以内。"
    SendRejection.NOT_CONFIGURED -> "请先在模型设置中保存服务地址、模型和密钥。"
    SendRejection.INVALID_RETRY -> "只能重试最近一条可重试的未完成回复。"
    SendRejection.STORAGE_UNAVAILABLE -> "无法保存消息，未发起请求；输入已保留。"
}
fun statusText(message: ChatMessage): String = when (message.status) {
    MessageStatus.REQUESTING -> "等待回复…"
    MessageStatus.STREAMING -> "正在回复…"
    MessageStatus.COMPLETED -> ""
    MessageStatus.CANCELLED -> "已停止，保留部分回复"
    MessageStatus.INTERRUPTED -> "回复已中断，可重试"
    MessageStatus.FAILED -> when (message.failure?.code) {
        FailureCode.AUTHENTICATION -> "认证失败，请检查密钥和服务权限。"
        FailureCode.RATE_LIMITED -> "请求过于频繁，请稍后重试。"
        FailureCode.TIMEOUT -> "请求超时，请检查网络后重试。"
        FailureCode.NETWORK -> "网络连接失败，请重试。"
        FailureCode.SERVER -> "模型服务暂时不可用，请稍后重试。"
        FailureCode.EMPTY_RESPONSE -> "模型未返回文字。"
        else -> "回复格式异常或超过长度限制。"
    }
}
fun canRetry(message: ChatMessage, latestId: String?, running: Boolean): Boolean = !running && message.id == latestId &&
    message.role == MessageRole.ASSISTANT && (message.status in listOf(MessageStatus.CANCELLED, MessageStatus.INTERRUPTED) ||
        (message.status == MessageStatus.FAILED && message.failure?.retryable == true))
