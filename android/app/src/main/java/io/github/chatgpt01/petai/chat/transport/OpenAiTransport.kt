package io.github.chatgpt01.petai.chat.transport

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.chatgpt01.petai.chat.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.Reader
import java.util.concurrent.TimeUnit

/** No redirects, implicit retries or logging interceptors on the production client. */
class OpenAiTransport(private val client: OkHttpClient = OkHttpClient()) : ChatTransport {
    override fun stream(request: ChatRequest, apiKey: ApiKey): Flow<GenerationEvent> = callbackFlow {
        val messages = JsonArray()
        if (request.systemPrompt.isNotBlank()) messages.add(JsonObject().apply {
            addProperty("role", "system"); addProperty("content", request.systemPrompt)
        })
        request.messages.forEach { message -> messages.add(JsonObject().apply {
            addProperty("role", message.role.name.lowercase()); addProperty("content", message.content)
        }) }
        val payload = JsonObject().apply {
            addProperty("model", request.config.model); addProperty("stream", true)
            addProperty("temperature", request.config.temperature)
            addProperty("max_tokens", request.config.maxOutputTokens); add("messages", messages)
        }
        val http = Request.Builder().url(request.config.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer " + apiKey.reveal())
            .header("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        val call = client.newBuilder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).callTimeout(request.config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build().newCall(http)
        fun fail(failure: ChatFailure) {
            trySendBlocking(GenerationEvent.Failed(request.requestId, failure)); close()
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                fail(ChatFailure(if (e is java.io.InterruptedIOException) FailureCode.TIMEOUT else FailureCode.NETWORK, true))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) { fail(httpFailure(response.code)); return }
                    val body = response.body
                    if (body == null || body.contentType()?.subtype != "event-stream") {
                        fail(ChatFailure(FailureCode.INVALID_RESPONSE)); return
                    }
                    try {
                        SseDecoder.decodeLines({
                            try { body.source().readUtf8LineStrict(65536) }
                            catch (_: java.io.EOFException) { throw InvalidStream() }
                        }) { text ->
                            trySendBlocking(GenerationEvent.Delta(request.requestId, text)).isSuccess
                        }
                        trySendBlocking(GenerationEvent.Completed(request.requestId)); close()
                    } catch (_: InvalidStream) { fail(ChatFailure(FailureCode.INVALID_RESPONSE))
                    } catch (e: IOException) {
                        fail(ChatFailure(if (e is java.io.InterruptedIOException) FailureCode.TIMEOUT else FailureCode.NETWORK, true))
                    }
                }
            }
        })
        awaitClose { call.cancel() }
    }
}

internal fun httpFailure(status: Int) = ChatFailure(when (status) {
    401, 403 -> FailureCode.AUTHENTICATION
    429 -> FailureCode.RATE_LIMITED
    in 500..599 -> FailureCode.SERVER
    else -> FailureCode.INVALID_RESPONSE
}, status == 429 || status in 500..599)

internal class InvalidStream : IOException("Invalid model stream")

/** Bounded SSE records; no raw server text escapes in exceptions. */
internal object SseDecoder {
    fun decode(reader: Reader, delta: (String) -> Boolean) {
        val input = reader.buffered()
        decodeLines({
            val line = StringBuilder()
            while (true) {
                val ch = input.read()
                if (ch == -1) throw InvalidStream()
                if (ch == 10) break
                if (line.length >= 65536) throw InvalidStream()
                line.append(ch.toChar())
            }
            line.toString().removeSuffix("\r")
        }, delta)
    }
    fun decodeLines(nextLine: () -> String, delta: (String) -> Boolean) {
        val data = StringBuilder()
        while (true) {
            val value = nextLine()
            if (value.isEmpty()) {
                if (data.isEmpty()) continue
                val record = data.toString().removeSuffix("\n"); data.setLength(0)
                if (record == "[DONE]") return
                try {
                    val root = JsonParser.parseString(record).asJsonObject
                    if (root.has("error")) throw InvalidStream()
                    val choices = root.getAsJsonArray("choices") ?: throw InvalidStream()
                    if (choices.size() == 0) continue // usage-only record
                    val choice = choices[0].asJsonObject
                    val content = choice.getAsJsonObject("delta")?.get("content")
                    if (content != null && !content.isJsonNull) {
                        if (!content.isJsonPrimitive || !content.asJsonPrimitive.isString) throw InvalidStream()
                        if (!delta(content.asString)) throw InvalidStream()
                    }
                    val finish = choice.get("finish_reason")
                    if (finish != null && !finish.isJsonNull) {
                        if (finish.asString !in listOf("stop", "length")) throw InvalidStream()
                        return
                    }
                } catch (_: RuntimeException) { throw InvalidStream() }
            } else if (value.startsWith("data:")) {
                data.append(value.substring(5).removePrefix(" ")).append('\n')
                if (data.length > 65536) throw InvalidStream()
            }
        }
    }
}
