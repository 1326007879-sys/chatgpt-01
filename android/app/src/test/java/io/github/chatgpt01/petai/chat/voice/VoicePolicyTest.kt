package io.github.chatgpt01.petai.chat.voice

import io.github.chatgpt01.petai.chat.*
import org.junit.Assert.*
import org.junit.Test

class VoicePolicyTest {
    @Test fun deletedNeuralVoiceFailsInsteadOfAutomaticallyUsingAnotherVoice() {
        val neural=NeuralEngine.PACKAGE
        val available=setOf("petai.neural.new")
        assertFalse(VoiceSelection.engineReady(neural,"petai.neural.deleted",neural,neural,available))
        assertFalse(VoiceSelection.engineReady(neural,null,null,neural,emptySet()))
        assertTrue(VoiceSelection.engineReady(neural,"system-voice","system-engine",neural,available))
        assertTrue(VoiceSelection.engineReady(null,"deleted-system-voice","system-engine","system-engine",setOf("default")))
    }
    @Test fun neuralEngineCannotAcceptAnAutomaticFallbackSystemVoice() {
        assertFalse(NeuralEngine.accepts(NeuralEngine.PACKAGE,"zh-cn-system"))
        assertTrue(NeuralEngine.accepts(NeuralEngine.PACKAGE,"petai.neural.candidate"))
        assertTrue(NeuralEngine.accepts(null,"zh-cn-system"))
    }
    private fun reply(id: String, status: MessageStatus = MessageStatus.COMPLETED) =
        ChatMessage(id, MessageRole.ASSISTANT, if (status == MessageStatus.REQUESTING) "" else "你好", 1, status)

    @Test fun longSpeechChunksPreserveChineseEmojiAndInputExactly() {
        val text = "a😀中文\n".repeat(8000)
        for (limit in listOf(2, 3, 100, 4000)) {
            val chunks = VoiceText.chunks(text, limit)
            assertEquals(text, chunks.joinToString(""))
            assertTrue(chunks.all { it.isNotEmpty() && it.length <= limit })
            assertTrue(chunks.none { it.first().isLowSurrogate() || it.last().isHighSurrogate() })
        }
        assertTrue(VoiceText.chunks("", 4000).isEmpty())
    }

    @Test fun historicalAndStreamingRepliesDoNotReadUntilNewCompletion() {
        val tracker = ReplyReadTracker()
        val old = reply("old")
        assertNull(tracker.observe(listOf(old), true, true))
        val loading = reply("new", MessageStatus.REQUESTING)
        assertNull(tracker.observe(listOf(old, loading), true, true))
        assertNull(tracker.observe(listOf(old, reply("new", MessageStatus.STREAMING)), true, true))
        assertEquals("new", tracker.observe(listOf(old, reply("new")), true, true)?.id)
        assertNull(tracker.observe(listOf(old, reply("new")), true, true))
    }

    @Test fun disabledAndBackgroundCompletionsAreNotReplayedLater() {
        val tracker = ReplyReadTracker()
        tracker.baseline(emptyList())
        val first = reply("first")
        assertNull(tracker.observe(listOf(first), false, true))
        assertNull(tracker.observe(listOf(first), true, true))
        val second = reply("second")
        assertNull(tracker.observe(listOf(first, second), true, false))
        assertNull(tracker.observe(listOf(first, second), true, true))
        assertEquals("third", tracker.observe(listOf(first, second, reply("third")), true, true)?.id)
    }

    @Test fun resumeBaselinePreventsCatchupAndCancelledTextIsNeverAutomaticallyRead() {
        val tracker = ReplyReadTracker()
        tracker.baseline(emptyList())
        val messages = listOf(reply("cancelled", MessageStatus.CANCELLED), reply("interrupted", MessageStatus.INTERRUPTED))
        assertNull(tracker.observe(messages, true, true))
        tracker.baseline(messages + reply("completed-while-away"))
        assertNull(tracker.observe(messages + reply("completed-while-away"), true, true))
        assertEquals("fresh", tracker.observe(messages + reply("completed-while-away") + reply("fresh"), true, true)?.id)
    }

    @Test fun userMessagesAreNeverAutomaticallyRead() {
        val tracker = ReplyReadTracker()
        tracker.baseline(emptyList())
        assertNull(tracker.observe(listOf(ChatMessage("u", MessageRole.USER, "你好", 1)), true, true))
    }

    @Test fun intermediateChunkDoesNotReleasePlaybackButFinalChunkDoes() {
        val playback = SpeechPlayback()
        val batch = playback.begin(3)
        assertFalse(playback.finish(batch.utteranceIds[0], false))
        assertFalse(playback.finish(batch.utteranceIds[1], false))
        assertTrue(playback.current(batch.ticket))
        assertTrue(playback.finish(batch.utteranceIds.last(), false))
        assertFalse(playback.current(batch.ticket))
        assertFalse(playback.finish(batch.utteranceIds.last(), false))
    }

    @Test fun errorOnAnyCurrentChunkStopsBatchButLateErrorsDoNotStopNextOne() {
        val playback = SpeechPlayback()
        val first = playback.begin(4)
        assertTrue(playback.finish(first.utteranceIds[1], true))
        val next = playback.begin(1)
        assertFalse(playback.finish(first.utteranceIds.last(), true))
        assertFalse(playback.finish(null, true))
        assertTrue(playback.current(next.ticket))
    }

    @Test fun cancelledPlaybackCallbacksAndOldFocusEventsCannotAffectNewPreviewUnderLoad() {
        val playback = SpeechPlayback()
        repeat(2000) {
            val old = playback.begin(3)
            playback.cancel() // page leaves, user stops, or focus is lost
            val preview = playback.begin(1)
            assertFalse(playback.current(old.ticket)) // old audio-focus callback
            for (id in old.utteranceIds.reversed()) {
                assertFalse(playback.finish(id, false))
                assertFalse(playback.finish(id, true))
            }
            assertTrue(playback.current(preview.ticket))
            assertTrue(playback.finish(preview.utteranceIds.single(), false))
        }
    }

    @Test fun selectingAnEngineScopedVoiceFallsBackWhenEngineOrInstalledDataChanges() {
        assertEquals("zh", VoiceSelection.resolve("zh", "engineA", "engineA", setOf("zh")))
        assertNull(VoiceSelection.resolve("zh", "engineA", "engineB", setOf("zh")))
        assertNull(VoiceSelection.resolve("zh", "engineA", "engineA", emptySet()))
        assertNull(VoiceSelection.resolve(null, "engineA", "engineA", setOf("zh")))
    }
}
