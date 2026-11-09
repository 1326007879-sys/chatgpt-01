package io.github.chatgpt01.plugins.fault

import android.content.*
import android.os.*
import io.github.chatgpt01.pluginprotocol.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Test-only cross-UID request. It never returns any note content to the caller. */
fun unauthorizedProbe(context: Context): String {
    val id = UUID.randomUUID().toString()
    val arrived = CountDownLatch(1); val closed = AtomicBoolean(false)
    val result = AtomicReference("PROBE_FAILED")
    val thread = HandlerThread("probe-reply").apply { start() }
    val receiver = Messenger(object : Handler(thread.looper) {
        override fun handleMessage(message: Message) {
            if (closed.get()) return
            try {
                val r = PluginProtocol.validateResponse(message.data.getString("json") ?: "",id)
                result.set(if (r["ok"].asBoolean) "ACCESS_GRANTED" else r["error"].asString)
            } catch (_: Exception) { result.set("PROBE_FAILED") }
            arrived.countDown()
        }
    })
    val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (closed.get()) return
            try { Messenger(service).send(Message.obtain(null,PluginProtocol.REQUEST).apply {
                replyTo = receiver; data = Bundle().apply {
                    putString("json",PluginProtocol.request(id,"search",mapOf("query" to "","page" to ""),"en"))
                }
            }) } catch (_: Exception) { arrived.countDown() }
        }
        override fun onServiceDisconnected(name: ComponentName) { arrived.countDown() }
        override fun onNullBinding(name: ComponentName) { arrived.countDown() }
    }
    var bound = false
    try {
        bound = context.bindService(Intent(PluginProtocol.ACTION).setComponent(ComponentName(
            "io.github.chatgpt01.plugins.notes","io.github.chatgpt01.plugins.notes.NotesService")),connection,Context.BIND_AUTO_CREATE)
        if (bound) arrived.await(2,TimeUnit.SECONDS)
        return result.get()
    } finally {
        closed.set(true)
        if (bound) runCatching { context.unbindService(connection) }
        thread.quitSafely()
    }
}
