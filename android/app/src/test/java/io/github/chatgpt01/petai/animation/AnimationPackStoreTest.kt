package io.github.chatgpt01.petai.animation

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

class AnimationPackStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val id = "ext_" + "a".repeat(24)
    private val pixels = byteArrayOf(1, 2, 3)
    private fun manifest() = PackManifest(1, "webp-frames-v1", "shenshen", id, 1, "睡觉", "Nap",
        listOf(640,360), listOf(212,60,428,330), 24.0, 1, listOf(0,0,640,360), true,
        listOf(MessageDigest.getInstance("SHA-256").digest(pixels).joinToString("") { "%02x".format(it) }))
    private fun archive(pack: PackManifest = manifest(), extra: String? = null, data: ByteArray = pixels): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun entry(name: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
            entry("pack.json", Gson().toJson(pack).toByteArray())
            entry("frames/f_0001.webp", data)
            entry("NOTICE.txt", "author".toByteArray())
            entry("THIRD_PARTY_NOTICES.md", "license".toByteArray())
            if (extra != null) entry(extra, byteArrayOf(1))
        }
        return out.toByteArray()
    }
    private fun failure(reason: String, run: () -> Unit) {
        try { run(); fail("expected $reason") } catch (e: PackFailure) { assertEquals(reason, e.reason) }
    }
    @Test fun onlineCatalogIdentityMustMatchBeforeCommit() {
        val store = AnimationPackStore(tmp.newFolder())
        failure("invalid") { store.installVerified(archive().inputStream(), { true }, "ext_" + "b".repeat(24), 1) {} }
        assertTrue(store.list().isEmpty())
        failure("invalid") { store.installVerified(archive().inputStream(), { true }, id, 2) {} }
        assertTrue(store.list().isEmpty())
        store.installVerified(archive().inputStream(), { true }, id, 1) {}
        assertEquals(id, store.list().single().id)
    }
    @Test fun installIsPersistentAndOnlyExtensionCanBeDeleted() {
        val root = tmp.newFolder(); val store = AnimationPackStore(root)
        store.install(archive().inputStream(), { it.readBytes().contentEquals(pixels) })
        val entry = AnimationPackStore(root).list().single()
        assertEquals("睡觉", entry.manifest!!.name)
        val clips = store.acquire()
        try { assertEquals(id, clips.single().id); assertTrue(clips.single().noMirror) }
        finally { AnimationPackStore.release() }
        failure("invalid") { store.delete("idle_123456789abc") }
        store.delete(id); assertTrue(store.list().isEmpty())
    }
    @Test fun invalidOrEscapingArchivesNeverPublishAnything() {
        val root = tmp.newFolder(); val store = AnimationPackStore(root)
        for (extra in listOf("../outside", "/absolute", "frames/../../outside", "plugin.dex", "frames/")) {
            failure("invalid") { store.install(archive(extra = extra).inputStream(), { true }) }
            assertTrue(store.list().isEmpty())
        }
        failure("invalid") { store.install(archive(data = byteArrayOf(9)).inputStream(), { true }) }
        failure("invalid") { store.install(archive().inputStream(), { false }) }
        failure("incompatible") { store.install(archive(manifest().copy(schema = 2)).inputStream(), { true }) }
        assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun oversizedFrameAndInvalidTimingLeaveNoInstalledData() {
        val store = AnimationPackStore(tmp.newFolder())
        failure("large") { store.install(archive(data = ByteArray(2097153)).inputStream(), { true }) }
        failure("invalid") { store.install(archive(manifest().copy(fps = 0.0)).inputStream(), { true }) }
        assertTrue(store.list().isEmpty())
    }

    @Test fun duplicateKeepsOriginalAndBrokenFramesAreVisibleForRemoval() {
        val root = tmp.newFolder(); val store = AnimationPackStore(root)
        store.install(archive().inputStream(), { true })
        failure("duplicate") { store.install(archive(manifest().copy(version = 2)).inputStream(), { true }) }
        assertEquals(1, store.list().single().manifest!!.version)
        File(root, "$id/frames/f_0001.webp").delete()
        assertNull(store.list().single().manifest)
        assertTrue(store.acquire().isEmpty()); AnimationPackStore.release()
        store.delete(id)
    }
    @Test fun cancellationCleansStagingAndReleasesMutationGuard() {
        val root = tmp.newFolder(); val store = AnimationPackStore(root)
        try { store.install(archive().inputStream(), { true }, { throw java.util.concurrent.CancellationException() })
            fail("expected cancellation")
        } catch (_: java.util.concurrent.CancellationException) { }
        assertTrue(root.listFiles()!!.isEmpty())
        store.install(archive().inputStream(), { true })
    }
    @Test fun installationAndOverlayLeaseExcludeEachOtherByEvents() {
        val store = AnimationPackStore(tmp.newFolder())
        val reached = CountDownLatch(1); val proceed = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor()
        try {
            val importing = worker.submit {
                store.install(archive().inputStream(), { reached.countDown(); check(proceed.await(5, TimeUnit.SECONDS)); true })
            }
            assertTrue(reached.await(5, TimeUnit.SECONDS))
            failure("busy") { store.acquire() }
            failure("busy") { store.delete(id) }
            proceed.countDown(); importing.get(5, TimeUnit.SECONDS)
            store.acquire()
            try {
                failure("busy") { store.delete(id) }
                failure("busy") { store.install(archive().inputStream(), { true }) }
            } finally { AnimationPackStore.release() }
            store.delete(id)
        } finally { proceed.countDown(); worker.shutdownNow() }
    }
}
