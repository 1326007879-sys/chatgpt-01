package io.github.chatgpt01.petai.reminders

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

/** Single host process, shared lock even across recreated repositories. Run operations off the UI thread. */
class ReminderStore(file: Path) {
    private val file = file.toAbsolutePath().normalize()
    private val lock = locks.computeIfAbsent(this.file) { Any() }

    fun <T> transaction(block: (MutableList<Reminder>) -> T): T = synchronized(lock) {
        val original = read()
        val items = original.toMutableList()
        val result = block(items)
        require(items.size <= 200 && items.map { it.id }.distinct().size == items.size)
        if (items != original) write(items)
        result
    }

    private fun read(): List<Reminder> {
        if (Files.notExists(file)) return emptyList()
        try {
            require(Files.size(file) <= MAX_BYTES)
            val root = JsonParser.parseString(String(Files.readAllBytes(file), Charsets.UTF_8)).asJsonObject
            require(root.keySet() == setOf("schema", "items"))
            require(number(root, "schema") == 1L)
            val items = root.getAsJsonArray("items")
            require(items.size() <= 200)
            return items.map { element ->
                val o = element.asJsonObject
                require(o.keySet() == setOf("id", "owner", "draft", "revision", "generation", "nextAt"))
                val owner = o.getAsJsonObject("owner")
                val d = o.getAsJsonObject("draft")
                val speak = d["speak"].asJsonPrimitive
                require(speak.isBoolean)
                Reminder(string(o, "id"), ReminderOwner(string(owner, "component"), string(owner, "grantFingerprint")),
                    ReminderDraft(string(d, "text"), number(d, "firstAt"), string(d, "zoneId"),
                        ReminderRepeat.valueOf(string(d, "repeat")), string(d, "localTime"), speak.asBoolean,
                        string(d, "voiceProvider"), string(d, "voiceId")), number(o, "revision"), string(o, "generation"),
                    if (o["nextAt"].isJsonNull) null else number(o, "nextAt"))
            }.also { rows -> require(rows.map { it.id }.distinct().size == rows.size) }
        } catch (e: Exception) {
            throw IOException("Reminders could not be read; original file preserved", e)
        }
    }

    private fun write(items: List<Reminder>) {
        val bytes = Gson().newBuilder().serializeNulls().create().toJson(mapOf("schema" to 1, "items" to items)).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES)
        Files.createDirectories(file.parent)
        val tmp = Files.createTempFile(file.parent, "reminders-", ".tmp")
        try {
            FileChannel.open(tmp, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(tmp) }
    }

    companion object {
        private const val MAX_BYTES = 2 * 1024 * 1024L
        private val locks = ConcurrentHashMap<Path, Any>()
        private fun string(o: JsonObject, key: String): String {
            val p = o[key].asJsonPrimitive; require(p.isString); return p.asString
        }
        private fun number(o: JsonObject, key: String): Long {
            val p = o[key].asJsonPrimitive; require(p.isNumber); return p.asBigDecimal.longValueExact()
        }
    }
}
