package io.github.chatgpt01.petai.chat.ui

import io.github.chatgpt01.petai.chat.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ChatControllersTest {
    class Conversation : ChatConversation {
        override val state = MutableStateFlow(ConversationState(ChatSession("main", "shenshen", emptyList())))
        var result: SendResult = SendResult.Accepted("r1")
        var gate: CompletableDeferred<Unit>? = null
        var calls = 0
        override suspend fun send(text: String): SendResult { calls++; gate?.await(); return result }
        override suspend fun retry(assistantMessageId: String): SendResult { calls++; gate?.await(); return result }
        override fun stop(requestId: String) {}
    }
    @Test fun rejectedSendPreservesDraftAndSuccessfulSendClearsOnlyUneditedDraft() = runBlocking {
        val conversation = Conversation(); val ui = ChatInputController(conversation, this)
        ui.edit("你好")
        conversation.result = SendResult.Rejected(SendRejection.STORAGE_UNAVAILABLE)
        ui.send().join()
        assertEquals("你好", ui.state.value.draft)
        assertNotNull(ui.state.value.notice)
        conversation.result = SendResult.Accepted("r1")
        ui.send().join()
        assertEquals("", ui.state.value.draft)
        assertNull(ui.state.value.notice)
    }
    @Test fun repeatedTapCannotSubmitTwiceAndRetypedSameTextIsPreserved() = runBlocking {
        val conversation = Conversation(); val ui = ChatInputController(conversation, this)
        val gate = CompletableDeferred<Unit>(); conversation.gate = gate
        ui.edit("hello"); val send = ui.send()
        ui.send().join()
        ui.edit(""); ui.edit("hello")
        gate.complete(Unit); send.join()
        assertEquals(1, conversation.calls)
        assertEquals("hello", ui.state.value.draft)
        assertFalse(ui.state.value.submitting)
    }
    @Test fun retryDoesNotConsumeAnUnsentDraft() = runBlocking {
        val conversation = Conversation(); val ui = ChatInputController(conversation, this)
        ui.edit("unsent"); ui.retry("a1").join()
        assertEquals("unsent", ui.state.value.draft)
    }
    class Stores : ChatSettingsStore, ChatCredentialStore {
        var config: ChatModelConfig? = null
        val secrets = mutableMapOf<String, ApiKey>()
        var fail = false
        override suspend fun load() = config
        override suspend fun save(config: ChatModelConfig) { if (fail) throw IOException("private detail"); this.config = config }
        override suspend fun load(config: ChatModelConfig) = secrets[scope(config)]
        override suspend fun save(config: ChatModelConfig, key: ApiKey) { secrets[scope(config)] = key }
        override suspend fun clear(config: ChatModelConfig) { secrets.remove(scope(config)) }
        private fun scope(c: ChatModelConfig) = c.baseUrl.trimEnd('/')
    }
    @Test fun switchingServiceDoesNotReuseSavedCredentialAndInvalidSettingsNeverPersist() = runBlocking {
        val stores = Stores(); val ui = ModelSettingsController(stores, stores, this)
        ui.load().join()
        val original = ModelEditor(model = "test")
        ui.save(original, "fake-key").join()
        assertEquals(1, ui.state.value.saved)
        val newServer = original.copy(url = "https://other.example/v1")
        ui.save(newServer, "").join()
        assertEquals(original.config(), stores.config)
        assertNotNull(ui.state.value.error)
        ui.save(original.copy(url = "http://example.com"), "secret-test").join()
        assertEquals(original.config(), stores.config)
        assertFalse(ui.state.value.error!!.contains("secret-test"))
    }
    @Test fun blankKeyPreservesExistingAndDeleteRemovesOnlyThatService() = runBlocking {
        val stores = Stores(); val ui = ModelSettingsController(stores, stores, this)
        val editor = ModelEditor(model = "test")
        ui.save(editor, "fake-key").join()
        ui.save(editor.copy(model = "other-model"), "").join()
        assertEquals("fake-key", stores.load(editor.config())!!.reveal())
        ui.clearKey(editor).join()
        assertNull(stores.load(editor.config()))
        assertFalse(ui.state.value.hasKey)
    }
    @Test fun failedSettingsSaveDoesNotReportSuccessOrExposeErrorDetails() = runBlocking {
        val stores = Stores(); stores.fail = true
        val ui = ModelSettingsController(stores, stores, this)
        ui.save(ModelEditor(model = "test"), "fake-key").join()
        assertEquals(0, ui.state.value.saved)
        assertFalse(ui.state.value.busy)
        assertNotNull(ui.state.value.error)
        assertFalse(ui.state.value.error!!.contains("private detail"))
    }
    @Test fun onlyLatestRetryableTerminalAnswerShowsRetry() {
        val answer = ChatMessage("a", MessageRole.ASSISTANT, "partial", 0, MessageStatus.CANCELLED)
        assertTrue(canRetry(answer, "a", false))
        assertFalse(canRetry(answer, "other", false))
        assertFalse(canRetry(answer, "a", true))
        assertTrue(statusText(answer).contains("停止"))
    }
}
