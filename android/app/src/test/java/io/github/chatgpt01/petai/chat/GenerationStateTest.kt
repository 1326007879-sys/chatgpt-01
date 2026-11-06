package io.github.chatgpt01.petai.chat

import org.junit.Assert.*
import org.junit.Test

class GenerationStateTest {
    @Test fun streamedChineseIsAppendedUntilSuccessfulCompletion() {
        val waiting = GenerationState.start("r1")
        assertTrue(waiting.isRunning)
        val state = waiting.apply(GenerationEvent.Delta("r1", "你"))
            .apply(GenerationEvent.Delta("r1", "好🐳"))
            .apply(GenerationEvent.Completed("r1"))
        assertEquals("你好🐳", state.text)
        assertEquals(GenerationPhase.COMPLETED, state.phase)
        assertFalse(state.isRunning)
    }

    @Test fun emptyChunksAndCallbacksFromPreviousRequestsCannotChangeState() {
        val state = GenerationState.start("new")
        assertSame(state, state.apply(GenerationEvent.Delta("new", "")))
        assertSame(state, state.apply(GenerationEvent.Delta("old", "过期回复")))
        assertSame(state, state.apply(GenerationEvent.Completed("old")))
        assertSame(state, state.cancel("old"))
    }

    @Test fun cancellationKeepsPartialTextAndRejectsLateCompletion() {
        val state = GenerationState.start("r1").apply(GenerationEvent.Delta("r1", "部分"))
            .cancel("r1")
        assertEquals(GenerationPhase.CANCELLED, state.phase)
        assertEquals("部分", state.text)
        assertSame(state, state.apply(GenerationEvent.Completed("r1")))
        assertSame(state, state.apply(GenerationEvent.Delta("r1", "不应加入")))
    }

    @Test fun failedGenerationKeepsPartialTextAndUsesStructuredError() {
        val error = ChatFailure(FailureCode.RATE_LIMITED, retryable = true)
        val state = GenerationState.start("r1").apply(GenerationEvent.Delta("r1", "部分"))
            .apply(GenerationEvent.Failed("r1", error))
        assertEquals(GenerationPhase.FAILED, state.phase)
        assertEquals(error, state.failure)
        assertEquals("部分", state.text)
        assertSame(state, state.apply(GenerationEvent.Completed("r1")))
    }

    @Test fun successfulEndWithoutTextIsAnEmptyResponseFailure() {
        val state = GenerationState.start("r1").apply(GenerationEvent.Delta("r1", " \n"))
            .apply(GenerationEvent.Completed("r1"))
        assertEquals(GenerationPhase.FAILED, state.phase)
        assertEquals(FailureCode.EMPTY_RESPONSE, state.failure?.code)
    }

    @Test fun processRecoveryMarksActiveGenerationInterruptedWithoutInventingAnAnswer() {
        val state = GenerationState.start("r1").apply(GenerationEvent.Delta("r1", "部分"))
            .interrupted()
        assertEquals(GenerationPhase.INTERRUPTED, state.phase)
        assertEquals("部分", state.text)
        assertFalse(state.isRunning)
        assertSame(state, state.interrupted())
        assertSame(state, state.apply(GenerationEvent.Completed("r1")))
    }

    @Test fun terminalStatesAndIdleIgnoreSubsequentEvents() {
        val idle = GenerationState.idle()
        assertSame(idle, idle.apply(GenerationEvent.Completed("r1")))
        assertFalse(idle.isRunning)
        val done = GenerationState.start("r1").apply(GenerationEvent.Delta("r1", "回答"))
            .apply(GenerationEvent.Completed("r1"))
        assertSame(done, done.cancel("r1"))
        assertSame(done, done.apply(GenerationEvent.Failed("r1", ChatFailure(FailureCode.NETWORK))))
    }
}
