package io.github.chatgpt01.petai.chat.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.chatgpt01.petai.chat.ChatConversation
import io.github.chatgpt01.petai.chat.conversation.DefaultChatConversation
import io.github.chatgpt01.petai.chat.storage.*
import io.github.chatgpt01.petai.chat.transport.OpenAiTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Application-context owner; holds no Activity. Do not close the conversation on page exit. */
internal class ChatRuntime(context: Context) {
    private val application = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings = AndroidChatSettingsStore(application)
    private val keys = AndroidChatCredentialStore(application)
    val settingsUi = ModelSettingsController(settings, keys, scope)
    private val mutex = Mutex()
    private var session: Pair<ChatConversation, ChatInputController>? = null
    suspend fun session(): Pair<ChatConversation, ChatInputController> = mutex.withLock {
        session?.let { return@withLock it }
        val conversation = DefaultChatConversation.open("main", "shenshen",
            FileChatSessionStore(application.noBackupFilesDir.toPath().resolve("chat/sessions")),
            settings, keys, OpenAiTransport(), "你是一只温柔、活泼的桌面宠物。请用简洁自然的中文陪伴用户，不要假装能执行手机操作。", scope)
        (conversation to ChatInputController(conversation, scope)).also { session = it }
    }
    companion object {
        @Volatile private var instance: ChatRuntime? = null
        fun get(context: Context): ChatRuntime = instance ?: synchronized(this) {
            instance ?: ChatRuntime(context.applicationContext).also { instance = it }
        }
    }
}

/** Private page opened by explicit user actions from the app or pet menu. */
class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val runtime = ChatRuntime.get(applicationContext)
        setContent {
            ChatTheme {
                var session by remember { mutableStateOf<Pair<ChatConversation, ChatInputController>?>(null) }
                var failed by remember { mutableStateOf(false) }
                var attempt by remember { mutableIntStateOf(0) }
                LaunchedEffect(attempt) {
                    failed = false
                    try { session = runtime.session() }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { failed = true }
                }
                val ready = session
                if (ready == null) Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
                    Text(if (failed) "无法读取聊天历史，原文件已保留。可以重试读取。" else "正在读取聊天…")
                    if (failed) Button(onClick = { attempt++ }) { Text("重试读取") }
                    TextButton(onClick = { finish() }) { Text("返回") }
                } else {
                    val state by ready.first.state.collectAsState()
                    val input by ready.second.state.collectAsState()
                    var settingsOpen by remember { mutableStateOf(false) }
                    ChatScreen(state, input, ready.second::edit, { ready.second.send() },
                        { state.generation.requestId?.let(ready.first::stop) }, { ready.second.retry(it) },
                        { runtime.settingsUi.load(); settingsOpen = true }, { finish() })
                    if (settingsOpen) ModelSettingsDialog(runtime.settingsUi) { settingsOpen = false }
                }
            }
        }
    }
}
