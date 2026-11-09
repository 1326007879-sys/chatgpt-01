package io.github.chatgpt01.petai.plugins

import android.content.*
import android.os.*
import com.google.gson.JsonObject
import io.github.chatgpt01.pluginprotocol.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.UUID

/** One bound service per explicit call; cancellation, timeout and Binder death unbind exactly once. */
class PluginClient(context: Context, private val registry: PluginRegistry) {
    private val app = context.applicationContext
    suspend fun call(entry: PluginEntry, operation: String, fields: Map<String,String>, language: String): JsonObject =
        withContext(Dispatchers.Main.immediate) {
            registry.verify(entry)
            val id = UUID.randomUUID().toString()
            val request = PluginProtocol.request(id,operation,fields,language)
            try {
                withTimeout(3000) {
                    suspendCancellableCoroutine { continuation ->
                        val handler = Handler(Looper.getMainLooper())
                        val gate = ReplyGate(id,entry.uid)
                        val replyThread = HandlerThread("plugin-reply").apply { start() }
                        var bound = false
                        var cleaned = false
                        lateinit var connection: ServiceConnection
                        fun cleanup() {
                            if (cleaned) return
                            cleaned = true; gate.cancel(); replyThread.quit()
                            if (bound) { bound = false; runCatching { app.unbindService(connection) } }
                        }
                        fun fail(code: String) {
                            cleanup()
                            if (continuation.isActive) continuation.resumeWithException(PluginFailure(code))
                        }
                        val replies = Messenger(object : Handler(replyThread.looper) {
                            override fun handleMessage(message: Message) {
                                if (!continuation.isActive || message.what != PluginProtocol.RESPONSE || message.sendingUid != entry.uid) return
                                try {
                                    val response = gate.accept(message.sendingUid,message.data.getString("json") ?: error("Missing payload")) ?: return
                                    replyThread.quit()
                                    handler.post {
                                        if (!continuation.isActive) cleanup()
                                        else try {
                                            registry.verify(entry)
                                            cleanup(); continuation.resume(response)
                                        } catch (e: PluginFailure) { fail(e.code) }
                                        catch (_: Exception) { fail("INVALID") }
                                    }
                                } catch (_: Exception) { replyThread.quit(); handler.post { fail("INVALID") } }
                            }
                        })
                        connection = object : ServiceConnection {
                            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                                if (cleaned || !continuation.isActive) { cleanup(); return }
                                try {
                                    registry.verify(entry)
                                    Messenger(service).send(Message.obtain(null,PluginProtocol.REQUEST).apply {
                                        replyTo = replies; data = Bundle().apply { putString("json",request) }
                                    })
                                } catch (_: Exception) { fail("DISCONNECTED") }
                            }
                            override fun onServiceDisconnected(name: ComponentName) { fail("DISCONNECTED") }
                            override fun onBindingDied(name: ComponentName) { fail("DISCONNECTED") }
                            override fun onNullBinding(name: ComponentName) { fail("DISCONNECTED") }
                        }
                        continuation.invokeOnCancellation { handler.post { cleanup() } }
                        try {
                            bound = app.bindService(Intent(PluginProtocol.ACTION).setComponent(entry.component),connection,Context.BIND_AUTO_CREATE)
                            if (!bound) fail("DISCONNECTED")
                        } catch (_: Exception) { fail("DISCONNECTED") }
                    }
                }
            } catch (_: TimeoutCancellationException) { throw PluginFailure("TIMEOUT") }
        }
}
