package io.github.chatgpt01.petai

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import io.github.chatgpt01.petai.chat.*
import io.github.chatgpt01.petai.chat.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Uses real Android Keystore and AtomicFile; needs a device, never counted as JVM coverage. */
class ChatStoresDeviceTest {
    @Test fun settingsAndScopedCredentialsSurviveRecreationAndClear() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(target.cacheDir, "chat-test-" + UUID.randomUUID()).apply { mkdirs() }
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = root }
        try {
            val config = ChatModelConfig("https://example.com/v1", "test")
            AndroidChatSettingsStore(context).save(config)
            assertEquals(config, AndroidChatSettingsStore(context).load())
            AndroidChatCredentialStore(context).save(config, ApiKey("fake-device-test-key"))
            assertEquals("fake-device-test-key", AndroidChatCredentialStore(context).load(config)?.reveal())
            assertNull(AndroidChatCredentialStore(context).load(config.copy(baseUrl = "https://other.example/v1")))
            assertTrue(root.walkTopDown().filter { it.isFile }.none { it.readText().contains("fake-device-test-key") })
            AndroidChatCredentialStore(context).clear(config)
            assertNull(AndroidChatCredentialStore(context).load(config))
            File(root, "chat/settings.json").writeText("broken")
            try { AndroidChatSettingsStore(context).load(); fail("Corruption must be reported") }
            catch (_: java.io.IOException) { assertEquals("broken", File(root, "chat/settings.json").readText()) }
        } finally { root.deleteRecursively() }
    }
}
