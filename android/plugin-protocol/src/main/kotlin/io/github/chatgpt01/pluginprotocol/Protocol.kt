package io.github.chatgpt01.pluginprotocol

import com.google.gson.*

object PluginProtocol {
    const val ACTION = "io.github.chatgpt01.petai.PLUGIN_SERVICE"
    const val HOST_ACTION = "io.github.chatgpt01.petai.PLUGIN_HOST"
    const val DESCRIPTOR = "pet.plugin.descriptor"
    const val REQUEST = 1
    const val RESPONSE = 2
    const val LIMIT = 32768
    const val VERSION = 1
    private val name = Regex("[a-z][a-z0-9_.]{0,63}")
    fun parse(text: String): JsonObject {
        require(text.toByteArray(Charsets.UTF_8).size <= LIMIT)
        return JsonParser.parseString(text).asJsonObject
    }
    fun descriptor(text: String): Descriptor {
        parse(text)
        val d = Gson().fromJson(text, Descriptor::class.java)
        require(d.id.matches(name) && d.name.isNotBlank() && d.name.length <= 100 && d.nameEn.isNotBlank() && d.nameEn.length <= 100)
        require(d.version in 1..1000000 && d.protocol in 1..1000 && d.permissions.size <= 8)
        require(d.operations.size in 1..16 && d.operations.map { it.id }.distinct().size == d.operations.size)
        d.operations.forEach { op ->
            require(op.id.matches(name) && op.label.length in 1..100 && op.labelEn.length in 1..100)
            require(op.fields.size <= 4 && op.fields.map { it.id }.distinct().size == op.fields.size)
            op.fields.forEach { f -> require(f.id.matches(name) && f.label.length in 1..100 && f.labelEn.length in 1..100) }
        }
        return d
    }
    fun supported(d: Descriptor): Boolean = d.protocol == VERSION &&
        d.permissions.all { it in setOf("storage.private", "diagnostics") }
    fun request(id: String, operation: String, fields: Map<String,String>, language: String): String {
        require(fields.values.all { it.length <= 1000 })
        return Gson().toJson(mapOf("protocol" to VERSION, "id" to id, "operation" to operation,
            "params" to fields, "language" to language)).also { parse(it) }
    }
    fun validateRequest(text: String, descriptor: Descriptor): JsonObject {
        val r = parse(text)
        require(r["protocol"].asInt == VERSION && r["id"].asString.length in 1..80)
        val op = descriptor.operations.single { it.id == r["operation"].asString }
        val params = r.getAsJsonObject("params")
        require(params.keySet() == op.fields.map { it.id }.toSet())
        params.entrySet().forEach { require(it.value.isJsonPrimitive && it.value.asJsonPrimitive.isString && it.value.asString.length <= 1000) }
        return r
    }
    fun response(id: String, result: JsonObject? = null, error: String? = null): String =
        Gson().toJson(mapOf("protocol" to VERSION, "id" to id, "ok" to (error == null),
            "result" to result, "error" to error)).also { parse(it) }
    fun validateResponse(text: String, id: String): JsonObject {
        val r = parse(text)
        require(r["protocol"].asInt == VERSION && r["id"].asString == id && r["ok"].asJsonPrimitive.isBoolean)
        if (r["ok"].asBoolean) {
            require(r["result"].isJsonObject)
            val result = r.getAsJsonObject("result")
            require(result.keySet().all { it in setOf("message","items") })
            result["message"]?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.length <= 4000) }
            result["items"]?.let { items ->
                require(items.isJsonArray && items.asJsonArray.size() <= 20)
                items.asJsonArray.forEach { item ->
                    val obj = item.asJsonObject
                    require(obj.keySet() == setOf("id","text"))
                    require(obj["id"].isJsonPrimitive && obj["id"].asJsonPrimitive.isString && obj["id"].asString.length in 1..80)
                    require(obj["text"].isJsonPrimitive && obj["text"].asJsonPrimitive.isString && obj["text"].asString.length <= 1000)
                }
            }
        }
        else require(r["error"].asString.matches(Regex("[A-Z_]{1,40}")))
        return r
    }
}
data class Field(@com.google.gson.annotations.SerializedName("id") val id: String = "", @com.google.gson.annotations.SerializedName("label") val label: String = "", @com.google.gson.annotations.SerializedName("labelEn") val labelEn: String = "")
data class Operation(@com.google.gson.annotations.SerializedName("id") val id: String = "", @com.google.gson.annotations.SerializedName("label") val label: String = "", @com.google.gson.annotations.SerializedName("labelEn") val labelEn: String = "", @com.google.gson.annotations.SerializedName("fields") val fields: List<Field> = emptyList())
data class Descriptor(@com.google.gson.annotations.SerializedName("id") val id: String = "", @com.google.gson.annotations.SerializedName("name") val name: String = "", @com.google.gson.annotations.SerializedName("nameEn") val nameEn: String = "", @com.google.gson.annotations.SerializedName("version") val version: Int = 0,
                      @com.google.gson.annotations.SerializedName("protocol") val protocol: Int = 0, @com.google.gson.annotations.SerializedName("permissions") val permissions: List<String> = emptyList(), @com.google.gson.annotations.SerializedName("operations") val operations: List<Operation> = emptyList())

/** A terminal event wins exactly once; forged UID and late replies never complete a call. */
class ReplyGate(private val requestId: String, private val expectedUid: Int) {
    private var closed = false
    @Synchronized fun cancel() { closed = true }
    @Synchronized fun accept(uid: Int, text: String): JsonObject? {
        if (closed || uid != expectedUid) return null
        closed = true
        return PluginProtocol.validateResponse(text,requestId)
    }
}
