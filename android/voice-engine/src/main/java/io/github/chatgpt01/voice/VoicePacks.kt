package io.github.chatgpt01.voice

import com.google.gson.JsonParser
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import java.util.stream.Collectors
import java.util.concurrent.ConcurrentHashMap

data class PackInfo(val id: String, val kind: String, val name: String, val referenceText: String,
                    val files: Map<String, String>)

/** Data-only, immutable packs. No extraction paths come from unchecked archive names. */
class VoicePacks(private val root: Path) {
    companion object {
        const val MODEL = "zipvoice-distill-int8-v1"
        const val ENGINE = "sherpa-zipvoice-v1"
        private val importLocks = ConcurrentHashMap<Path,Any>()
        val MODEL_FILES = setOf("encoder.int8.onnx", "decoder.int8.onnx", "vocos_24khz.onnx", "tokens.txt", "lexicon.txt", "lexicon-en-us.txt")
        fun manifest(bytes: ByteArray): PackInfo {
            require(bytes.size <= 65536)
            val o = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
            require(o.keySet() == setOf("schema", "kind", "engine", "model", "id", "name", "referenceText", "license", "files"))
            require(o["schema"].asString == "1" && o["engine"].asString == ENGINE && o["model"].asString == MODEL)
            val id = o["id"].asString
            require(id.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")))
            val kind = o["kind"].asString
            require(kind in setOf("model", "voice"))
            require(kind != "model" || id == MODEL)
            val name = o["name"].asString
            require(name.isNotBlank() && name.length <= 80)
            require(o["license"].asString.length in 1..4000)
            val ref = o["referenceText"].asString
            require(if (kind == "voice") ref.isNotBlank() && ref.length <= 500 else ref.isEmpty())
            val files = o["files"].asJsonObject.entrySet().associate { it.key to it.value.asString }
            require(files.keys == if (kind == "model") MODEL_FILES else setOf("reference.wav"))
            require(files.values.all { it.matches(Regex("[0-9a-f]{64}")) })
            return PackInfo(id, kind, name, ref, files)
        }
    }

    fun directory(kind: String, id: String): Path {
        require(kind in setOf("voice", "model") && id.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")))
        return root.resolve("${kind}_$id")
    }
    fun listVoices(): List<PackInfo> {
        if (!Files.exists(root)) return emptyList()
        return Files.list(root).use { paths -> paths.filter { it.fileName.toString().startsWith("voice_") }
            .map { manifest(Files.readAllBytes(it.resolve("manifest.json"))) }.collect(Collectors.toList()) }
    }
    fun importPack(input: InputStream): PackInfo = synchronized(importLocks.computeIfAbsent(root.toAbsolutePath().normalize()) { Any() }) {
        Files.createDirectories(root)
        check(!Thread.currentThread().isInterrupted)
        Files.list(root).use { paths -> paths.filter { it.fileName.toString().matches(Regex("\\.import-[0-9a-f-]{36}")) }.forEach { orphan ->
            Files.walk(orphan).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        } }
        val stage = Files.createDirectory(root.resolve(".import-${UUID.randomUUID()}"))
        try {
            lateinit var info: PackInfo
            ZipInputStream(input).use { zip ->
                val first = zip.nextEntry ?: error("Empty pack")
                require(first.name == "manifest.json" && !first.isDirectory)
                val headerOut = java.io.ByteArrayOutputStream()
                val headerBuffer = ByteArray(4096)
                while (true) {
                    val n = zip.read(headerBuffer)
                    if (n < 0) break
                    require(headerOut.size()+n <= 65536)
                    headerOut.write(headerBuffer,0,n)
                }
                val header = headerOut.toByteArray()
                info = manifest(header)
                require(info.kind != "voice" || listVoices().size < 32)
                Files.write(stage.resolve("manifest.json"), header)
                val seen = mutableSetOf<String>()
                var total = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && entry.name in info.files && seen.add(entry.name))
                    val digest = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    Files.newOutputStream(stage.resolve(entry.name)).use { out ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            check(!Thread.currentThread().isInterrupted)
                            val n = zip.read(buffer)
                            if (n < 0) break
                            size += n; total += n
                            require(size <= if (info.kind == "voice") 1_500_000 else 256L * 1024 * 1024)
                            require(total <= 512L * 1024 * 1024)
                            digest.update(buffer, 0, n); out.write(buffer, 0, n)
                        }
                    }
                    require(size > 0 && digest.digest().joinToString("") { "%02x".format(it) } == info.files[entry.name])
                }
                require(seen == info.files.keys)
            }
            if (info.kind == "voice") readReference(stage.resolve("reference.wav"))
            val destination = directory(info.kind, info.id)
            require(!Files.exists(destination)) { "Pack already installed" }
            Files.move(stage, destination, StandardCopyOption.ATOMIC_MOVE)
            info
        } finally {
            if (Files.exists(stage)) Files.walk(stage).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    /** A deliberately narrow PCM16 mono 24kHz WAV format; no compressed or executable assets. */
    fun readReference(path: Path): FloatArray {
        require(Files.size(path) in 44..1_500_000)
        val b = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN)
        fun tag(at: Int) = String(b.array(), at, 4, Charsets.US_ASCII)
        require(tag(0) == "RIFF" && tag(8) == "WAVE")
        require(b.getInt(4).toLong()+8 == b.limit().toLong())
        var offset = 12
        var pcm = false
        var samples: FloatArray? = null
        while (offset + 8 <= b.limit()) {
            val size = b.getInt(offset + 4)
            require(size >= 0 && size.toLong() + offset + 8 <= b.limit())
            when (tag(offset)) {
                "fmt " -> {
                    require(size >= 16)
                    pcm = b.getShort(offset+8).toInt() == 1 && b.getShort(offset+10).toInt() == 1 &&
                        b.getInt(offset+12) == 24000 && b.getShort(offset+22).toInt() == 16
                    require(pcm)
                }
                "data" -> {
                    require(pcm && samples == null && size % 2 == 0 && size / 2 in 24000..360000)
                    samples = FloatArray(size/2) { b.getShort(offset+8+it*2) / 32768f }
                }
            }
            offset += 8 + size + size % 2
        }
        require(offset == b.limit())
        return requireNotNull(samples)
    }
}
