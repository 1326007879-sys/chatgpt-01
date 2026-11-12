package io.github.chatgpt01.voice

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class VoicePacksTest {
    private fun wav(): ByteArray {
        val b = ByteBuffer.allocate(48044).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(48036).put("WAVEfmt ".toByteArray()).putInt(16)
        b.putShort(1).putShort(1).putInt(24000).putInt(48000).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(48000)
        return b.array()
    }
    private fun pack(id: String="candidate", payload: ByteArray=wav(), extra: String?=null, badHash: Boolean=false): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        val header = Gson().toJson(mapOf("schema" to 1,"kind" to "voice","engine" to VoicePacks.ENGINE,"model" to VoicePacks.MODEL,
            "id" to id,"name" to "Test","referenceText" to "测试文本","license" to "Test data only",
            "files" to mapOf("reference.wav" to if(badHash) "0".repeat(64) else hash)))
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(header.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("reference.wav")); zip.write(payload); zip.closeEntry()
            if(extra!=null) { zip.putNextEntry(ZipEntry(extra)); zip.write(byteArrayOf(1)); zip.closeEntry() }
        }
        return out.toByteArray()
    }
    @Test fun failedImportsPreserveInstalledVoiceAndLeaveNoStagingFiles() {
        val root=Files.createTempDirectory("voice-packs")
        try {
            val store=VoicePacks(root)
            store.importPack(ByteArrayInputStream(pack()))
            for (bad in listOf(pack(id="../escape"),pack(id="broken",badHash=true),pack(id="badwav",payload=byteArrayOf(1)),pack(id="extra",extra="../escape"))) {
                assertThrows(Exception::class.java) { store.importPack(ByteArrayInputStream(bad)) }
                assertEquals(listOf("candidate"),store.listVoices().map { it.id })
                Files.list(root).use { assertEquals(1L,it.count()) }
            }
            assertThrows(Exception::class.java) { store.importPack(ByteArrayInputStream(pack())) }
            assertEquals(24000,store.readReference(root.resolve("voice_candidate/reference.wav")).size)
        } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
    @Test fun disabledByDefaultAndAtomicChoiceSurvivesRecreation() {
        val root=Files.createTempDirectory("voice-choice")
        try {
            assertFalse(EngineState(root).read().enabled)
            EngineState(root).write(EngineChoice(true,"candidate"))
            assertEquals(EngineChoice(true,"candidate"),EngineState(root).read())
            Files.write(root.resolve("engine.json"),"{broken".toByteArray())
            assertThrows(Exception::class.java) { EngineState(root).read() }
        } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
    @Test fun simultaneousImportsDoNotDeleteOneAnothersStagingDirectories() {
        val root=Files.createTempDirectory("voice-concurrent")
        val pool=Executors.newFixedThreadPool(8)
        val start=CyclicBarrier(8)
        try {
            val tasks=(0 until 8).map { index -> pool.submit<String> {
                start.await(10,TimeUnit.SECONDS)
                VoicePacks(root).importPack(ByteArrayInputStream(pack(id="voice-$index"))).id
            } }
            assertEquals((0 until 8).map { "voice-$it" }.toSet(),tasks.map { it.get(15,TimeUnit.SECONDS) }.toSet())
            assertEquals(8,VoicePacks(root).listVoices().size)
            Files.list(root).use { assertEquals(8L,it.count()) }
        } finally { pool.shutdownNow(); Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
    @Test fun interruptedImportAndOrphanRecoveryPreserveInstalledPacks() {
        val root=Files.createTempDirectory("voice-interrupt")
        try {
            val store=VoicePacks(root)
            store.importPack(ByteArrayInputStream(pack()))
            val orphan=Files.createDirectory(root.resolve(".import-"+java.util.UUID.randomUUID()))
            Files.write(orphan.resolve("partial"),byteArrayOf(1))
            Thread.currentThread().interrupt()
            try { assertThrows(Exception::class.java) { store.importPack(ByteArrayInputStream(pack(id="interrupted"))) } }
            finally { Thread.interrupted() }
            store.importPack(ByteArrayInputStream(pack(id="next")))
            assertFalse(Files.exists(orphan))
            assertEquals(setOf("candidate","next"),store.listVoices().map { it.id }.toSet())
        } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
}
