package io.github.chatgpt01.petai.chat.storage

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.github.chatgpt01.petai.chat.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Pass context.noBackupFilesDir.toPath().resolve("chat/sessions"). One instance per app. */
class FileChatSessionStore(private val directory: Path) : ChatSessionStore {
    private val mutex = Mutex()
    private fun path(id: String): Path {
        require(id.isNotBlank())
        val name = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return directory.resolve("$name.json")
    }
    override suspend fun load(sessionId: String): ChatSession? = withContext(Dispatchers.IO) { mutex.withLock {
        val file = path(sessionId)
        if (Files.notExists(file)) return@withLock null
        try {
            require(Files.size(file) <= MAX_BYTES)
            decodeSession(String(Files.readAllBytes(file), Charsets.UTF_8)).also { require(it.id == sessionId) }
        } catch (_: Exception) { throw IOException("Chat history could not be read; original file preserved") }
    } }
    override suspend fun save(session: ChatSession) { withContext(Dispatchers.IO) { mutex.withLock {
        val bytes = Gson().toJson(session).toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) throw IOException("Chat history size limit reached")
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "session-", ".tmp")
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            // If atomic replacement is unsupported, fail without touching the original.
            Files.move(temporary, path(session.id), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    } } }
    companion object { const val MAX_BYTES = 5L * 1024 * 1024 }
}

internal fun decodeSession(text: String): ChatSession {
    val root = JsonParser.parseString(text).asJsonObject
    fun string(item: com.google.gson.JsonObject, name: String): String {
        val value = item.get(name)
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }
    fun number(name: String): Long {
        val p = root.get(name)
        require(p.isJsonPrimitive && p.asJsonPrimitive.isNumber)
        return p.asBigDecimal.longValueExact()
    }
    require(number("schemaVersion") == 1L)
    val messages = root.getAsJsonArray("messages").map { element ->
        val item = element.asJsonObject
        val timestamp = item.get("createdAtEpochMs")
        require(timestamp.isJsonPrimitive && timestamp.asJsonPrimitive.isNumber)
        val error = item.get("failure")?.takeUnless { it.isJsonNull }?.asJsonObject?.let {
            val retry = it.get("retryable")
            require(retry.isJsonPrimitive && retry.asJsonPrimitive.isBoolean)
            ChatFailure(FailureCode.valueOf(string(it, "code")), retry.asBoolean)
        }
        ChatMessage(string(item, "id"), MessageRole.valueOf(string(item, "role")),
            string(item, "content"), timestamp.asBigDecimal.longValueExact(),
            MessageStatus.valueOf(string(item, "status")), error)
    }
    return ChatSession(string(root, "id"), string(root, "characterId"), messages)
}
