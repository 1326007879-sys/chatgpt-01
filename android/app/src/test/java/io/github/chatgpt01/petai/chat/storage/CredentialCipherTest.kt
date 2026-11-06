package io.github.chatgpt01.petai.chat.storage

import io.github.chatgpt01.petai.chat.ChatModelConfig
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class CredentialCipherTest {
    @Test fun configDecoderRejectsUnknownAndFractionalVersionsAndTruncatedFiles() {
        val json = """{"schema":1,"baseUrl":"https://example.com/v1","model":"test","temperature":1,"maxOutputTokens":1024,"timeoutSeconds":60}"""
        assertEquals("test", decodeConfig(json).model)
        for (text in listOf(json.replace("schema\":1", "schema\":2"),
                json.replace("schema\":1", "schema\":1.5"),
                json.replace("1024", "1.5"), "broken")) {
            assertThrows(Exception::class.java) { decodeConfig(text) }
        }
    }
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    @Test fun ciphertextIsRandomizedAndRoundTrips() {
        val key = key()
        val a = CredentialCipher.encrypt(key, "server-a", "fake-secret")
        val b = CredentialCipher.encrypt(key, "server-a", "fake-secret")
        assertFalse(a.contentEquals(b))
        assertFalse(String(a, Charsets.ISO_8859_1).contains("fake-secret"))
        assertEquals("fake-secret", CredentialCipher.decrypt(key, "server-a", a))
    }
    @Test fun wrongServerWrongKeyAndTamperingAreRejected() {
        val key = key(); val data = CredentialCipher.encrypt(key, "server-a", "fake-secret")
        assertThrows(Exception::class.java) { CredentialCipher.decrypt(key, "server-b", data) }
        assertThrows(Exception::class.java) { CredentialCipher.decrypt(key(), "server-a", data) }
        data[data.lastIndex] = (data.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { CredentialCipher.decrypt(key, "server-a", data) }
        assertThrows(Exception::class.java) { CredentialCipher.decrypt(key, "server-a", byteArrayOf(2)) }
    }
    @Test fun credentialIdentityNormalizesHostPortAndSlashButPreservesPath() {
        fun scope(url: String, model: String = "a") = credentialScope(ChatModelConfig(url, model))
        assertEquals(scope("https://EXAMPLE.com/v1/"), scope("https://example.com:443/v1", "b"))
        assertNotEquals(scope("https://example.com/v1"), scope("https://example.com/V1"))
        assertNotEquals(scope("https://example.com/v1"), scope("https://other.example/v1"))
    }
}
