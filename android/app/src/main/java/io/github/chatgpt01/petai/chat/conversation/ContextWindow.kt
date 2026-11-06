package io.github.chatgpt01.petai.chat.conversation

import io.github.chatgpt01.petai.chat.*

/** Select complete adjacent turns, newest first; never include an orphaned or partial answer. */
object ContextWindow {
    const val MAX_INPUT_CHARS = 12000
    fun select(history: List<ChatMessage>, user: ChatMessage, maxChars: Int = MAX_INPUT_CHARS,
               maxPairs: Int = 12): List<ChatMessage> {
        require(user.role == MessageRole.USER && user.status == MessageStatus.COMPLETED)
        require(user.content.length <= maxChars && maxPairs >= 0)
        val pairs = history.zipWithNext().filter { (a, b) ->
            a.role == MessageRole.USER && b.role == MessageRole.ASSISTANT &&
                a.status == MessageStatus.COMPLETED && b.status == MessageStatus.COMPLETED
        }
        var remaining = maxChars - user.content.length
        val selected = ArrayList<List<ChatMessage>>()
        for ((a, b) in pairs.asReversed()) {
            if (selected.size >= maxPairs) break
            val size = a.content.length.toLong() + b.content.length
            if (size > remaining) break
            selected.add(listOf(a, b)); remaining -= size.toInt()
        }
        return selected.asReversed().flatten() + user
    }
}
