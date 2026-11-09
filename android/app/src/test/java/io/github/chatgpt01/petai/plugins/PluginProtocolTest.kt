package io.github.chatgpt01.petai.plugins

import com.google.gson.JsonObject
import io.github.chatgpt01.pluginprotocol.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PluginProtocolTest {
    private val descriptor = """{"id":"test.echo","name":"测试","nameEn":"Test","version":1,"protocol":1,"permissions":["storage.private"],"operations":[{"id":"echo","label":"回声","labelEn":"Echo","fields":[{"id":"text","label":"内容","labelEn":"Text"}]}]}"""
    private fun rejects(block: () -> Unit) { try { block(); fail("expected rejection") } catch (_: Exception) { } }
    private fun reply(id: String = "one") = PluginProtocol.response(id,JsonObject().apply { addProperty("message","原文😀") })
    @Test fun aNewDescriptorProducesOperationsWithoutHostSpecificCode() {
        val d = PluginProtocol.descriptor(descriptor)
        assertTrue(PluginProtocol.supported(d))
        val request = PluginProtocol.request("one","echo",mapOf("text" to "你好😀"),"zh-CN")
        assertEquals("你好😀",PluginProtocol.validateRequest(request,d).getAsJsonObject("params")["text"].asString)
        assertEquals("原文😀",PluginProtocol.validateResponse(reply(),"one").getAsJsonObject("result")["message"].asString)
    }
    @Test fun shippedSampleDescriptorsAreCompatibleAndNegativeFixturesStayRejected() {
        fun descriptor(module: String, name: String) = PluginProtocol.descriptor(java.io.File(
            "../$module/src/main/res/raw/$name.json").readText(Charsets.UTF_8))
        val notes = descriptor("plugin-notes","descriptor")
        assertTrue(PluginProtocol.supported(notes))
        assertEquals(setOf("add","search","delete"),notes.operations.map { it.id }.toSet())
        assertEquals(setOf("query","page"),notes.operations.single { it.id == "search" }.fields.map { it.id }.toSet())
        assertTrue(PluginProtocol.supported(descriptor("plugin-fault","descriptor")))
        assertFalse(PluginProtocol.supported(descriptor("plugin-fault","future")))
        assertFalse(PluginProtocol.supported(descriptor("plugin-fault","denied")))
    }

    @Test fun futureVersionsAndUndeclaredPermissionsAreRejected() {
        val d = PluginProtocol.descriptor(descriptor)
        assertFalse(PluginProtocol.supported(d.copy(protocol = 99)))
        assertFalse(PluginProtocol.supported(d.copy(permissions = listOf("host.chat_history"))))
        assertFalse(PluginProtocol.supported(d.copy(permissions = listOf("host.model_keys"))))
        rejects { PluginProtocol.validateRequest(PluginProtocol.request("one","other",emptyMap(),"en"),d) }
        rejects { PluginProtocol.validateRequest(PluginProtocol.request("one","echo",mapOf("text" to "a","extra" to "b"),"en"),d) }
    }
    @Test fun malformedOversizedAndUnexpectedReplyShapesNeverReachUi() {
        rejects { PluginProtocol.validateResponse("bad JSON","one") }
        rejects { PluginProtocol.validateResponse(reply("another"),"one") }
        rejects { PluginProtocol.parse("x".repeat(PluginProtocol.LIMIT+1)) }
        rejects { PluginProtocol.request("one","echo",mapOf("text" to "x".repeat(1001)),"en") }
        rejects { PluginProtocol.validateResponse("""{"protocol":1,"id":"one","ok":true,"result":{"items":"invalid"}}""","one") }
        rejects { PluginProtocol.validateResponse("""{"protocol":1,"id":"one","ok":false,"error":"private details"}""","one") }
    }
    @Test fun forgedUidCannotCompleteAndFirstGenuineReplyWins() {
        val gate = ReplyGate("one",42)
        assertNull(gate.accept(43,reply()))
        assertNotNull(gate.accept(42,reply()))
        assertNull(gate.accept(42,reply()))
    }
    @Test fun cancellationAndReplyRaceHaveOneTerminalOutcomeByEvents() {
        val workers = Executors.newFixedThreadPool(2)
        try {
            repeat(100) {
                val gate = ReplyGate("one",42); val start = CountDownLatch(1)
                val cancel = workers.submit { check(start.await(5,TimeUnit.SECONDS)); gate.cancel() }
                val result = workers.submit<JsonObject?> { check(start.await(5,TimeUnit.SECONDS)); gate.accept(42,reply()) }
                start.countDown(); cancel.get(5,TimeUnit.SECONDS); result.get(5,TimeUnit.SECONDS)
                assertNull(gate.accept(42,reply()))
            }
        } finally { workers.shutdownNow() }
    }
    @Test fun invalidFirstReplyIsTerminalAndLateRepliesAfterCancellationAreIgnored() {
        val invalid = ReplyGate("one",42)
        rejects { invalid.accept(42,"bad") }
        assertNull(invalid.accept(42,reply()))
        val cancelled = ReplyGate("one",42); cancelled.cancel()
        assertNull(cancelled.accept(42,reply()))
    }
}
