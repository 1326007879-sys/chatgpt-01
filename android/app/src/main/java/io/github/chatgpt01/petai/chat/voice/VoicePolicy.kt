package io.github.chatgpt01.petai.chat.voice

import io.github.chatgpt01.petai.chat.ChatMessage
import io.github.chatgpt01.petai.chat.MessageRole
import io.github.chatgpt01.petai.chat.MessageStatus

/** These are generated assistant-visible words, never credentials or transport configuration. */
object VoiceText {
    /** Android TTS limits each utterance; never cut between UTF-16 surrogate pairs. */
    fun chunks(text: String, limit: Int): List<String> {
        require(limit >= 2)
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + limit, text.length)
            if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
            result += text.substring(start, end)
            start = end
        }
        return result
    }
}

/** Historical replies are never replayed on page entry, resume, or when enabling auto-read. */
class ReplyReadTracker {
    private var completed: Set<String>? = null
    fun baseline(messages: List<ChatMessage>) {
        completed = completedIds(messages)
    }
    fun observe(messages: List<ChatMessage>, enabled: Boolean, active: Boolean): ChatMessage? {
        val previous = completed
        completed = completedIds(messages)
        if (previous == null || !enabled || !active) return null
        return messages.lastOrNull {
            it.role == MessageRole.ASSISTANT && it.status == MessageStatus.COMPLETED &&
                it.content.isNotBlank() && it.id !in previous
        }
    }
    private fun completedIds(messages: List<ChatMessage>) = messages.filter {
        it.role == MessageRole.ASSISTANT && it.status == MessageStatus.COMPLETED
    }.map { it.id }.toSet()
}

data class SpeechBatch(val ticket: Long, val utteranceIds: List<String>)

/** Playback completion belongs to a batch, not to whichever callback happens to arrive last. */
class SpeechPlayback {
    private var generation = 0L
    private var active: SpeechBatch? = null
    fun begin(chunks: Int): SpeechBatch {
        require(chunks > 0)
        val ticket = ++generation
        return SpeechBatch(ticket, (0 until chunks).map { "petai-$ticket-$it" }).also { active = it }
    }
    fun current(ticket: Long) = active?.ticket == ticket
    fun cancel() { generation++; active = null }
    /** True only for a current error or completion of the final chunk. */
    fun finish(id: String?, error: Boolean): Boolean {
        val batch = active ?: return false
        if (id !in batch.utteranceIds || (!error && id != batch.utteranceIds.last())) return false
        active = null
        return true
    }
}

object VoiceSelection {
    fun resolve(savedName: String?, savedEngine: String?, currentEngine: String?, available: Set<String>): String? =
        savedName?.takeIf { savedEngine == currentEngine && it in available }
}
