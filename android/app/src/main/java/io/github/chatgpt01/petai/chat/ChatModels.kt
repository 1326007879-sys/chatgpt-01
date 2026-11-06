package io.github.chatgpt01.petai.chat

/** Persist these names, never enum ordinals. System instructions belong to the request. */
enum class MessageRole { USER, ASSISTANT }
enum class MessageStatus { REQUESTING, STREAMING, COMPLETED, CANCELLED, FAILED, INTERRUPTED }

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val createdAtEpochMs: Long,
    val status: MessageStatus = MessageStatus.COMPLETED,
    val failure: ChatFailure? = null,
) {
    init {
        require(id.isNotBlank()) { "Message ID is required" }
        require(createdAtEpochMs >= 0) { "Invalid message timestamp" }
        require(role == MessageRole.ASSISTANT || status == MessageStatus.COMPLETED) {
            "User messages must be complete"
        }
        require(status != MessageStatus.COMPLETED || content.isNotBlank()) {
            "Completed messages must contain text"
        }
        require((status == MessageStatus.FAILED) == (failure != null)) {
            "Only failed messages carry a structured failure"
        }
    }
}

/** Main-pet session in phase 1; character identity is independent of the display name. */
data class ChatSession(
    val id: String,
    val characterId: String,
    val messages: List<ChatMessage>,
    val schemaVersion: Int = 1,
) {
    init {
        require(schemaVersion == 1) { "Unsupported chat session schema" }
        require(id.isNotBlank() && characterId.isNotBlank()) { "Session identity is required" }
        require(messages.map { it.id }.distinct().size == messages.size) { "Duplicate message IDs" }
    }
}

/** Snapshot created by the session owner after context selection, before network I/O. */
data class ChatRequest(
    val requestId: String,
    val config: ChatModelConfig,
    val systemPrompt: String,
    val messages: List<ChatMessage>,
) {
    init {
        require(requestId.isNotBlank()) { "Request ID is required" }
        require(messages.isNotEmpty() && messages.last().role == MessageRole.USER) {
            "A request must end with a user message"
        }
        require(messages.all { it.status == MessageStatus.COMPLETED }) {
            "Unfinished answers must not enter model context"
        }
        require(messages.map { it.id }.distinct().size == messages.size) { "Duplicate message IDs" }
    }
}
