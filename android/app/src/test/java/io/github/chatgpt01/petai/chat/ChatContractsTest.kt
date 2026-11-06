package io.github.chatgpt01.petai.chat

import org.junit.Assert.*
import org.junit.Test

class ChatContractsTest {
    private fun user(id: String = "u1") = ChatMessage(id, MessageRole.USER, "你好", 100)
    private fun assistant(status: MessageStatus, text: String = "部分回复") =
        ChatMessage("a1", MessageRole.ASSISTANT, text, 101, status,
            if (status == MessageStatus.FAILED) ChatFailure(FailureCode.NETWORK, true) else null)
    private val config get() = ChatModelConfig("https://example.com/v1", "test-model")

    @Test fun validConversationPreservesChineseAndStableIdentity() {
        val session = ChatSession("main", "shenshen", listOf(user(), assistant(MessageStatus.COMPLETED)))
        assertEquals(1, session.schemaVersion)
        assertEquals("你好", session.messages.first().content)
        assertEquals("a1", session.messages.last().id)
    }

    @Test fun duplicateMessageIdsAndUnknownSchemasAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ChatSession("main", "shenshen", listOf(user(), user()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChatSession("main", "shenshen", emptyList(), schemaVersion = 2)
        }
    }

    @Test fun unfinishedUserMessagesAndBlankCompletedMessagesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            user().copy(status = MessageStatus.STREAMING)
        }
        for (role in MessageRole.entries) {
            assertThrows(IllegalArgumentException::class.java) { ChatMessage("id", role, "  ", 0) }
        }
        assertEquals("", assistant(MessageStatus.STREAMING, "").content)
    }

    @Test fun requestRejectsUnfinishedHistoryAndRequiresTheLastUserTurn() {
        for (status in MessageStatus.entries.filter { it != MessageStatus.COMPLETED }) {
            assertThrows(IllegalArgumentException::class.java) {
                ChatRequest("r1", config, "你是一只桌宠", listOf(user(), assistant(status)))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChatRequest("r1", config, "角色", listOf(assistant(MessageStatus.COMPLETED)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChatRequest("r1", config, "角色", emptyList())
        }
        assertEquals("r1", ChatRequest("r1", config, "角色", listOf(user())).requestId)
    }

    @Test fun configurationRequiresHttpsAndExcludesUrlCredentials() {
        for (url in listOf("http://example.com/v1", "https://user:secret@example.com/v1",
                "https://example.com/v1?key=secret", "https://example.com/v1#secret",
                "https:///v1", "https://example.com:70000/v1")) {
            assertThrows(IllegalArgumentException::class.java) { ChatModelConfig(url, "model") }
        }
        assertEquals("https://example.com/custom/v1/",
            ChatModelConfig("https://example.com/custom/v1/", "model").baseUrl)
    }

    @Test fun invalidModelParametersAreRejectedRatherThanSilentlyChanged() {
        assertThrows(IllegalArgumentException::class.java) { config.copy(model = " ") }
        assertThrows(IllegalArgumentException::class.java) { config.copy(temperature = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { config.copy(temperature = 2.1) }
        assertThrows(IllegalArgumentException::class.java) { config.copy(maxOutputTokens = 0) }
        assertThrows(IllegalArgumentException::class.java) { config.copy(timeoutSeconds = 0) }
    }

    @Test fun apiKeyDoesNotAppearInDiagnosticStrings() {
        val key = ApiKey("fake-test-secret")
        assertEquals("fake-test-secret", key.reveal())
        assertFalse(key.toString().contains("fake-test-secret"))
        assertThrows(IllegalArgumentException::class.java) { ApiKey(" ") }
        assertThrows(IllegalArgumentException::class.java) { ApiKey("key\nInjected: header") }
    }

    @Test fun savedFailureKeepsItsSafeReasonAndRetryPolicy() {
        val failed = assistant(MessageStatus.FAILED)
        val session = ChatSession("main", "shenshen", listOf(user(), failed))
        assertEquals(FailureCode.NETWORK, session.messages.last().failure?.code)
        assertEquals(true, session.messages.last().failure?.retryable)
        assertThrows(IllegalArgumentException::class.java) { failed.copy(failure = null) }
        assertThrows(IllegalArgumentException::class.java) { user().copy(failure = failed.failure) }
    }

    @Test fun blankIdentitiesAndNegativeTimestampsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { user().copy(id = " ") }
        assertThrows(IllegalArgumentException::class.java) { user().copy(createdAtEpochMs = -1) }
        assertThrows(IllegalArgumentException::class.java) { ChatSession("", "shenshen", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { GenerationState.start(" ") }
        assertThrows(IllegalArgumentException::class.java) {
            ChatRequest("", config, "角色", listOf(user()))
        }
    }
}
