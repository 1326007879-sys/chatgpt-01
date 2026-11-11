package io.github.chatgpt01.petai.animation

import com.google.gson.Gson
import io.github.chatgpt01.petai.ClipSpec
import io.github.chatgpt01.petai.SourceBox
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

class PackFailure(val reason: String) : IOException(reason)
@androidx.annotation.Keep
data class PackManifest(
    val schema: Int = 0, val format: String = "", val character: String = "",
    val id: String = "", val version: Int = 0, val name: String = "", val nameEn: String = "",
    val canvas: List<Int> = emptyList(), val body: List<Int> = emptyList(),
    val fps: Double = 0.0, val frames: Int = 0, val crop: List<Int> = emptyList(),
    val noMirror: Boolean = false, val hashes: List<String> = emptyList(),
) {
    fun validate() {
        if (schema != 1 || format != "webp-frames-v1" || character != "shenshen" ||
            canvas != listOf(640, 360) || body != listOf(212, 60, 428, 330)) throw PackFailure("incompatible")
        if (!id.matches(Regex("ext_[a-f0-9]{24}")) || version < 1 || name.isBlank() || name.length > 100 ||
            nameEn.isBlank() || nameEn.length > 150 || !fps.isFinite() || fps !in 1.0..60.0 || frames !in 1..1800 ||
            crop.size != 4 || crop[0] !in 0..639 || crop[1] !in 0..359 || crop[2] !in (crop[0]+1)..640 ||
            crop[3] !in (crop[1]+1)..360 || hashes.size != frames ||
            hashes.any { !it.matches(Regex("[a-f0-9]{64}")) }) throw PackFailure("invalid")
    }
    fun clip(directory: File) = ClipSpec(id, "random", fps, frames,
        crop = SourceBox(crop[0].toDouble(), crop[1].toDouble(), crop[2].toDouble(), crop[3].toDouble()),
        displayName = name, englishName = nameEn, batch = 4, noMirror = noMirror,
        installedDirectory = directory)
}
data class InstalledAnimation(val id: String, val manifest: PackManifest?, val bytes: Long)

