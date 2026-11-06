package io.github.chatgpt01.petai.chat

/** Safe, structured errors. Never carry response bodies, credentials, or raw exception messages. */
enum class FailureCode { AUTHENTICATION, RATE_LIMITED, NETWORK, TIMEOUT, SERVER, INVALID_RESPONSE, EMPTY_RESPONSE }
data class ChatFailure(val code: FailureCode, val retryable: Boolean = false)

sealed interface GenerationEvent {
    val requestId: String
    data class Delta(override val requestId: String, val text: String) : GenerationEvent
    data class Completed(override val requestId: String) : GenerationEvent
    data class Failed(override val requestId: String, val failure: ChatFailure) : GenerationEvent
}

enum class GenerationPhase { IDLE, REQUESTING, STREAMING, COMPLETED, CANCELLED, FAILED, INTERRUPTED }

/** Pure transition model; the session owner serializes calls and owns the actual cancellable job. */
@ConsistentCopyVisibility
data class GenerationState private constructor(
    val requestId: String?,
    val phase: GenerationPhase,
    val text: String = "",
    val failure: ChatFailure? = null,
) {
    init {
        require(if (phase == GenerationPhase.IDLE) requestId == null else !requestId.isNullOrBlank())
        require((phase == GenerationPhase.FAILED) == (failure != null))
        require(phase != GenerationPhase.IDLE || text.isEmpty())
        require(phase != GenerationPhase.COMPLETED || text.isNotBlank())
    }

    val isRunning get() = phase == GenerationPhase.REQUESTING || phase == GenerationPhase.STREAMING

    fun apply(event: GenerationEvent): GenerationState {
        if (!isRunning || event.requestId != requestId) return this
        return when (event) {
            is GenerationEvent.Delta -> if (event.text.isEmpty()) this else
                copy(phase = GenerationPhase.STREAMING, text = text + event.text)
            is GenerationEvent.Completed -> if (text.isBlank())
                copy(phase = GenerationPhase.FAILED, failure = ChatFailure(FailureCode.EMPTY_RESPONSE))
                else copy(phase = GenerationPhase.COMPLETED)
            is GenerationEvent.Failed -> copy(phase = GenerationPhase.FAILED, failure = event.failure)
        }
    }

    fun cancel(requestId: String): GenerationState =
        if (isRunning && this.requestId == requestId) copy(phase = GenerationPhase.CANCELLED) else this

    /** Recovery after a lost process, not normal Activity recreation or an automatic retry. */
    fun interrupted(): GenerationState = if (isRunning) copy(phase = GenerationPhase.INTERRUPTED) else this

    companion object {
        fun idle() = GenerationState(null, GenerationPhase.IDLE)
        fun start(requestId: String) = GenerationState(requestId, GenerationPhase.REQUESTING)
    }
}
