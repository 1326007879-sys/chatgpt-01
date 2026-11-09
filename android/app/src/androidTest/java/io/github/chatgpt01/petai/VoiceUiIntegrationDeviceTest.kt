package io.github.chatgpt01.petai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.chatgpt01.petai.chat.*
import io.github.chatgpt01.petai.chat.ui.*
import io.github.chatgpt01.petai.chat.voice.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** UI integration without external credentials, microphone access, or a speech-provider requirement. */
class VoiceUiIntegrationDeviceTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun unavailableSpeechKeepsKeyboardAndSendWorking() {
        var sent = false
        var edited = ""
        compose.setContent {
            ChatTheme {
                ChatScreen(ConversationState(ChatSession("main", "shenshen", emptyList())),
                    ChatInputState("测试文字"), { edited = it }, { sent = true }, {}, {}, {}, {},
                    speech = SpeechUiState(loading = false, notice = text(R.string.voice_engine_missing)))
            }
        }
        compose.onNodeWithTag("chat_input").performTextReplacement("仍能输入")
        assertEquals("仍能输入", edited)
        compose.onNodeWithTag("chat_send").assertIsEnabled().performClick()
        assertTrue(sent)
        compose.onNodeWithText(text(R.string.voice_engine_missing)).assertExists()
    }

    @Test fun keyboardInputRemainsEditableAndFocusingItStopsReading() {
        var stopped = false
        var draft = ""
        compose.setContent {
            ChatTheme {
                var input by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                ChatScreen(ConversationState(ChatSession("main", "shenshen", emptyList())),
                    ChatInputState(input), { input = it; draft = it }, {}, {}, {}, {}, {},
                    onStopRead = { stopped = true })
            }
        }
        compose.onNodeWithTag("chat_input").performClick().performTextInput("键盘语音结果")
        compose.onNodeWithTag("chat_send").assertIsEnabled()
        assertEquals("键盘语音结果", draft)
        assertTrue(stopped)
        compose.onNodeWithText("Dictate").assertDoesNotExist()
    }

    @Test fun completedReplyCanBeReadWithoutReadingTheUserMessage() {
        var spoken = ""
        val messages = listOf(ChatMessage("u", MessageRole.USER, "我的问题", 1),
            ChatMessage("a", MessageRole.ASSISTANT, "桌宠回复", 2))
        compose.setContent {
            ChatTheme {
                ChatScreen(ConversationState(ChatSession("main", "shenshen", messages)), ChatInputState(),
                    {}, {}, {}, {}, {}, {}, speech = SpeechUiState(loading = false, ready = true, canSpeak = true),
                    onRead = { spoken = it })
            }
        }
        compose.onAllNodesWithText(text(R.string.voice_read_reply)).assertCountEquals(1)
        compose.onNodeWithText(text(R.string.voice_read_reply)).performClick()
        assertEquals("桌宠回复", spoken)
    }

    @Test fun englishUiTranslatesNoticesButPreservesUserAndModelText() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = android.content.res.Configuration(app.resources.configuration).apply {
            setLocale(java.util.Locale.ENGLISH)
        }
        val localized = app.createConfigurationContext(configuration)
        val messages = listOf(ChatMessage("u", MessageRole.USER, "用户原文", 1),
            ChatMessage("a", MessageRole.ASSISTANT, "模型原文", 2, MessageStatus.INTERRUPTED))
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides localized,
                androidx.compose.ui.platform.LocalConfiguration provides configuration) {
                ChatTheme {
                    ChatScreen(ConversationState(ChatSession("main", "shenshen", messages)),
                        ChatInputState(), {}, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Model settings").assertExists()
        compose.onNodeWithText("Send").assertExists()
        compose.onNodeWithText("Reply interrupted; you can retry").assertExists()
        compose.onNodeWithText("用户原文").assertExists()
        compose.onNodeWithText("模型原文").assertExists()
    }

    @Test fun voiceSettingsExposePreviewStopAndSelectionCallbacks() {
        var preview = false; var stopped = false; var selected: String? = null
        compose.setContent {
            ChatTheme {
                VoiceSettingsScreen(SpeechUiState(loading = false, ready = true, canSpeak = true,
                    speaking = true, voices = listOf(SystemVoiceOption("test-zh", "zh-CN", false))),
                    {}, { selected = it }, { preview = true }, { stopped = true }, {})
            }
        }
        val list = compose.onNodeWithTag("voice_settings")
        list.performScrollToNode(hasText(text(R.string.voice_preview)))
        compose.onNodeWithText(text(R.string.voice_preview)).performClick()
        list.performScrollToNode(hasText(text(R.string.voice_stop)))
        compose.onNodeWithText(text(R.string.voice_stop)).performClick()
        val label = "test-zh\nzh-CN · ${text(R.string.voice_offline)}"
        list.performScrollToNode(hasText(label))
        compose.onNodeWithText(label).performClick()
        assertTrue(preview); assertTrue(stopped); assertEquals("test-zh", selected)
    }
}
