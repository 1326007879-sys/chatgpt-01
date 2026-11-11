package io.github.chatgpt01.petai.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.chatgpt01.petai.LocalizedActivity
import io.github.chatgpt01.petai.AppLanguage
import io.github.chatgpt01.petai.R
import io.github.chatgpt01.petai.chat.ui.ChatTheme
import io.github.chatgpt01.petai.plugins.*
import com.google.gson.JsonObject
import kotlinx.coroutines.*

class PluginManagementActivity : LocalizedActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var registry: PluginRegistry
    private lateinit var client: PluginClient
    private var enabledKeys by mutableStateOf<Set<String>>(emptySet())
    private var entries by mutableStateOf<List<PluginEntry>>(emptyList())
    private var selected by mutableStateOf<PluginEntry?>(null)
    private var grant by mutableStateOf<PluginEntry?>(null)
    private var uninstall by mutableStateOf<PluginEntry?>(null)
    private var inputs by mutableStateOf<Map<String,String>>(emptyMap())
    private var result by mutableStateOf<JsonObject?>(null)
    private var busy by mutableStateOf(false)
    private var notice by mutableStateOf<Int?>(null)
    private var callJob: Job? = null
    private var scanJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        registry = PluginRegistry(applicationContext); client = PluginClient(applicationContext,registry)
        setContent { ChatTheme { Page() } }
    }
    override fun onResume() { super.onResume(); if (::registry.isInitialized) refresh() }
    override fun onStop() { callJob?.cancel(); super.onStop() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    private fun refresh() {
        scanJob?.cancel()
        scanJob = scope.launch {
            try {
                entries = withContext(Dispatchers.IO) { registry.scan() }
                enabledKeys = entries.filter { registry.enabled(it) }.map { it.component.flattenToString() }.toSet()
                selected = selected?.let { old -> entries.firstOrNull { it.component == old.component && it.stamp == old.stamp } }
                if (selected == null) result = null
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { notice = R.string.plugin_scan_failed }
        }
    }
    private fun error(code: String): Int = when (code) {
        "TIMEOUT" -> R.string.plugin_timeout
        "CHANGED" -> R.string.plugin_changed
        "SAVE" -> R.string.plugin_save_failed
        "INVALID" -> R.string.plugin_invalid_reply
        "UNAUTHORIZED" -> R.string.plugin_pair_required
        "DISCONNECTED" -> R.string.plugin_disconnected
        else -> R.string.plugin_call_failed
    }
    private fun run(entry: PluginEntry, operation: String, fields: Map<String,String>) {
        if (busy) return
        busy = true; notice = null; result = null
        callJob = scope.launch {
            try {
                val response = client.call(entry,operation,fields,AppLanguage.selected(this@PluginManagementActivity))
                if (response["ok"].asBoolean) result = response.getAsJsonObject("result")
                else notice = error(response["error"].asString)
            } catch (e: CancellationException) { throw e }
            catch (e: PluginFailure) { notice = error(e.code) }
            catch (_: Exception) { notice = R.string.plugin_call_failed }
            finally { busy = false }
        }
    }
    @OptIn(ExperimentalLayoutApi::class)
    @Composable private fun Page() {
        val english = AppLanguage.selected(this) == "en"
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) try {
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (_: Exception) { notice = R.string.plugin_install_failed }
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp)
            .testTag("plugins_page"),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { finish() }) { Text(stringResource(R.string.plugins_back)) }
            Text(stringResource(R.string.plugins_title),style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.plugin_scope))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (packageManager.canRequestPackageInstalls()) picker.launch(arrayOf("application/vnd.android.package-archive","application/octet-stream","*/*"))
                    else {
                        notice = R.string.plugin_install_permission
                        try { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName"))) }
                        catch (_: Exception) { notice = R.string.plugin_install_failed }
                    }
                },enabled = !busy) { Text(stringResource(R.string.plugin_install)) }
                TextButton(onClick = ::refresh,enabled = !busy) { Text(stringResource(R.string.plugin_refresh)) }
            }
            notice?.let { Text(stringResource(it),modifier = Modifier.testTag("plugin_notice")) }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = { callJob?.cancel(); notice = R.string.plugin_cancelled }) { Text(stringResource(R.string.plugin_cancel)) }
            }
            if (entries.isEmpty()) Text(stringResource(R.string.plugin_no_plugins),modifier = Modifier.testTag("plugins_empty"))
            for (entry in entries) {
                val d = entry.descriptor
                HorizontalDivider()
                Text(if (english) d?.nameEn ?: entry.name else entry.name,style = MaterialTheme.typography.titleMedium)
                Text(entry.component.packageName,style = MaterialTheme.typography.bodySmall)
                if (d != null) Text(stringResource(R.string.plugin_versions,d.version,d.protocol))
                entry.issue?.let { Text(stringResource(when (it) {
                    "VERSION" -> R.string.plugin_version_unsupported
                    "PERMISSION" -> R.string.plugin_permission_unsupported
                    else -> R.string.plugin_descriptor_invalid
                })) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        try {
                            if (registry.enabled(entry)) registry.setEnabled(entry,false) else { grant = entry; return@TextButton }
                            result = null; refresh()
                        } catch (_: Exception) { notice = R.string.plugin_save_failed }
                    },enabled = !busy && entry.issue == null) {
                        Text(stringResource(if (entry.component.flattenToString() in enabledKeys) R.string.plugin_disable else R.string.plugin_enable))
                    }
                    TextButton(onClick = {
                        selected = entry; inputs = emptyMap(); result = null; notice = null
                    },enabled = !busy && entry.component.flattenToString() in enabledKeys) { Text(stringResource(R.string.plugin_open)) }
                    TextButton(onClick = {
                        try { val intent = packageManager.getLaunchIntentForPackage(entry.component.packageName)
                            if (intent == null) notice = R.string.plugin_pair_required else startActivity(intent)
                        } catch (_: Exception) { notice = R.string.plugin_install_failed }
                    },enabled = !busy) { Text(stringResource(R.string.plugin_pair)) }
                    TextButton(onClick = { uninstall = entry },enabled = !busy) { Text(stringResource(R.string.plugin_uninstall)) }
                }
                if (selected?.component == entry.component && entry.component.flattenToString() in enabledKeys) {
                    for (operation in d!!.operations) {
                        Text(if (english) operation.labelEn else operation.label)
                        for (field in operation.fields) {
                            val key = operation.id+":"+field.id
                            OutlinedTextField(value = inputs[key].orEmpty(),onValueChange = { if (it.length <= 1000) inputs = inputs + (key to it) },
                                label = { Text(if (english) field.labelEn else field.label) },modifier = Modifier.fillMaxWidth(),
                                enabled = !busy,maxLines = 4)
                        }
                        Button(onClick = { run(entry,operation.id,operation.fields.associate { it.id to inputs[operation.id+":"+it.id].orEmpty() }) },
                            enabled = !busy,modifier = Modifier.testTag("plugin_op_${operation.id}")) { Text(if (english) operation.labelEn else operation.label) }
                    }
                    result?.let { response ->
                        response["message"]?.let { Text(it.asString) }
                        response.getAsJsonArray("items")?.forEach { element ->
                            val item = element.asJsonObject
                            SelectionContainer { Text(item["text"].asString) }
                            TextButton(onClick = {
                                startActivity(Intent(this@PluginManagementActivity, ReminderActivity::class.java)
                                    .putExtra("component", entry.component.flattenToString()).putExtra("stamp", entry.stamp)
                                    .putExtra("text", item["text"].asString))
                            }, enabled = !busy) { Text(stringResource(R.string.reminder_from_note)) }

                            Text(item["id"].asString,style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                val updates = d.operations.flatMap { op -> op.fields.filter { it.id == "id" }.map { op.id+":"+it.id to item["id"].asString } }.toMap()
                                inputs = inputs + updates
                            },enabled = !busy) { Text(stringResource(R.string.plugin_select_record)) }
                        }
                    }
                }
            }
        }
        grant?.let { entry ->
            AlertDialog(onDismissRequest = { grant = null },title = { Text(stringResource(R.string.plugin_enable)) },
                text = { Text(stringResource(R.string.plugin_grant_hint)+"\n"+entry.descriptor!!.permissions.joinToString("\n") {
                    getString(if (it == "storage.private") R.string.plugin_private_storage else R.string.plugin_diagnostics)
                }) },
                confirmButton = { TextButton(onClick = {
                    try { registry.setEnabled(entry,true); refresh() } catch (_: Exception) { notice = R.string.plugin_save_failed }
                    grant = null
                }) { Text(stringResource(R.string.plugin_allow)) } },
                dismissButton = { TextButton(onClick = { grant = null }) { Text(stringResource(R.string.cancel)) } })
        }
        uninstall?.let { entry ->
            AlertDialog(onDismissRequest = { uninstall = null },title = { Text(stringResource(R.string.plugin_uninstall)) },
                text = { Text(stringResource(R.string.plugin_uninstall_hint)) },
                confirmButton = { TextButton(onClick = {
                    try {
                        registry.disablePackage(entry.component.packageName)
                        startActivity(Intent(Intent.ACTION_DELETE,Uri.parse("package:${entry.component.packageName}")))
                    } catch (_: Exception) { notice = R.string.plugin_install_failed }
                    uninstall = null
                }) { Text(stringResource(R.string.plugin_uninstall)) } },
                dismissButton = { TextButton(onClick = { uninstall = null }) { Text(stringResource(R.string.cancel)) } })
        }
    }
}
