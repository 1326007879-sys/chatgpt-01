package io.github.chatgpt01.petai.reminders

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReminderTest {
    private val owner = ReminderOwner("notes/service", "certificate-and-grant-1")
    private val other = ReminderOwner("notes/service", "certificate-and-grant-2")
    private fun at(text: String) = Instant.parse(text).toEpochMilli()
    private fun draft(time: Long = 10000) = ReminderDraft("买东西", time, "Asia/Shanghai")
    private fun fixture(block: (ReminderStore, ReminderCoordinator, MutableList<Long?>) -> Unit) {
        val dir = Files.createTempDirectory("reminders-test")
        try {
            val calls = mutableListOf<Long?>()
            val store = ReminderStore(dir.resolve("reminders.json"))
            block(store, ReminderCoordinator(store) { calls.add(it) }, calls)
        } finally { dir.toFile().deleteRecursively() }
    }
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected invalid input") } catch (_: IllegalArgumentException) { }
    }
    @Test fun onceSurvivesRestartAndAcknowledgesOnlyAfterDelivery() = fixture { store, c, calls ->
        c.save("a", owner, draft(), 1)
        val restarted = ReminderCoordinator(store) { calls.add(it) }
        restarted.reconcile(11000) { true }; assertEquals(11000L, calls.last())
        val token = restarted.due(11000) { true }.single().first
        assertEquals(token, restarted.due(11001) { true }.single().first)
        assertTrue(restarted.acknowledge(token, 11001))
        assertFalse(c.acknowledge(token, 11002))
        restarted.reconcile(11002) { true }; assertNull(calls.last())
        assertTrue(restarted.due(11002) { true }.isEmpty())
    }
    @Test fun editsCancellationAndRecreationRejectStaleDelivery() = fixture { _, c, _ ->
        c.save("a", owner, draft(), 1)
        val old = c.due(10000) { true }.single().first
        c.save("a", owner, draft(20000), 2)
        assertFalse(c.acknowledge(old, 10000))
        c.cancel("a", owner)
        c.save("a", owner, draft(), 3)
        assertFalse(c.acknowledge(old, 10000))
        assertEquals(1, c.due(10000) { true }.size)
    }
    @Test fun authorizationAndRevocationNeverExposeAnotherOwnersReminder() = fixture { _, c, calls ->
        c.save("a", owner, draft(), 1)
        assertTrue(c.list(other).isEmpty()); assertFalse(c.cancel("a", other))
        rejected { c.save("a", other, draft(), 1) }
        c.reconcile(1) { it == other }; assertNull(calls.last())
        assertTrue(c.due(10000) { it == other }.isEmpty())
        assertEquals(1, c.revoke(owner)); c.reconcile(1) { true }; assertNull(calls.last())
    }
    @Test fun dailySkipsBacklogAndPreservesChosenZone() = fixture { _, c, _ ->
        val first = at("2026-11-11T05:00:00Z")
        c.save("daily", owner, draft(first).copy(repeat = ReminderRepeat.DAILY, localTime = "13:00"), first - 1)
        val late = at("2026-11-15T06:00:00Z")
        assertTrue(c.acknowledge(c.due(late) { true }.single().first, late))
        assertEquals(at("2026-11-16T05:00:00Z"), c.list(owner).single().nextAt)
    }
    @Test fun daylightSavingGapAndOverlapFireOncePerLocalDay() {
        val gap = ReminderDraft("gap", at("2026-03-08T07:30:00Z"), "America/New_York", ReminderRepeat.DAILY, "02:30")
        assertEquals(at("2026-03-08T07:30:00Z"), gap.after(at("2026-03-08T05:00:00Z")))
        assertEquals(at("2026-03-09T06:30:00Z"), gap.after(at("2026-03-08T07:30:00Z")))
        val overlap = ReminderDraft("overlap", at("2026-11-01T05:30:00Z"), "America/New_York", ReminderRepeat.DAILY, "01:30")
        assertEquals(at("2026-11-02T06:30:00Z"), overlap.after(at("2026-11-01T05:30:00Z")))
    }
    @Test fun persistencePreservesCompletedRowsAndVoicePreferences() = fixture { store, c, _ ->
        c.save("a", owner, draft().copy(speak = true, voiceId = "device-voice"), 1)
        c.acknowledge(c.due(10000) { true }.single().first, 10000)
        val row = ReminderCoordinator(store) { }.list(owner).single()
        assertTrue(row.draft.speak); assertEquals("device-voice", row.draft.voiceId); assertNull(row.nextAt)
    }
    @Test fun invalidInputsAreRejectedAndClockCannotConsumeFutureEvent() = fixture { _, c, _ ->
        rejected { draft().copy(text = " ") }; rejected { draft().copy(voiceProvider = "untrusted.plugin") }
        rejected { draft().copy(localTime = "25:00") }
        rejected { c.save("a", owner, draft(), 10000) }
        c.save("a", owner, draft(), 1)
        assertFalse(c.acknowledge(c.due(10000) { true }.single().first, 9999))
    }
    @Test fun schedulingFailurePreservesDurableReminderForRetry() = fixture { store, c, _ ->
        c.save("a", owner, draft(), 1)
        try { ReminderCoordinator(store) { throw IOException("alarm unavailable") }.reconcile(1) { true }; fail() }
        catch (_: IOException) { }
        assertEquals(1, c.list(owner).size)
    }
    @Test fun failedDeliveryRemainsPendingAndAuthorizationIsRechecked() = fixture { _, c, _ ->
        c.save("a", owner, draft(), 1)
        val token = c.due(10000) { true }.single().first
        assertFalse(c.deliver(token, 10000, { false }) { fail("Unauthorized notification") })
        try { c.deliver(token, 10000, { true }) { throw IOException("notification blocked") }; fail() }
        catch (_: IOException) { }
        assertEquals(token, c.due(10000) { true }.single().first)
        assertTrue(c.deliver(token, 10000, { true }) { assertEquals("买东西", it.draft.text) })
        assertFalse(c.deliver(token, 10000, { true }) { fail("Duplicate notification") })
    }
    @Test fun cancellationFromAnotherRepositoryInvalidatesQueuedDelivery() = fixture { store, c, _ ->
        c.save("a", owner, draft(), 1)
        val token = c.due(10000) { true }.single().first
        val pool = Executors.newSingleThreadExecutor()
        val cancelled = CountDownLatch(1)
        try {
            val task = pool.submit { ReminderCoordinator(store) { }.cancel("a", owner); cancelled.countDown() }
            assertTrue(cancelled.await(10, TimeUnit.SECONDS)); task.get(10, TimeUnit.SECONDS)
            assertFalse(c.deliver(token, 10000, { true }) { fail("Cancelled notification") })
        } finally { pool.shutdownNow() }
    }
    @Test fun corruptedOrFutureSchemaIsPreservedAndNotOverwritten() {
        val dir = Files.createTempDirectory("reminder-corruption")
        try {
            val path = dir.resolve("reminders.json")
            for (data in listOf("broken", "{\"schema\":1.5,\"items\":[]}", "{\"schema\":2,\"items\":[]}")) {
                Files.write(path, data.toByteArray())
                try { ReminderCoordinator(ReminderStore(path)) { }.save("a", owner, draft(), 1); fail() }
                catch (_: IOException) { }
                assertEquals(data, String(Files.readAllBytes(path)))
            }
        } finally { dir.toFile().deleteRecursively() }
    }
    @Test fun concurrentRepositoriesDoNotLoseWrites() {
        val dir = Files.createTempDirectory("reminder-concurrent")
        val pool = Executors.newFixedThreadPool(4)
        try {
            val start = CountDownLatch(1)
            val tasks = (1..24).map { index -> pool.submit {
                assertTrue(start.await(10, TimeUnit.SECONDS))
                ReminderCoordinator(ReminderStore(dir.resolve("reminders.json"))) { }.save("a$index", owner, draft(), 1)
            } }
            start.countDown(); tasks.forEach { it.get(30, TimeUnit.SECONDS) }
            assertEquals(24, ReminderCoordinator(ReminderStore(dir.resolve("reminders.json"))) { }.list(owner).size)
        } finally { pool.shutdownNow(); dir.toFile().deleteRecursively() }
    }
}
