package io.github.chatgpt01.petai.chat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cold, single-request stream. Emit deltas followed by exactly one Completed/Failed event.
 * Collection cancellation must close HTTP resources and propagate CancellationException.
 * A truncated stream is INVALID_RESPONSE, never an implicit successful completion.
 * I/O implementations run off the main thread and do not log keys, prompts or response bodies.
 */
interface ChatTransport {
    fun stream(request: ChatRequest, apiKey: ApiKey): Flow<GenerationEvent>
}

/** No API key in this store or in PetPreferences. Null means not configured. */
interface ChatSettingsStore {
    suspend fun load(): ChatModelConfig?
    suspend fun save(config: ChatModelConfig)
}

/**
 * Encrypt at rest using Android Keystore; never fall back to plaintext. Credentials are scoped to
 * the normalized API root (not the model); changing servers must never reuse another server's key.
 */
interface ChatCredentialStore {
    suspend fun load(config: ChatModelConfig): ApiKey?
    suspend fun save(config: ChatModelConfig, key: ApiKey)
    suspend fun clear(config: ChatModelConfig)
}

/**
 * Atomic whole-session replacement in app-private storage. Null means absent, not corrupt.
 * On corruption/unknown schema/I/O failure, report failure and preserve the original file.
 * Callers pass owned snapshots and serialize saves; implementations must not mutate messages.
 */
interface ChatSessionStore {
    suspend fun load(sessionId: String): ChatSession?
    suspend fun save(session: ChatSession)
}

data class ConversationState(val session: ChatSession, val generation: GenerationState = GenerationState.idle(),
                             val storageError: Boolean = false)

enum class SendRejection { BUSY, EMPTY_INPUT, INPUT_TOO_LONG, NOT_CONFIGURED, INVALID_RETRY, STORAGE_UNAVAILABLE }
sealed interface SendResult {
    data class Accepted(val requestId: String) : SendResult
    data class Rejected(val reason: SendRejection) : SendResult
}

/**
 * UI boundary owned by the conversation module, not the overlay service or composable.
 * send/retry return after accepting or rejecting work, not after the whole response.
 * Only one request runs per conversation; retry replaces the latest retryable failed/cancelled/interrupted
 * answer with a new attempt and request ID, without duplicating its user turn.
 * stop updates visible state immediately, then cancels network work; stale events are ignored.
 */
interface ChatConversation {
    val state: StateFlow<ConversationState>
    suspend fun send(text: String): SendResult
    suspend fun retry(assistantMessageId: String): SendResult
    fun stop(requestId: String)
}
