package io.github.chatgpt01.petai.chat.conversation

import io.github.chatgpt01.petai.chat.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID

/** Own once above the Activity; rotating a page only re-subscribes to state. */
class DefaultChatConversation private constructor(
    initial: ChatSession,
    private val sessions: ChatSessionStore,
    private val settings: ChatSettingsStore,
    private val credentials: ChatCredentialStore,
    private val transport: ChatTransport,
    private val prompt: String,
    owner: CoroutineScope,
    private val nextId: () -> String,
    private val timestamp: () -> Long,
    private val monotonicNanos: () -> Long,
) : ChatConversation {
    private val scope = CoroutineScope(owner.coroutineContext + SupervisorJob(owner.coroutineContext[Job]))
    private val monitor = Any()
    private val operations = Mutex()
    private val disk = Mutex()
    private val mutable = MutableStateFlow(ConversationState(initial))
    override val state: StateFlow<ConversationState> = mutable.asStateFlow()
    private var active: Job? = null

    override suspend fun send(text: String): SendResult = operations.withLock {
        if (state.value.generation.isRunning) return@withLock SendResult.Rejected(SendRejection.BUSY)
        if (text.isBlank()) return@withLock SendResult.Rejected(SendRejection.EMPTY_INPUT)
        if (text.length > ContextWindow.MAX_INPUT_CHARS) return@withLock SendResult.Rejected(SendRejection.INPUT_TOO_LONG)
        val user = ChatMessage(nextId(), MessageRole.USER, text, timestamp())
        val answer = ChatMessage(nextId(), MessageRole.ASSISTANT, "", timestamp(), MessageStatus.REQUESTING)
        val history = state.value.session.messages
        begin(state.value.session.copy(messages = history + user + answer), answer.id,
            ContextWindow.select(history, user))
    }

    override suspend fun retry(assistantMessageId: String): SendResult = operations.withLock {
        if (state.value.generation.isRunning) return@withLock SendResult.Rejected(SendRejection.BUSY)
        val session = state.value.session
        val answer = session.messages.lastOrNull()
        val user = session.messages.dropLast(1).lastOrNull()
        if (answer?.id != assistantMessageId || answer.role != MessageRole.ASSISTANT || user?.role != MessageRole.USER ||
            !(answer.status in listOf(MessageStatus.CANCELLED, MessageStatus.INTERRUPTED) ||
                (answer.status == MessageStatus.FAILED && answer.failure?.retryable == true)))
            return@withLock SendResult.Rejected(SendRejection.INVALID_RETRY)
        begin(session.copy(messages = session.messages.dropLast(1) + answer.copy(
            content = "", status = MessageStatus.REQUESTING, failure = null)), answer.id,
            ContextWindow.select(session.messages.dropLast(2), user))
    }

    private suspend fun begin(session: ChatSession, answerId: String, context: List<ChatMessage>): SendResult {
        if (!scope.isActive) return SendResult.Rejected(SendRejection.BUSY)
        val config: ChatModelConfig
        val key: ApiKey
        try {
            config = settings.load() ?: return SendResult.Rejected(SendRejection.NOT_CONFIGURED)
            key = credentials.load(config) ?: return SendResult.Rejected(SendRejection.NOT_CONFIGURED)
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { return SendResult.Rejected(SendRejection.NOT_CONFIGURED) }
        val request = ChatRequest(nextId(), config, prompt, context.toList())
        val job = scope.launch(start = CoroutineStart.LAZY) { generate(request, answerId, key) }
        try {
            disk.withLock {
                sessions.save(session)
                synchronized(monitor) {
                    mutable.value = ConversationState(session, GenerationState.start(request.requestId))
                    active = job
                }
            }
        } catch (e: CancellationException) { job.cancel(); throw e
        } catch (_: Exception) {
            job.cancel()
            synchronized(monitor) { mutable.value = mutable.value.copy(storageError = true) }
            return SendResult.Rejected(SendRejection.STORAGE_UNAVAILABLE)
        }
        job.start()
        return SendResult.Accepted(request.requestId)
    }

    override fun stop(requestId: String) {
        val job = synchronized(monitor) {
            val old = mutable.value.generation
            val stopped = old.cancel(requestId)
            if (stopped === old) return
            publish(stopped)
            active
        }
        job?.cancel()
        scope.launch { persistCurrent() }
    }

    private fun publish(generation: GenerationState) {
        val current = mutable.value
        val answer = current.session.messages.last()
        mutable.value = current.copy(generation = generation, session = current.session.copy(
            messages = current.session.messages.dropLast(1) + answer.copy(content = generation.text,
                status = MessageStatus.valueOf(generation.phase.name), failure = generation.failure)))
    }

    private class Finished : CancellationException()
    private suspend fun generate(request: ChatRequest, answerId: String, key: ApiKey) {
        var lastSave = monotonicNanos()
        try {
            transport.stream(request, key).collect { event ->
                val terminal = synchronized(monitor) {
                    val old = mutable.value.generation
                    if (old.requestId != request.requestId || !old.isRunning ||
                        mutable.value.session.messages.last().id != answerId) return@synchronized false
                    val safe = if (event is GenerationEvent.Delta && event.requestId == old.requestId &&
                        event.text.length > MAX_OUTPUT_CHARS - old.text.length)
                        GenerationEvent.Failed(request.requestId, ChatFailure(FailureCode.INVALID_RESPONSE)) else event
                    val next = old.apply(safe)
                    if (next !== old) publish(next)
                    !next.isRunning
                }
                if (terminal) throw Finished()
                val now = monotonicNanos()
                if (now - lastSave >= 500_000_000L) { persistCurrent(); lastSave = now }
            }
            synchronized(monitor) {
                if (mutable.value.generation.requestId == request.requestId && mutable.value.generation.isRunning)
                    publish(mutable.value.generation.apply(GenerationEvent.Failed(request.requestId,
                        ChatFailure(FailureCode.INVALID_RESPONSE, true))))
            }
        } catch (_: Finished) { // Terminal event; stop collecting and close the HTTP stream.
        } catch (e: CancellationException) {
            synchronized(monitor) {
                if (mutable.value.generation.requestId == request.requestId && mutable.value.generation.isRunning)
                    publish(mutable.value.generation.interrupted())
            }
            throw e
        } catch (_: Exception) {
            synchronized(monitor) {
                if (mutable.value.generation.requestId == request.requestId && mutable.value.generation.isRunning)
                    publish(mutable.value.generation.apply(GenerationEvent.Failed(request.requestId,
                        ChatFailure(FailureCode.NETWORK, true))))
            }
        } finally { withContext(NonCancellable) { persistCurrent() } }
    }

    private suspend fun persistCurrent() = disk.withLock {
        try {
            sessions.save(state.value.session)
            synchronized(monitor) { mutable.value = mutable.value.copy(storageError = false) }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { synchronized(monitor) { mutable.value = mutable.value.copy(storageError = true) } }
    }

    /** Application owner shutdown, not page navigation. Pending text is preserved as INTERRUPTED. */
    suspend fun close() = operations.withLock {
        synchronized(monitor) {
            if (mutable.value.generation.isRunning) publish(mutable.value.generation.interrupted())
        }
        scope.coroutineContext[Job]?.cancelAndJoin()
        withContext(NonCancellable) { persistCurrent() }
    }

    companion object {
        const val MAX_OUTPUT_CHARS = 32000
        suspend fun open(sessionId: String, characterId: String, sessions: ChatSessionStore,
                         settings: ChatSettingsStore, credentials: ChatCredentialStore, transport: ChatTransport,
                         prompt: String, owner: CoroutineScope,
                         nextId: () -> String = { UUID.randomUUID().toString() },
                         timestamp: () -> Long = { System.currentTimeMillis() },
                         monotonicNanos: () -> Long = { System.nanoTime() }): DefaultChatConversation {
            val loaded = sessions.load(sessionId) ?: ChatSession(sessionId, characterId, emptyList())
            if (loaded.id != sessionId || loaded.characterId != characterId) throw IOException("Chat identity mismatch")
            val recovered = loaded.copy(messages = loaded.messages.map {
                if (it.status in listOf(MessageStatus.REQUESTING, MessageStatus.STREAMING))
                    it.copy(status = MessageStatus.INTERRUPTED) else it
            })
            if (recovered != loaded) sessions.save(recovered)
            return DefaultChatConversation(recovered, sessions, settings, credentials, transport, prompt, owner, nextId, timestamp, monotonicNanos)
        }
    }
}
