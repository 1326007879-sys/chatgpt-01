package io.github.chatgpt01.petai.chat.ui

import android.app.Activity
import android.content.Intent
import android.content.Context
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContracts
import io.github.chatgpt01.petai.R
import io.github.chatgpt01.petai.chat.voice.*
import java.util.Locale
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
    private lateinit var speech: SystemSpeechOutput
    private val voiceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val readTracker = ReplyReadTracker()
    private var boundConversation: ChatConversation? = null
    private var foreground by mutableStateOf(false)
    private var voiceNotice by mutableStateOf<String?>(null)
    private val recognition = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognized = runCatching {
                result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            }.getOrDefault("")
            if (recognized.isBlank()) voiceNotice = getString(R.string.voice_input_empty)
            else voiceScope.launch {
                try {
                    val input = ChatRuntime.get(applicationContext).session().second
                    val draft = VoiceText.appendDraft(input.state.value.draft, recognized)
                    if (draft == null) voiceNotice = getString(R.string.voice_input_too_long)
                    else { input.edit(draft); voiceNotice = getString(R.string.voice_input_review) }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { voiceNotice = getString(R.string.voice_input_failed) }
            }
        }
    }

    private fun recognize() {
        speech.stop()
        voiceNotice = null
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_input_prompt))
        try {
            if (intent.resolveActivity(packageManager) == null) voiceNotice = getString(R.string.voice_input_missing)
            else recognition.launch(intent)
        } catch (_: RuntimeException) { voiceNotice = getString(R.string.voice_input_failed) }
    }

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
                    Text(if (failed) "无法读取聊天历史，原文件已保留。可以重试读取。" else "正在读取聊天…")
                    if (failed) Button(onClick = { attempt++ }) { Text("重试读取") }
                    TextButton(onClick = { finish() }) { Text("返回") }
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
                    else ChatScreen(state, input, { voiceNotice = null; ready.second.edit(it) },
                        { speech.stop(); voiceNotice = null; ready.second.send() },
                        { speech.stop(); state.generation.requestId?.let(ready.first::stop) },
                        { speech.stop(); ready.second.retry(it) },
                        { speech.stop(); runtime.settingsUi.load(); settingsOpen = true }, { finish() },
                        speechState, voiceNotice, ::recognize,
                        { speech.stop(); voiceSettingsOpen = true }, speech::speak, speech::stop)
                    if (settingsOpen) ModelSettingsDialog(runtime.settingsUi) { settingsOpen = false }
                }
            }
        }
    }
}
