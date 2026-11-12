package io.github.chatgpt01.voice

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

data class EngineChoice(val enabled: Boolean = false, val voice: String = "") {
    init {
        require(voice.isEmpty() || voice.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")))
        require(!enabled || voice.isNotEmpty())
    }
}

class EngineState(private val root: Path) {
    val packs = VoicePacks(root.resolve("packs"))
    private val file = root.resolve("engine.json")
    fun read(): EngineChoice {
        if (!Files.exists(file)) return EngineChoice()
        require(Files.size(file) <= 1024)
        val o = JsonParser.parseString(String(Files.readAllBytes(file),Charsets.UTF_8)).asJsonObject
        require(o.keySet() == setOf("enabled", "voice") && o["enabled"].asJsonPrimitive.isBoolean)
        val id = o["voice"].asString
        require(id.isEmpty() || id.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")))
        return EngineChoice(o["enabled"].asBoolean, id)
    }
    fun write(choice: EngineChoice) {
        Files.createDirectories(root)
        val tmp = Files.createTempFile(root, "choice-", ".tmp")
        try {
            val bytes = Gson().toJson(choice).toByteArray(Charsets.UTF_8)
            FileChannel.open(tmp, StandardOpenOption.WRITE).use { c ->
                val b = ByteBuffer.wrap(bytes)
                while (b.hasRemaining()) c.write(b)
                c.force(true)
            }
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(tmp) }
    }
    fun available(): List<PackInfo> = if (Files.exists(packs.directory("model", VoicePacks.MODEL))) packs.listVoices() else emptyList()
}
