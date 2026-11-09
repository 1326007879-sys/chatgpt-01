package io.github.chatgpt01.pluginruntime

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.util.AtomicFile
import android.widget.*
import com.google.gson.*
import io.github.chatgpt01.pluginprotocol.*
import java.io.File
import java.security.MessageDigest

object Pairing {
    @Suppress("DEPRECATION")
    fun certificate(context: Context, pkg: String): String {
        val info = context.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
        return info.signatures.orEmpty().map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) } }.sorted().joinToString(":")
    }
    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "hosts.json"))
    fun read(context: Context): JsonObject = try {
        val f = file(context)
        if (!f.baseFile.exists()) JsonObject() else f.openRead().use { JsonParser.parseString(it.bufferedReader().readText()).asJsonObject }
    } catch (_: Exception) { JsonObject() }
    fun grant(context: Context, pkg: String, enabled: Boolean, expected: String) {
        val data = read(context)
        if (enabled) { require(expected.isNotEmpty() && certificate(context,pkg) == expected); data.addProperty(pkg,expected) } else data.remove(pkg)
        val f = file(context); val stream = f.startWrite()
        try { stream.write(data.toString().toByteArray(Charsets.UTF_8)); f.finishWrite(stream) }
        catch (e: Exception) { f.failWrite(stream); throw e }
    }
    fun allowed(context: Context, uid: Int): Boolean {
        val approved = read(context)
        return context.packageManager.getPackagesForUid(uid).orEmpty().any { pkg ->
            runCatching { approved.has(pkg) && approved[pkg].asString == certificate(context, pkg) }.getOrDefault(false)
        }
    }
}

/** A separate plugin application's explicit consent screen, not part of the host process. */
class PairingActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    @Suppress("DEPRECATION")
    private fun render() {
        val english = resources.configuration.locales[0].language == "en"
        val pad = (24 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad,pad,pad,pad) }
        column.addView(TextView(this).apply { text = if (english) "Plugin access" else "插件访问授权"; textSize = 24f })
        column.addView(TextView(this).apply { text = if (english)
            "Approve a host before it can call this plugin. This plugin stores its own data; uninstalling it deletes that data."
            else "请先授权桌宠，才允许它调用此插件。插件数据独立保存，卸载插件会删除其数据。" })
        val hosts = packageManager.queryIntentActivities(Intent(PluginProtocol.HOST_ACTION).addCategory(Intent.CATEGORY_DEFAULT), 0)
            .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
        val grants = Pairing.read(this)
        for (host in hosts) {
            val signature = Pairing.certificate(this,host.packageName)
            val enabled = runCatching { grants[host.packageName]?.asString == signature }.getOrDefault(false)
            column.addView(TextView(this).apply { text = "${packageManager.getApplicationLabel(host)}\n${host.packageName}\n${signature.take(16)}…" })
            column.addView(Button(this).apply {
                text = if (english) { if (enabled) "Revoke access" else "Allow this host" } else { if (enabled) "撤销授权" else "允许此桌宠访问" }
                setOnClickListener {
                    try { Pairing.grant(this@PairingActivity,host.packageName,!enabled,signature); render() }
                    catch (_: Exception) { Toast.makeText(this@PairingActivity,if (english) "Could not save access" else "授权保存失败",Toast.LENGTH_LONG).show() }
                }
            })
        }
        if (hosts.isEmpty()) column.addView(TextView(this).apply { text = if (english) "Install a compatible host first." else "尚未发现支持插件的新版本桌宠，请先安装兼容主应用。" })
        column.addView(Button(this).apply { text = if (english) "Refresh" else "刷新"; setOnClickListener { render() } })
        setContentView(ScrollView(this).apply {
            addView(column)
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
                insets
            }
        })
    }
}

/** No host code is loaded. The Binder caller UID is checked against explicit certificate approval. */
abstract class BasePluginService : Service() {
    protected abstract fun descriptor(): Descriptor
    protected abstract fun execute(request: JsonObject): JsonObject?
    private lateinit var worker: HandlerThread
    private lateinit var messenger: Messenger
    override fun onCreate() {
        super.onCreate()
        worker = HandlerThread("plugin-requests").apply { start() }
        messenger = Messenger(object : Handler(worker.looper) {
            override fun handleMessage(message: Message) {
                if (message.what != PluginProtocol.REQUEST) return
                val reply = message.replyTo ?: return
                var id = "invalid"
                val output = try {
                    val raw = message.data.getString("json") ?: return
                    val request = PluginProtocol.validateRequest(raw,descriptor()); id = request["id"].asString
                    if (!Pairing.allowed(this@BasePluginService,message.sendingUid)) PluginProtocol.response(id,error="UNAUTHORIZED")
                    else execute(request)?.let { PluginProtocol.response(id,it) } ?: return
                } catch (_: Exception) { PluginProtocol.response(id,error="PLUGIN_ERROR") }
                try { reply.send(Message.obtain(null,PluginProtocol.RESPONSE).apply { data = Bundle().apply { putString("json",output) } }) }
                catch (_: RemoteException) { /* Host already timed out or left. */ }
            }
        })
    }
    override fun onBind(intent: Intent?): IBinder = messenger.binder
    override fun onDestroy() { worker.quitSafely(); super.onDestroy() }
}
