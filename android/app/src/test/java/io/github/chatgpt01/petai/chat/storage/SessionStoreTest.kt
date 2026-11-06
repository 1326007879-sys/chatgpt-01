package io.github.chatgpt01.petai.chat.storage

import io.github.chatgpt01.petai.chat.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.IOException

class SessionStoreTest {
    private fun session(id: String = "main") = ChatSession(id, "shenshen", listOf(
        ChatMessage("u", MessageRole.USER, "你好🐳", 0),
        ChatMessage("a", MessageRole.ASSISTANT, "部分", 1, MessageStatus.FAILED, ChatFailure(FailureCode.NETWORK, true))))
    @Test fun realFileRoundTripUsesSafeNamesAndPreservesFailure() = runBlocking {
        val root = Files.createTempDirectory("pet-chat")
        try {
            val store = FileChatSessionStore(root)
            assertNull(store.load("missing"))
            val value = session("../outside")
            store.save(value)
            assertEquals(value, FileChatSessionStore(root).load(value.id))
            Files.list(root).use { files -> assertTrue(files.allMatch { it.fileName.toString().matches(Regex("[a-f0-9]{64}\\.json")) }) }
            assertFalse(String(Files.readAllBytes(Files.list(root).use { it.findFirst().get() })).contains("fake-key"))
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun corruptAndUnknownVersionFilesAreNotOverwrittenOnLoad() = runBlocking {
        val root = Files.createTempDirectory("pet-chat")
        try {
            val store = FileChatSessionStore(root); store.save(session())
            val path = Files.list(root).use { it.findFirst().get() }
            val original = String(Files.readAllBytes(path))
            for (text in listOf("broken", original.replace("schemaVersion\":1", "schemaVersion\":2"),
                    original.replace("schemaVersion\":1", "schemaVersion\":1.5"),
                    original.replace("\"characterId\":\"shenshen\"", "\"characterId\":42"), original.replace("\"id\":\"main\"", "\"id\":\"other\""))) {
                Files.write(path, text.toByteArray())
                try { store.load("main"); fail("Must reject invalid history") } catch (_: IOException) {}
                assertEquals(text, String(Files.readAllBytes(path)))
            }
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun replacementDoesNotLeaveTemporaryFiles() = runBlocking {
        val root = Files.createTempDirectory("pet-chat")
        try {
            val store = FileChatSessionStore(root)
            store.save(session()); store.save(session().copy(messages = emptyList()))
            assertTrue(store.load("main")!!.messages.isEmpty())
            Files.list(root).use { assertEquals(1L, it.count()) }
        } finally { root.toFile().deleteRecursively() }
    }
}
