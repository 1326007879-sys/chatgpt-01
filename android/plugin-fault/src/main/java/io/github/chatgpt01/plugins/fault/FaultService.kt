package io.github.chatgpt01.plugins.fault

import android.os.Process
import com.google.gson.JsonObject
import io.github.chatgpt01.pluginprotocol.*
import io.github.chatgpt01.pluginruntime.BasePluginService

class FaultService : BasePluginService() {
    override fun descriptor() = PluginProtocol.descriptor(resources.openRawResource(R.raw.descriptor).bufferedReader().use { it.readText() })
    override fun execute(request: JsonObject): JsonObject? = when (request["operation"].asString) {
        "probe" -> JsonObject().apply { addProperty("message",unauthorizedProbe(this@FaultService)) }
        "timeout" -> null // Deliberately never reply; no sleeping worker blocks other requests.
        "error" -> error("Simulated failure")
        "crash" -> { Process.killProcess(Process.myPid()); null }
        else -> JsonObject().apply { addProperty("message",request.getAsJsonObject("params")["text"].asString) }
    }
}
