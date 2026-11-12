package io.github.chatgpt01.voice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import java.nio.file.Files
import java.util.concurrent.Executors

/** Explicit local import and opt-in. Nothing is downloaded or enabled automatically. */
class EngineActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var layout: LinearLayout
    private var busy = false
    private var notice = ""
    private fun state() = EngineState(noBackupFilesDir.toPath())
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    private fun render() {
        layout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        setContentView(ScrollView(this).apply { addView(layout) })
        fun label(text: String) { layout.addView(TextView(this).apply { this.text=text; setPadding(0,12,0,12) }) }
        fun button(text: String, action: () -> Unit) { layout.addView(Button(this).apply { this.text=text; isEnabled=!busy; setOnClickListener { action() } }) }
        label("独立离线音色引擎 · 研究版")
        label("仅在本机合成。先导入共享模型包和音色包，再启用。启用后，与你的引擎签名一致的桌宠应用可发送本次朗读文字；不会获得聊天密钥或完整历史。请只导入可信来源的模型。")
        label("新音色仍待试听验收。本引擎使用安卓兼容的神经语音推理；Qwen3-TTS原始权重不能直接导入。")
        try {
            val s = state(); val choice = s.read(); val voices = s.packs.listVoices()
            label(if (choice.enabled) "已启用" else "已停用")
            label(if (Files.exists(s.packs.directory("model",VoicePacks.MODEL))) "共享模型已安装" else "尚未安装共享模型")
            if (notice.isNotEmpty()) label(notice)
            button(if (busy) "处理中…" else "导入模型包 / 音色包") {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),42)
            }
            button(if (choice.enabled) "停用引擎" else "启用选定音色") {
                runWork {
                    if (!choice.enabled) check(s.available().any { it.id == choice.voice }) { "请先安装模型并选择音色" }
                    s.write(choice.copy(enabled=!choice.enabled))
                }
            }
            voices.forEach { voice ->
                label(voice.name + if (choice.voice == voice.id) "（已选）" else "")
                button("选择此音色") { runWork { s.write(EngineChoice(false,voice.id)) } }
                button("删除此音色") { runWork {
                    s.write(choice.copy(enabled=false,voice=if(choice.voice==voice.id) "" else choice.voice))
                    Files.walk(s.packs.directory("voice",voice.id)).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
                } }
            }
        } catch (_: Exception) { label("无法读取音色设置或资源，原文件保留。") }
    }
    private fun runWork(block: () -> Unit) {
        if (busy) return
        busy=true; render()
        worker.execute {
            val result = runCatching(block)
            runOnUiThread { if (!isDestroyed) { busy=false; notice=if(result.isSuccess) "操作完成；选择音色后需手动启用。" else "操作失败，已有资源保留。请检查格式、校验值、存储空间及是否已安装同名资源。"; render() } }
        }
    }
    @Deprecated("Platform result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode!=42 || resultCode!=RESULT_OK) return
        val uri = data?.data ?: return
        runWork { contentResolver.openInputStream(uri).use { input -> state().packs.importPack(requireNotNull(input)) } }
    }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}
