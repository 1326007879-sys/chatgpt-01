package io.github.chatgpt01.petai.plugins

import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import io.github.chatgpt01.pluginprotocol.*
import java.security.MessageDigest

class PluginFailure(val code: String) : Exception(code)
data class PluginEntry(val component: ComponentName, val name: String, val descriptor: Descriptor?,
                       val stamp: String, val uid: Int, val issue: String? = null)
class PluginRegistry(private val context: Context) {
    private val prefs = context.getSharedPreferences("plugin_grants_v1",Context.MODE_PRIVATE)
    @Suppress("DEPRECATION")
    fun stamp(component: ComponentName): String {
        val p = context.packageManager.getPackageInfo(component.packageName,PackageManager.GET_SIGNATURES)
        val signatures = p.signatures.orEmpty().map { sha(it.toByteArray()) }.sorted().joinToString(":")
        require(signatures.isNotEmpty())
        val version = if (android.os.Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
        return sha("${component.flattenToString()}|$version|${p.firstInstallTime}|${p.lastUpdateTime}|$signatures".toByteArray())
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    @Suppress("DEPRECATION")
    fun scan(): List<PluginEntry> {
        val pm = context.packageManager
        val entries = pm.queryIntentServices(Intent(PluginProtocol.ACTION),PackageManager.GET_META_DATA).take(64).map { resolved ->
            val s = resolved.serviceInfo; val component = ComponentName(s.packageName,s.name)
            try {
                val resources = pm.getResourcesForApplication(s.applicationInfo)
                val resource = s.metaData?.getInt(PluginProtocol.DESCRIPTOR) ?: 0
                val text = resources.openRawResource(resource).use { stream ->
                    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                    while (true) { val n = stream.read(buffer); if (n < 0) break; require(out.size()+n<=PluginProtocol.LIMIT); out.write(buffer,0,n) }
                    out.toString("UTF-8")
                }
                val d = PluginProtocol.descriptor(text)
                val permissions = pm.getPackageInfo(s.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
                val issue = when {
                    d.protocol != PluginProtocol.VERSION -> "VERSION"
                    !PluginProtocol.supported(d) || permissions.isNotEmpty() -> "PERMISSION"
                    !s.exported || s.permission != null || s.applicationInfo.uid == context.applicationInfo.uid ||
                        s.flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0 -> "INVALID"
                    else -> null
                }
                PluginEntry(component,d.name,d,stamp(component),s.applicationInfo.uid,issue)
            } catch (_: Exception) { PluginEntry(component,s.packageName,null,"",s.applicationInfo.uid,"INVALID") }
        }.sortedBy { it.name }
        val valid = entries.filter { it.issue == null }.associate { it.component.flattenToString() to it.stamp }
        val editor = prefs.edit(); var changed = false
        for ((key,value) in prefs.all) if (valid[key] != value) { editor.remove(key); changed = true }
        if (changed && !editor.commit()) throw PluginFailure("SAVE")
        return entries
    }
    fun enabled(entry: PluginEntry): Boolean = entry.issue == null && prefs.getString(entry.component.flattenToString(),null) == entry.stamp
    fun setEnabled(entry: PluginEntry, enabled: Boolean) {
        if (enabled && (entry.issue != null || stamp(entry.component) != entry.stamp)) throw PluginFailure("CHANGED")
        val edit = prefs.edit()
        if (enabled) edit.putString(entry.component.flattenToString(),entry.stamp) else edit.remove(entry.component.flattenToString())
        if (!edit.commit()) throw PluginFailure("SAVE")
    }
    fun disablePackage(packageName: String) {
        val edit = prefs.edit()
        prefs.all.keys.filter { ComponentName.unflattenFromString(it)?.packageName == packageName }.forEach { edit.remove(it) }
        if (!edit.commit()) throw PluginFailure("SAVE")
    }
    fun verify(entry: PluginEntry) { if (!enabled(entry) || stamp(entry.component) != entry.stamp) throw PluginFailure("CHANGED") }
}
