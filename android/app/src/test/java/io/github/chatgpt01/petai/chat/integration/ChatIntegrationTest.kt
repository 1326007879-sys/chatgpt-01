package io.github.chatgpt01.petai.chat.integration

import com.google.gson.JsonParser
import io.github.chatgpt01.petai.chat.*
import io.github.chatgpt01.petai.chat.conversation.DefaultChatConversation
import io.github.chatgpt01.petai.chat.storage.FileChatSessionStore
import io.github.chatgpt01.petai.chat.transport.OpenAiTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.net.Proxy
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Real TLS + production transport/coordinator + actual file restart; no external model or real key. */
class ChatIntegrationTest {
    @Test fun twoTurnsSurviveReopeningAndSendOnlyCompletedContext() = runBlocking {
        withTimeout(30000) {
            val root = Files.createTempDirectory("pet-integration")
            val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            var current: DefaultChatConversation? = null
            val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
            val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
            val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
            try {
                MockWebServer().use { server ->
                    server.useHttps(serverTls.sslSocketFactory(), false); server.start()
                    val config = ChatModelConfig(server.url("/v1").toString(), "test-model", timeoutSeconds = 15)
                    val settings = object : ChatSettingsStore {
                        override suspend fun load() = config
                        override suspend fun save(config: ChatModelConfig) {}
                    }
                    val keys = object : ChatCredentialStore {
                        override suspend fun load(config: ChatModelConfig) = ApiKey("fake-integration-key")
                        override suspend fun save(config: ChatModelConfig, key: ApiKey) {}
                        override suspend fun clear(config: ChatModelConfig) {}
                    }
                    val transport = OpenAiTransport(OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
                        .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build())
                    suspend fun open() = DefaultChatConversation.open("main", "shenshen", FileChatSessionStore(root),
                        settings, keys, transport, "桌宠角色", owner)
                    fun response(text: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                        "data: {\"choices\":[{\"delta\":{\"content\":\"$text\"}}]}\n\ndata: [DONE]\n\n")
                    server.enqueue(response("你好🐳")); server.enqueue(response("第二次回复"))
                    current = open()
                    assertTrue(current!!.send("第一问") is SendResult.Accepted)
                    current!!.state.first { it.generation.phase == GenerationPhase.COMPLETED }
                    current!!.close()
                    current = open()
                    assertEquals(listOf("第一问", "你好🐳"), current!!.state.value.session.messages.map { it.content })
                    assertTrue(current!!.send("第二问") is SendResult.Accepted)
                    current!!.state.first { it.generation.phase == GenerationPhase.COMPLETED }
                    current!!.close()
                    val history = FileChatSessionStore(root).load("main")!!
                    assertEquals(4, history.messages.size)
                    assertTrue(history.messages.all { it.status == MessageStatus.COMPLETED })
                    server.takeRequest(5, TimeUnit.SECONDS)!!
                    val request = server.takeRequest(5, TimeUnit.SECONDS)!!
                    assertEquals("/v1/chat/completions", request.path)
                    val sent = JsonParser.parseString(request.body.readUtf8()).asJsonObject.getAsJsonArray("messages")
                    assertEquals(listOf("桌宠角色", "第一问", "你好🐳", "第二问"), sent.map { it.asJsonObject.get("content").asString })
                    Files.list(root).use { files -> files.forEach { assertFalse(String(Files.readAllBytes(it)).contains("fake-integration-key")) } }
                }
            } finally { current?.close(); owner.cancel(); root.toFile().deleteRecursively() }
        }
    }
}
