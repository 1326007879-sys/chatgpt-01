package io.github.chatgpt01.plugins.notes

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NoteStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun saveSearchAndDeletePersistAcrossServiceInstances() {
        val file = File(tmp.newFolder(),"notes.json")
        val note = NoteStore(file).add("明天买猫粮😀")
        assertEquals(note,NoteStore(file).search("猫粮").single())
        assertTrue(NoteStore(file).delete(note.id))
        assertTrue(NoteStore(file).read().isEmpty())
        assertFalse(NoteStore(file).delete(note.id))
    }
    @Test fun invalidInputAndCorruptStorageDoNotOverwriteExistingData() {
        val file = File(tmp.newFolder(),"notes.json"); val store = NoteStore(file)
        store.add("original")
        for (text in listOf(" ","x".repeat(1001))) {
            try { store.add(text); fail("expected rejection") } catch (_: IllegalArgumentException) { }
        }
        assertEquals("original",store.read().single().text)
        file.writeText("corrupt")
        try { store.add("replacement"); fail("expected failure") } catch (_: Exception) { }
        assertEquals("corrupt",file.readText())
    }
    @Test fun concurrentRequestsOnOneServiceDoNotLoseWritesAndSearchIsBounded() {
        val file = File(tmp.newFolder(),"notes.json"); val store = NoteStore(file); val pool = Executors.newFixedThreadPool(4)
        try {
            val calls = (1..100).map { n -> pool.submit { NoteStore(file).add("note $n") } }
            calls.forEach { it.get(10,TimeUnit.SECONDS) }
            assertEquals(100,store.read().size)
            assertEquals(100,store.read().map { it.id }.distinct().size)
            assertEquals(10,store.search("").size)
            assertEquals(10,store.search("",10).size)
            assertTrue(store.search("",11).isEmpty())
        } finally { pool.shutdownNow() }
    }
}
