package io.github.chatgpt01.petai

import androidx.test.platform.app.InstrumentationRegistry
import io.github.chatgpt01.petai.plugins.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Install both sample APKs and approve this test host in both plugin launchers first. */
class PluginIpcDeviceTest {
    @Test fun independentApksSupportNotesAndIsolateFaultsAndUnauthorizedUid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val registry = PluginRegistry(context); val client = PluginClient(context,registry)
        val entries = registry.scan()
        val notes = entries.single { it.descriptor?.id == "example.notes" }
        val fault = entries.single { it.descriptor?.id == "example.fault" }
        assertNotEquals(context.applicationInfo.uid,notes.uid)
        assertNotEquals(notes.uid,fault.uid)
        assertEquals("VERSION",entries.single { it.descriptor?.id == "example.future" }.issue)
        assertEquals("PERMISSION",entries.single { it.descriptor?.id == "example.denied" }.issue)
        val oldNotes = registry.enabled(notes); val oldFault = registry.enabled(fault)
        var noteId: String? = null
        try {
            registry.setEnabled(notes,true); registry.setEnabled(fault,true)
            val text = "plugin-test-${UUID.randomUUID()}"
            val saved = client.call(notes,"add",mapOf("text" to text),"en")
            assertTrue("Approve the host in the notes plugin first: $saved",saved["ok"].asBoolean)
            noteId = saved.getAsJsonObject("result").getAsJsonArray("items")[0].asJsonObject["id"].asString
            val found = client.call(notes,"search",mapOf("query" to text,"page" to ""),"en")
            assertEquals(text,found.getAsJsonObject("result").getAsJsonArray("items")[0].asJsonObject["text"].asString)
            val echoed = client.call(fault,"echo",mapOf("text" to "ready"),"en")
            assertTrue("Approve the host in the fault plugin first: $echoed",echoed["ok"].asBoolean)
            assertEquals("UNAUTHORIZED",client.call(fault,"probe",emptyMap(),"en").getAsJsonObject("result")["message"].asString)
            assertFalse(client.call(fault,"error",emptyMap(),"en")["ok"].asBoolean)
            try { client.call(fault,"timeout",emptyMap(),"en"); fail("Expected timeout") }
            catch (e: PluginFailure) { assertEquals("TIMEOUT",e.code) }
            assertTrue(client.call(fault,"echo",mapOf("text" to "after timeout"),"en")["ok"].asBoolean)
            try { client.call(fault,"crash",emptyMap(),"en"); fail("Expected disconnection") }
            catch (e: PluginFailure) { assertEquals("DISCONNECTED",e.code) }
            assertTrue(client.call(fault,"echo",mapOf("text" to "after crash"),"en")["ok"].asBoolean)
            assertTrue(client.call(notes,"delete",mapOf("id" to noteId!!),"en")["ok"].asBoolean)
            noteId = null
            registry.disablePackage(notes.component.packageName)
            try { client.call(notes,"search",mapOf("query" to "","page" to ""),"en"); fail("Disabled plugin called") }
            catch (e: PluginFailure) { assertEquals("CHANGED",e.code) }
        } finally {
            if (noteId != null) {
                registry.setEnabled(notes,true)
                runCatching { client.call(notes,"delete",mapOf("id" to noteId!!),"en") }
            }
            registry.setEnabled(notes,oldNotes); registry.setEnabled(fault,oldFault)
        }
    }
}