/** Resource-only ZIPs. A process-wide lease excludes writes while the overlay uses these files. */
class AnimationPackStore(private val root: File) {
    companion object {
        private val gate = Any()
        private var users = 0
        private var writing = false
        private const val MAX_BYTES = 256L * 1024 * 1024
        private const val MAX_ARCHIVE = 300L * 1024 * 1024
        private val gson = Gson()
        fun release() = synchronized(gate) { check(users > 0); users-- }
        private fun beginWrite() = synchronized(gate) {
            if (users > 0 || writing) throw PackFailure("busy")
            writing = true
        }
        private fun endWrite() = synchronized(gate) { writing = false }
    }
    private fun manifest(dir: File): PackManifest {
        val file = File(dir, "pack.json")
        if (file.length() !in 1..262144) throw PackFailure("invalid")
        return gson.fromJson(file.readText(Charsets.UTF_8), PackManifest::class.java).also {
            it.validate()
            for (index in 1..it.frames) {
                val frame = File(dir, "frames/f_%04d.webp".format(java.util.Locale.ROOT, index))
                if (!frame.isFile || frame.length() !in 1..2097152) throw PackFailure("invalid")
            }
        }
    }
    fun list(): List<InstalledAnimation> {
        return root.listFiles().orEmpty().filter { it.isDirectory && it.name.matches(Regex("ext_[a-f0-9]{24}")) }
            .map { dir -> InstalledAnimation(dir.name, runCatching { manifest(dir).also {
                if (it.id != dir.name) throw PackFailure("invalid")
            } }.getOrNull(), dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }) }
            .sortedBy { it.manifest?.name ?: it.id }
    }
    fun acquire(): List<ClipSpec> = synchronized(gate) {
        if (writing) throw PackFailure("busy")
        val clips = list().mapNotNull { entry -> entry.manifest?.clip(File(root, entry.id)) }
        users++
        clips
    }
    fun install(input: InputStream, checkFrame: (File) -> Boolean, cancelled: () -> Unit = {}): PackManifest =
        installInternal(input, checkFrame, cancelled, null)
    fun installVerified(input: InputStream, checkFrame: (File) -> Boolean, id: String, version: Int, cancelled: () -> Unit): PackManifest =
        installInternal(input, checkFrame, cancelled, id to version)
    private fun installInternal(input: InputStream, checkFrame: (File) -> Boolean, cancelled: () -> Unit, expected: Pair<String,Int>?): PackManifest {
        beginWrite()
        val stage = File(root, ".staging-${UUID.randomUUID()}")
        try {
            root.mkdirs()
            // A previous process may have died before completing an import. No active writers exist here.
            root.listFiles().orEmpty().filter { it.name.startsWith(".staging-") }.forEach {
                if (!it.deleteRecursively()) throw PackFailure("storage")
            }
            if (!stage.mkdirs()) throw PackFailure("storage")
            var total = 0L
            val seen = HashSet<String>()
            val bounded = object : FilterInputStream(input) {
                var count = 0L
                override fun read(): Int { val n = super.read(); if (n >= 0) checkCount(1); return n }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = `in`.read(b, off, len); if (n > 0) checkCount(n); return n
                }
                fun checkCount(n: Int) { count += n; if (count > MAX_ARCHIVE) throw PackFailure("large") }
            }
            ZipInputStream(BufferedInputStream(bounded)).use { zip ->
                while (true) {
                    cancelled()
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (!seen.add(name) || seen.size > 1804) throw PackFailure("invalid")
                    // Directory entries are unnecessary and could hide an unbounded decompression drain.
                    if (entry.isDirectory) throw PackFailure("invalid")
                    if (name !in setOf("pack.json", "NOTICE.txt", "THIRD_PARTY_NOTICES.md") &&
                        !name.matches(Regex("frames/f_[0-9]{4}\\.webp"))) throw PackFailure("invalid")
                    val target = File(stage, name)
                    target.parentFile!!.mkdirs()
                    var size = 0L
                    val limit = if (name == "pack.json") 262144 else 2 * 1024 * 1024
                    target.outputStream().use { out ->
                        val buffer = ByteArray(32768)
                        while (true) {
                            cancelled()
                            val n = zip.read(buffer); if (n < 0) break
                            size += n; total += n
                            if (size > limit || total > MAX_BYTES) throw PackFailure("large")
                            out.write(buffer, 0, n)
                        }
                    }
                }
            }
            val pack = manifest(stage)
            if (expected != null && (pack.id != expected.first || pack.version != expected.second)) throw PackFailure("invalid")
            val expected = (1..pack.frames).map { "frames/f_%04d.webp".format(java.util.Locale.ROOT, it) }.toSet()
            if (seen.filter { it.startsWith("frames/") && it != "frames/" }.toSet() != expected ||
                !seen.containsAll(listOf("NOTICE.txt", "THIRD_PARTY_NOTICES.md"))) throw PackFailure("invalid")
            for (index in 1..pack.frames) {
                cancelled()
                val frame = File(stage, "frames/f_%04d.webp".format(java.util.Locale.ROOT, index))
                val sha = MessageDigest.getInstance("SHA-256").digest(frame.readBytes())
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                if (sha != pack.hashes[index - 1] || !checkFrame(frame)) throw PackFailure("invalid")
            }
            val destination = File(root, pack.id)
            if (destination.exists()) throw PackFailure("duplicate")
            cancelled()
            if (!stage.renameTo(destination)) throw PackFailure("storage")
            return pack
        } finally {
            try { stage.deleteRecursively() } finally { endWrite() }
        }
    }
    fun delete(id: String) {
        if (!id.matches(Regex("ext_[a-f0-9]{24}"))) throw PackFailure("invalid")
        beginWrite()
        try {
            val dir = File(root, id)
            if (dir.exists() && !dir.deleteRecursively()) throw PackFailure("storage")
        } finally { endWrite() }
    }
}
