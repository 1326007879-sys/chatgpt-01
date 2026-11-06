package io.github.chatgpt01.petai.chat.conversation

import io.github.chatgpt01.petai.chat.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class ConversationTest {
    class Store : ChatSessionStore {
        @Volatile var saved: ChatSession? = null
        @Volatile var fail = false
        val writes = AtomicInteger()
        override suspend fun load(sessionId: String) = saved
        override suspend fun save(session: ChatSession) {
            if (fail) throw IOException("fake disk error")
            saved = session.copy(messages = session.messages.toList()); writes.incrementAndGet()
        }
    }
    class Transport : ChatTransport {
        val requests = Channel<Pair<ChatRequest, Channel<GenerationEvent>>>(Channel.UNLIMITED)
        override fun stream(request: ChatRequest, apiKey: ApiKey) = flow {
            val events = Channel<GenerationEvent>(Channel.UNLIMITED)
            requests.send(request to events)
            for (event in events) emit(event)
        }
    }
    class Fixture {
        val store = Store(); val transport = Transport()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var configured = true
        var settingsGate: CompletableDeferred<Unit>? = null
        val settingsEntered = CompletableDeferred<Unit>()
        val settings = object : ChatSettingsStore {
            override suspend fun load(): ChatModelConfig? {
                settingsEntered.complete(Unit); settingsGate?.await()
                return if (configured) ChatModelConfig("https://example.com/v1", "test") else null
            }
            override suspend fun save(config: ChatModelConfig) {}
        }
        val keys = object : ChatCredentialStore {
            override suspend fun load(config: ChatModelConfig) = ApiKey("fake-key")
            override suspend fun save(config: ChatModelConfig, key: ApiKey) {}
            override suspend fun clear(config: ChatModelConfig) {}
        }
        suspend fun open() = DefaultChatConversation.open("main", "shenshen", store, settings, keys, transport, "角色", owner, monotonicNanos = { 0L })
    }
    private fun user(id: String, text: String) = ChatMessage(id, MessageRole.USER, text, 0)
    private fun answer(id: String, text: String, status: MessageStatus = MessageStatus.COMPLETED) =
        ChatMessage(id, MessageRole.ASSISTANT, text, 0, status)

    @Test fun contextKeepsWholeNewestPairsAndDropsCancelledTurns() {
        val history = listOf(user("u1", "old"), answer("a1", "reply"), user("u2", "bad"),
            answer("a2", "partial", MessageStatus.CANCELLED), user("u3", "new"), answer("a3", "yes"))
        assertEquals(listOf("u3", "a3", "u4"), ContextWindow.select(history, user("u4", "now"), 10).map { it.id })
        assertEquals(listOf("u4"), ContextWindow.select(history, user("u4", "now"), 3).map { it.id })
    }
    @Test fun sendIsSavedBeforeNetworkAndDuplicateSendIsRejected() = runBlocking {
        withTimeout(10000) {
            val f = Fixture(); val c = f.open()
            try {
                val accepted = c.send("你好") as SendResult.Accepted
                val (request, events) = f.transport.requests.receive()
                assertEquals("你好", f.store.saved!!.messages.first().content)
                assertEquals(SendResult.Rejected(SendRejection.BUSY), c.send("duplicate"))
                events.send(GenerationEvent.Delta(request.requestId, "你好🐳"))
                events.send(GenerationEvent.Completed(request.requestId))
                val done = c.state.first { !it.generation.isRunning }
                assertEquals(accepted.requestId, done.generation.requestId)
                assertEquals("你好🐳", done.session.messages.last().content)
            } finally { c.close(); f.owner.cancel() }
            assertEquals(MessageStatus.COMPLETED, f.store.saved!!.messages.last().status)
        }
    }
    @Test fun stopAndRetryReuseTurnButNeverOldRequestId() = runBlocking {
        withTimeout(10000) {
            val f = Fixture(); val c = f.open()
            try {
                c.send("question")
                val (first, events) = f.transport.requests.receive()
                events.send(GenerationEvent.Delta(first.requestId, "partial"))
                c.state.first { it.generation.text == "partial" }
                c.stop(first.requestId)
                assertEquals(MessageStatus.CANCELLED, c.state.value.session.messages.last().status)
                val answerId = c.state.value.session.messages.last().id
                c.retry(answerId)
                val (second, next) = f.transport.requests.receive()
                assertNotEquals(first.requestId, second.requestId)
                next.send(GenerationEvent.Delta(first.requestId, "stale"))
                next.send(GenerationEvent.Delta(second.requestId, "new"))
                next.send(GenerationEvent.Completed(second.requestId))
                val done = c.state.first { !it.generation.isRunning }
                assertEquals(2, done.session.messages.size)
                assertEquals(answerId, done.session.messages.last().id)
                assertEquals("new", done.session.messages.last().content)
                assertEquals(SendResult.Rejected(SendRejection.INVALID_RETRY), c.retry(answerId))
            } finally { c.close(); f.owner.cancel() }
        }
    }
    @Test fun diskFailureAndMissingConfigurationNeverStartNetwork() = runBlocking {
        val f = Fixture(); val c = f.open()
        try {
            f.configured = false
            assertEquals(SendResult.Rejected(SendRejection.NOT_CONFIGURED), c.send("hello"))
            f.configured = true; f.store.fail = true
            assertEquals(SendResult.Rejected(SendRejection.STORAGE_UNAVAILABLE), c.send("hello"))
            assertTrue(c.state.value.storageError)
            assertTrue(c.state.value.session.messages.isEmpty())
            assertTrue(f.transport.requests.tryReceive().isFailure)
            assertEquals(SendResult.Rejected(SendRejection.INPUT_TOO_LONG), c.send("x".repeat(12001)))
        } finally { c.close(); f.owner.cancel() }
    }
    @Test fun recoveryMarksPartialReplyInterruptedWithoutResending() = runBlocking {
        val f = Fixture()
        f.store.saved = ChatSession("main", "shenshen", listOf(user("u", "hi"), answer("a", "partial", MessageStatus.STREAMING)))
        val c = f.open()
        try {
            assertEquals(MessageStatus.INTERRUPTED, c.state.value.session.messages.last().status)
            assertEquals("partial", f.store.saved!!.messages.last().content)
            assertTrue(f.transport.requests.tryReceive().isFailure)
        } finally { c.close(); f.owner.cancel() }
    }
    @Test fun unexpectedEofAndOutputLimitAreVisibleFailures() = runBlocking {
        withTimeout(10000) {
            val f = Fixture(); val c = f.open()
            try {
                c.send("first"); val (_, stream) = f.transport.requests.receive(); stream.close()
                assertEquals(FailureCode.INVALID_RESPONSE, c.state.first { !it.generation.isRunning }.generation.failure?.code)
                c.send("second"); val (req, next) = f.transport.requests.receive()
                next.send(GenerationEvent.Delta(req.requestId, "x".repeat(32001)))
                val result = c.state.first { !it.generation.isRunning }
                assertEquals(FailureCode.INVALID_RESPONSE, result.generation.failure?.code)
                assertTrue(result.generation.text.isEmpty())
                assertEquals(listOf("second"), req.messages.map { it.content })
            } finally { c.close(); f.owner.cancel() }
        }
    }

    @Test fun fastStreamCoalescesWritesAndClosingPreservesInterruptedReply() = runBlocking {
        withTimeout(10000) {
            val f = Fixture(); val c = f.open()
            try {
                c.send("hello"); val (request, stream) = f.transport.requests.receive()
                repeat(100) { stream.send(GenerationEvent.Delta(request.requestId, "字")) }
                c.state.first { it.generation.text.length == 100 }
                assertEquals(1, f.store.writes.get())
                c.close()
                assertEquals(MessageStatus.INTERRUPTED, f.store.saved!!.messages.last().status)
                assertEquals(100, f.store.saved!!.messages.last().content.length)
            } finally { c.close(); f.owner.cancel() }
        }
    }
    @Test fun finalSaveFailureIsVisibleAndDoesNotDiscardReply() = runBlocking {
        withTimeout(10000) {
            val f = Fixture(); val c = f.open()
            try {
                c.send("hello"); val (request, stream) = f.transport.requests.receive()
                f.store.fail = true
                stream.send(GenerationEvent.Delta(request.requestId, "reply"))
                stream.send(GenerationEvent.Completed(request.requestId))
                val failed = c.state.first { it.storageError }
                assertEquals("reply", failed.session.messages.last().content)
                assertEquals(MessageStatus.COMPLETED, failed.session.messages.last().status)
                f.store.fail = false
                c.close()
                assertEquals("reply", f.store.saved!!.messages.last().content)
            } finally { c.close(); f.owner.cancel() }
        }
    }

    @Test fun closingDuringSendCannotPublishANewRunningRequestAfterClose() = runBlocking {
        withTimeout(10000) {
            val f = Fixture(); val c = f.open()
            try {
                val gate = CompletableDeferred<Unit>(); f.settingsGate = gate
                val send = async { c.send("hello") }
                f.settingsEntered.await()
                val closed = async(start = CoroutineStart.UNDISPATCHED) { c.close() }
                gate.complete(Unit)
                send.await(); closed.await()
                assertFalse(c.state.value.generation.isRunning)
                assertEquals(SendResult.Rejected(SendRejection.BUSY), c.send("after close"))
                assertEquals(MessageStatus.INTERRUPTED, f.store.saved!!.messages.last().status)
            } finally { c.close(); f.owner.cancel() }
        }
    }
}
