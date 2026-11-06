package io.github.chatgpt01.petai.chat.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.chatgpt01.petai.chat.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal fun credentialScope(config: ChatModelConfig): String {
    val uri = URI(config.baseUrl)
    return "https://${uri.host.lowercase()}:${if (uri.port == -1) 443 else uri.port}${uri.rawPath.orEmpty().trimEnd('/')}"
}

internal object CredentialCipher {
    fun encrypt(key: SecretKey, scope: String, value: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(scope.toByteArray(Charsets.UTF_8))
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    }
    fun decrypt(key: SecretKey, scope: String, data: ByteArray): String {
        require(data.size >= 29 && data[0] == 1.toByte()) { "Invalid credential envelope" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(1, 13)))
        cipher.updateAAD(scope.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(data.copyOfRange(13, data.size)), Charsets.UTF_8)
    }
}

internal fun decodeConfig(text: String): ChatModelConfig {
    val json = JsonParser.parseString(text).asJsonObject
    fun integer(name: String): Int {
        val value = json.get(name)
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return value.asBigDecimal.intValueExact()
    }
    require(integer("schema") == 1)
    return ChatModelConfig(json.get("baseUrl").asString, json.get("model").asString,
        json.get("temperature").asDouble, integer("maxOutputTokens"), integer("timeoutSeconds"))
}

private fun writeAtomic(file: File, data: ByteArray) {
    file.parentFile?.mkdirs()
    val atomic = AtomicFile(file)
    val stream = atomic.startWrite()
    try { stream.write(data); atomic.finishWrite(stream) }
    catch (e: Exception) { atomic.failWrite(stream); throw e }
}

/** Create one instance of each store per app; all disk operations are serialized off main. */
class AndroidChatSettingsStore(context: Context) : ChatSettingsStore {
    private val file = File(context.noBackupFilesDir, "chat/settings.json")
    private val mutex = Mutex()
    override suspend fun load(): ChatModelConfig? = withContext(Dispatchers.IO) { mutex.withLock {
        if (!file.exists() && !File(file.path + ".bak").exists()) return@withLock null
        try {
            decodeConfig(AtomicFile(file).readFully().toString(Charsets.UTF_8))
        } catch (_: Exception) { throw IOException("Chat configuration could not be read") }
    } }
    override suspend fun save(config: ChatModelConfig) { withContext(Dispatchers.IO) { mutex.withLock {
        val json = JsonObject().apply {
            addProperty("schema", 1); addProperty("baseUrl", config.baseUrl); addProperty("model", config.model)
            addProperty("temperature", config.temperature); addProperty("maxOutputTokens", config.maxOutputTokens)
            addProperty("timeoutSeconds", config.timeoutSeconds)
        }
        writeAtomic(file, json.toString().toByteArray(Charsets.UTF_8))
    } } }
}

class AndroidChatCredentialStore(context: Context) : ChatCredentialStore {
    private val directory = File(context.noBackupFilesDir, "chat/credentials")
    private val mutex = Mutex()
    private fun file(config: ChatModelConfig): File = File(directory,
        MessageDigest.getInstance("SHA-256").digest(credentialScope(config).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) } + ".enc")
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "dshpet.chat.credentials.v1"
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "Credential encryption key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    override suspend fun load(config: ChatModelConfig): ApiKey? = withContext(Dispatchers.IO) { mutex.withLock {
        val file = file(config)
        if (!file.exists() && !File(file.path + ".bak").exists()) return@withLock null
        try { ApiKey(CredentialCipher.decrypt(key(false), credentialScope(config), AtomicFile(file).readFully())) }
        catch (_: Exception) { throw IOException("Credential unavailable; enter the key again") }
    } }
    override suspend fun save(config: ChatModelConfig, key: ApiKey) { withContext(Dispatchers.IO) { mutex.withLock {
        try { writeAtomic(file(config), CredentialCipher.encrypt(key(true), credentialScope(config), key.reveal())) }
        catch (_: Exception) { throw IOException("Credential could not be securely saved") }
    } } }
    override suspend fun clear(config: ChatModelConfig) { withContext(Dispatchers.IO) { mutex.withLock {
        val file = file(config)
        AtomicFile(file).delete()
        if (file.exists() || File(file.path + ".bak").exists()) throw IOException("Credential could not be removed")
    } } }
}
