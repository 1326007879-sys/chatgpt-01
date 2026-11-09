package io.github.chatgpt01.petai

import org.junit.Assert.*
import org.junit.Test

class PetEngineTest {
    private val clips = listOf(
        ClipSpec("idle", "idle", 24.0, 24),
        ClipSpec("turn", "turn", 24.0, 12),
        ClipSpec("walk", "move", 24.0, 24, stride = 120.0),
        ClipSpec("click", "click", 24.0, 12),
        ClipSpec("drag", "drag", 24.0, 24),
    )
    private fun engine() = PetEngine(clips, random = kotlin.random.Random(7)).apply {
        resize(300.0, 600.0)
        restorePosition(.5, .5)
    }

    @Test fun clickPlaysOnceThenReturnsToIdle() {
        val e = engine()
        e.click()
        assertEquals(PetMode.CLICK, e.mode)
        repeat(20) { e.advance(.05) }
        assertEquals(PetMode.IDLE, e.mode)
    }

    @Test fun draggingOwnsPositionAndReleaseClampsSpeed() {
        val e = engine()
        e.beginDrag()
        e.dragTo(1000.0, -200.0)
        e.advance(.05)
        assertEquals(300.0, e.x, .001)
        assertEquals(0.0, e.y, .001)
        e.release(100000.0, -100000.0)
        assertEquals(PetMode.THROWN, e.mode)
        assertTrue(kotlin.math.hypot(e.vx, e.vy) <= 4800.01)
    }

    @Test fun throwSettlesAndAlwaysStaysInBounds() {
        val e = engine()
        e.beginDrag()
        e.release(650.0, -500.0)
        repeat(1200) {
            e.advance(1.0 / 60)
            assertTrue(e.x in 0.0..300.0 && e.y in 0.0..600.0)
        }
        assertNotEquals(PetMode.THROWN, e.mode)
    }

    @Test fun pauseAndScreenOffFreezeWithoutCatchingUp() {
        val e = engine()
        e.beginDrag()
        e.release(300.0, 200.0)
        e.paused = true
        val x = e.x
        val y = e.y
        e.advance(60.0)
        assertEquals(x, e.x, 0.0)
        assertEquals(y, e.y, 0.0)
        e.paused = false
        e.advance(.016)
        assertTrue(kotlin.math.abs(e.x - x) < 10)
    }

    @Test fun rotationPreservesRelativePositionAndHandlesZeroBounds() {
        val e = engine()
        e.restorePosition(.25, .75)
        e.resize(600.0, 300.0)
        assertEquals(150.0, e.x, .001)
        assertEquals(225.0, e.y, .001)
        e.resize(0.0, 0.0)
        assertEquals(0.0, e.x, 0.0)
        assertEquals(0.0, e.y, 0.0)
    }

    @Test fun disablingWanderKeepsPetStillAfterIdleCycles() {
        val e = engine()
        e.wander = false
        val x = e.x
        repeat(300) { e.advance(.05) }
        assertEquals(x, e.x, 0.0)
    }

    @Test fun desktopThrowReferenceMatchesMobileIntegration() {
        val result = ThrowMath.step(100.0, 200.0, 400.0, -300.0, .016,
            300.0, 600.0)
        assertEquals(106.4, result.x, .0001)
        assertEquals(195.5584, result.y, .0001)
        assertEquals(-277.6, result.vy, .0001)
    }

    @Test fun everyBundledActionPlaysOnceInPlaceThenIdles() {
        for (clip in clips) {
            val e = engine()
            val x = e.x; val y = e.y
            assertTrue(e.playAction(clip.id))
            assertEquals(clip, e.clip)
            assertEquals(PetMode.MANUAL, e.mode)
            assertEquals(0, e.frameIndex)
            repeat((clip.duration / .05).toInt() - 1) { e.advance(.05) }
            assertEquals(PetMode.MANUAL, e.mode)
            assertEquals(x, e.x, 0.0)
            assertEquals(y, e.y, 0.0)
            repeat(3) { e.advance(.05) }
            assertEquals(PetMode.IDLE, e.mode)
            assertEquals("idle", e.clip.group)
        }
    }

    @Test fun manualSelectionStopsThrowAndDoesNotLeakVelocityOrPendingWalk() {
        val e = engine()
        e.beginDrag(); e.release(1200.0, -800.0)
        assertTrue(e.playAction("walk"))
        val x = e.x; val y = e.y
        assertEquals(0.0, e.vx, 0.0)
        assertEquals(0.0, e.vy, 0.0)
        repeat(10) { e.advance(.05) }
        assertEquals(x, e.x, 0.0)
        assertEquals(y, e.y, 0.0)
        e.stopWandering()
        assertEquals(PetMode.MANUAL, e.mode)
        repeat(20) { e.advance(.05) }
        assertEquals(PetMode.IDLE, e.mode)
    }

    @Test fun repeatedSelectionRestartsAndDifferentSelectionReplacesIt() {
        val e = engine()
        e.playAction("walk")
        repeat(4) { e.advance(.05) }
        assertTrue(e.frameIndex > 0)
        assertTrue(e.playAction("walk"))
        assertEquals(0, e.frameIndex)
        assertTrue(e.playAction("click"))
        assertEquals("click", e.clip.id)
        assertEquals(0, e.frameIndex)
    }

    @Test fun invalidPausedAndDraggingSelectionsPreserveState() {
        val e = engine()
        e.playAction("walk"); e.advance(.05)
        val index = e.frameIndex
        assertFalse(e.playAction("not-installed"))
        assertEquals("walk", e.clip.id)
        assertEquals(index, e.frameIndex)
        e.paused = true
        assertFalse(e.playAction("click"))
        e.advance(30.0)
        assertEquals(index, e.frameIndex)
        e.paused = false
        e.advance(.05)
        assertTrue(e.frameIndex > index)
        e.beginDrag()
        assertFalse(e.playAction("click"))
        assertEquals(PetMode.DRAG, e.mode)
    }

    @Test fun gesturesAndCollisionsInterruptManualActionWithoutLaterResumingIt() {
        val e = engine()
        e.playAction("walk"); e.click()
        assertEquals(PetMode.CLICK, e.mode)
        repeat(12) { e.advance(.05) }
        assertEquals(PetMode.IDLE, e.mode)
        e.playAction("click"); e.beginDrag()
        assertEquals(PetMode.DRAG, e.mode)
        e.release(0.0, 0.0)
        assertEquals(PetMode.IDLE, e.mode)
        e.playAction("walk"); e.nudge(0.0, 0.0, 900.0, -800.0)
        assertEquals(PetMode.THROWN, e.mode)
        e.wander = false
        repeat(1500) { e.advance(.05) }
        assertEquals(PetMode.IDLE, e.mode)
    }

    @Test fun manualActionBelongsOnlyToTheSelectedPet() {
        val mother = engine(); val companion = engine()
        assertTrue(companion.playAction("click"))
        assertEquals(PetMode.IDLE, mother.mode)
        assertEquals(PetMode.MANUAL, companion.mode)
    }
}
