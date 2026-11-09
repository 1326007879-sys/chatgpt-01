package io.github.chatgpt01.petai.chat.ui

import android.content.Context
import io.github.chatgpt01.petai.R
import io.github.chatgpt01.petai.chat.voice.*
import androidx.compose.ui.res.stringResource
import android.os.Bundle
import io.github.chatgpt01.petai.LocalizedActivity
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
class ChatActivity : LocalizedActivity() {
    private lateinit var speech: SystemSpeechOutput
    private val voiceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val readTracker = ReplyReadTracker()
    private var boundConversation: ChatConversation? = null
    private var foreground by mutableStateOf(false)
    override fun onStart() {
        super.onStart()
        boundConversation?.let { readTracker.baseline(it.state.value.session.messages) }
        foreground = true
    }
    override fun onStop() {
        foreground = false
        if (::speech.isInitialized) speech.stop()
        super.onStop()
    }
    override fun onDestroy() {
        voiceScope.cancel()
        if (::speech.isInitialized) speech.close()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        speech = SystemSpeechOutput(applicationContext)
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
                    Text(if (failed) stringResource(R.string.history_load_failed) else stringResource(R.string.history_loading))
                    if (failed) Button(onClick = { attempt++ }) { Text(stringResource(R.string.history_retry)) }
                    TextButton(onClick = { finish() }) { Text(stringResource(R.string.back)) }
                } else {
                    val state by ready.first.state.collectAsState()
                    val input by ready.second.state.collectAsState()
                    var settingsOpen by remember { mutableStateOf(false) }
                    var voiceSettingsOpen by remember { mutableStateOf(false) }
                    val speechState by speech.state.collectAsState()
                    SideEffect { boundConversation = ready.first }
                    LaunchedEffect(ready.first) {
                        readTracker.baseline(ready.first.state.value.session.messages)
                        ready.first.state.collect { snapshot ->
                            // Streaming snapshots cannot be read; avoid scanning history on every token.
                            if (!snapshot.generation.isRunning) {
                                readTracker.observe(snapshot.session.messages, speech.state.value.autoRead,
                                    foreground && !settingsOpen && !voiceSettingsOpen)?.let { speech.speak(it.content) }
                            }
                        }
                    }
                    if (voiceSettingsOpen) VoiceSettingsScreen(speechState, speech::autoRead, speech::select,
                        { speech.speak(getString(R.string.voice_sample)) }, speech::stop,
                        { speech.stop(); voiceSettingsOpen = false })
                    else ChatScreen(state, input, { ready.second.edit(it) },
                        { speech.stop(); ready.second.send() },
                        { speech.stop(); state.generation.requestId?.let(ready.first::stop) },
                        { speech.stop(); ready.second.retry(it) },
                        { speech.stop(); runtime.settingsUi.load(); settingsOpen = true }, { finish() },
                        speechState,
                        { speech.stop(); voiceSettingsOpen = true }, speech::speak, speech::stop)
                    if (settingsOpen) ModelSettingsDialog(runtime.settingsUi) { settingsOpen = false }
                }
            }
        }
    }
}
