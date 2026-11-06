package io.github.chatgpt01.petai.chat.transport

import io.github.chatgpt01.petai.chat.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.EventListener
import okhttp3.Call
import okhttp3.Protocol
import java.io.IOException
import java.util.concurrent.CountDownLatch
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import java.net.Proxy
import java.util.concurrent.TimeUnit

class TransportTest {
    private var released = CountDownLatch(1)
    @Test fun sseSupportsUnicodeCommentsCrlfAndUsage() {
        val chunks = mutableListOf<String>()
        SseDecoder.decode(StringReader(": heartbeat\r\n\r\ndata: {\"choices\":[]}\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"你好🐳\"}}]}\r\n\r\ndata: [DONE]\r\n\r\n")) {
            chunks.add(it); true
        }
        assertEquals(listOf("你好🐳"), chunks)
    }
    @Test fun truncatedMalformedAndOversizedStreamsFail() {
        for (s in listOf("", "data: {}\n\n", "data: broken\n\n", "data: " + "x".repeat(65537) + "\n\n",
                "data: {\"choices\":[{\"delta\":{\"content\":42}}]}\n\n")) {
            assertThrows(InvalidStream::class.java) { SseDecoder.decode(StringReader(s)) { true } }
        }
    }
    @Test fun finishReasonIsAnExplicitEndSignal() {
        SseDecoder.decode(StringReader("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n")) { true }
    }
    @Test fun httpErrorsHaveSafeClassifications() {
        assertEquals(FailureCode.AUTHENTICATION, httpFailure(401).code)
        assertEquals(FailureCode.AUTHENTICATION, httpFailure(403).code)
        assertTrue(httpFailure(429).retryable)
        assertEquals(FailureCode.SERVER, httpFailure(503).code)
        assertFalse(httpFailure(302).retryable)
    }
    private fun fixture(block: (MockWebServer, OpenAiTransport, ChatRequest) -> Unit) {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCert = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientCert = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        MockWebServer().use { server ->
            server.protocols = listOf(Protocol.HTTP_1_1)
            server.useHttps(serverCert.sslSocketFactory(), false); server.start()
            released = CountDownLatch(1)
            val client = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun callEnd(call: Call) { released.countDown() }
                override fun callFailed(call: Call, ioe: IOException) { released.countDown() }
            }).proxy(Proxy.NO_PROXY)
                .sslSocketFactory(clientCert.sslSocketFactory(), clientCert.trustManager).build()
            val config = ChatModelConfig(server.url("/v1/").toString(), "fake-model", timeoutSeconds = 1)
            block(server, OpenAiTransport(client), ChatRequest("r1", config, "角色", listOf(
                ChatMessage("u1", MessageRole.USER, "你好", 1))))
        }
    }
    @Test fun realHttpsRequestStreamsAndUsesExpectedPathAndHeaders() = fixture { server, transport, request ->
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
            "data: {\"choices\":[{\"delta\":{\"content\":\"回答\"}}]}\n\ndata: [DONE]\n\n"))
        val events = runBlocking { transport.stream(request, ApiKey("fake-test-key")).toList() }
        assertEquals(listOf(GenerationEvent.Delta("r1", "回答"), GenerationEvent.Completed("r1")), events)
        val received = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/v1/chat/completions", received.path)
        assertEquals("Bearer fake-test-key", received.getHeader("Authorization"))
        assertTrue(received.body.readUtf8().contains("fake-model"))
    }
    @Test fun redirectIsNotFollowed() = fixture { server, transport, request ->
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.com/stolen"))
        val result = runBlocking { transport.stream(request, ApiKey("fake-test-key")).toList() }
        assertEquals(1, result.size)
        assertTrue(result.single() is GenerationEvent.Failed)
        assertEquals(1, server.requestCount)
    }
    @Test fun cancellationAfterFirstChunkClosesCollectionWithoutCompletion() = fixture { server, transport, request ->
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setChunkedBody(
            "data: {\"choices\":[{\"delta\":{\"content\":\"部分\"}}]}\n\n" + ": wait\n\n".repeat(10000), 4096)
            .throttleBody(16384, 1, TimeUnit.SECONDS))
        val events = runBlocking { withTimeout(5000) { transport.stream(request.copy(config = request.config.copy(timeoutSeconds = 5)), ApiKey("fake-test-key")).take(1).toList() } }
        assertEquals(1, events.size)
        assertTrue(events.single() is GenerationEvent.Delta)
        assertTrue("HTTP resources must close on cancellation", released.await(5, TimeUnit.SECONDS))
    }
    @Test fun timeoutIsReportedWithoutLeakingServerBody() = fixture { server, transport, request ->
        server.enqueue(MockResponse().setHeadersDelay(2, TimeUnit.SECONDS))
        val events = runBlocking { withTimeout(5000) { transport.stream(request, ApiKey("fake-test-key")).toList() } }
        assertEquals(FailureCode.TIMEOUT, (events.single() as GenerationEvent.Failed).failure.code)
    }
}
