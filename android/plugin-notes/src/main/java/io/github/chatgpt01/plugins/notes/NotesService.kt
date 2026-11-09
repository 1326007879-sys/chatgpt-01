package io.github.chatgpt01.plugins.notes

import com.google.gson.*
import io.github.chatgpt01.pluginprotocol.*
import io.github.chatgpt01.pluginruntime.BasePluginService
import java.io.File

class NotesService : BasePluginService() {
    override fun descriptor() = PluginProtocol.descriptor(resources.openRawResource(R.raw.descriptor).bufferedReader().use { it.readText() })
    override fun execute(request: JsonObject): JsonObject {
        val store = NoteStore(File(noBackupFilesDir,"notes.json"))
        val p = request.getAsJsonObject("params"); val english = request["language"]?.asString == "en"
        val result = JsonObject()
        when (request["operation"].asString) {
            "add" -> {
                val note = store.add(p["text"].asString)
                result.add("items",Gson().toJsonTree(listOf(note)))
                result.addProperty("message",if (english) "Saved" else "已保存")
            }
            "search" -> {
                val page = p["page"].asString.let { if (it.isBlank()) 1 else it.toInt() }
                result.add("items",Gson().toJsonTree(store.search(p["query"].asString,page)))
                result.addProperty("message",if (english) "Page $page, up to 10 matches. Change the page or refine the search." else "第${page}页，每页最多10条。可修改页码或关键词。")
            }
            "delete" -> {
                val deleted = store.delete(p["id"].asString)
                result.addProperty("message",if (english) { if (deleted) "Deleted" else "Not found" } else { if (deleted) "已删除" else "未找到这条便签" })
            }
        }
        return result
    }
}
