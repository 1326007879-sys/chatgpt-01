package io.github.chatgpt01.petai

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.chatgpt01.petai.chat.*
import io.github.chatgpt01.petai.chat.ui.*
import org.junit.Assert.*
import org.junit.Rule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Test

/** Real Compose semantics and touch actions. Compile-only until an Android device is attached. */
class ChatScreenDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun emptyDraftDisablesSendAndTypedMessageCanBeSent() {
        var text = ""; var sent = false
        compose.setContent {
            ChatTheme {
                var draft by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                ChatScreen(ConversationState(ChatSession("main", "shenshen", emptyList())), ChatInputState(draft),
                    { text = it; draft = it }, { sent = true }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("chat_send").assertIsNotEnabled()
        compose.onNodeWithTag("chat_input").performTextInput("你好")
        compose.onNodeWithTag("chat_send").assertIsEnabled().performClick()
        assertEquals("你好", text); assertTrue(sent)
    }
    @Test fun runningReplyOffersStop() {
        var stopped = false
        val message = ChatMessage("a", MessageRole.ASSISTANT, "partial", 0, MessageStatus.STREAMING)
        compose.setContent {
            ChatTheme { ChatScreen(ConversationState(ChatSession("main", "shenshen", listOf(message)),
                GenerationState.start("r")), ChatInputState(), {}, {}, { stopped = true }, {}, {}, {}) }
        }
        compose.onNodeWithTag("chat_stop").performClick()
        assertTrue(stopped)
        compose.onNodeWithText("partial").assertIsDisplayed()
    }

    @Test fun cancelledLatestReplyOffersRetry() {
        var retried: String? = null
        val message = ChatMessage("a", MessageRole.ASSISTANT, "partial", 0, MessageStatus.CANCELLED)
        compose.setContent {
            ChatTheme { ChatScreen(ConversationState(ChatSession("main", "shenshen", listOf(message))),
                ChatInputState(), {}, {}, {}, { retried = it }, {}, {}) }
        }
        compose.onNodeWithText("重试").performClick()
        assertEquals("a", retried)
    }
}
