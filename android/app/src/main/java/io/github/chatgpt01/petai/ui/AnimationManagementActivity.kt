package io.github.chatgpt01.petai.ui

import android.os.Bundle
import android.graphics.BitmapFactory
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.chatgpt01.petai.R
import io.github.chatgpt01.petai.*
import io.github.chatgpt01.petai.animation.*
import io.github.chatgpt01.petai.chat.ui.ChatTheme
import kotlinx.coroutines.*
import java.io.File

/** Local SAF import and entry to separately managed online resource downloads. */
class AnimationManagementActivity : LocalizedActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var store: AnimationPackStore
    private var installed by mutableStateOf<List<InstalledAnimation>>(emptyList())
    private var busy by mutableStateOf(false)
    private var notice by mutableStateOf<Int?>(null)
    private var deleting by mutableStateOf<InstalledAnimation?>(null)
    private var coreCount by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = AnimationPackStore(File(noBackupFilesDir, "animations"))
        coreCount = runCatching { AssetCatalog.load(assets).clips.size }.getOrDefault(11)
        operation { null }
        setContent {
            ChatTheme {
                val runtime by PetRuntime.state.collectAsState()
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) operation {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            store.install(stream, ::validAnimationFrame) { currentCoroutineContextCheck() }
                        } ?: throw PackFailure("invalid")
                        R.string.packs_success
                    }
                }
                LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp).testTag("animation_management"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        TextButton(onClick = { finish() }) { Text(stringResource(R.string.back)) }
                        Text(stringResource(R.string.packs_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.packs_hint))
                        OutlinedButton(onClick = {
                            startActivity(android.content.Intent(this@AnimationManagementActivity, OnlineAnimationsActivity::class.java))
                        }, enabled = !busy) { Text(stringResource(R.string.online_animations_title)) }

                        Text(stringResource(R.string.packs_core, coreCount))
                        if (runtime.running) OutlinedButton(onClick = {
                            PetOverlayService.command(this@AnimationManagementActivity, PetOverlayService.STOP)
                        }) { Text(stringResource(R.string.packs_stop)) }
                        Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy && !runtime.running,
                            modifier = Modifier.testTag("import_animation_pack")) {
                            Text(stringResource(if (busy) R.string.packs_working else R.string.animation_import))
                        }
                        notice?.let { Text(stringResource(it), modifier = Modifier.testTag("pack_notice")) }
                        Text(stringResource(R.string.packs_total, installed.size,
                            installed.sumOf { it.bytes } / 1048576.0))
                    }
                    items(installed, key = { it.id }) { entry ->
                        Column(Modifier.fillMaxWidth()) {
                            val pack = entry.manifest
                            Text(if (pack == null) stringResource(R.string.packs_broken)
                                else if (AppLanguage.selected(this@AnimationManagementActivity) == "en") pack.nameEn else pack.name,
                                style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.packs_size, entry.bytes / 1048576.0))
                            if (pack != null) Text(stringResource(R.string.packs_version, pack.version))
                            TextButton(onClick = { deleting = entry }, enabled = !busy && !runtime.running,
                                modifier = Modifier.testTag("delete_${entry.id}")) {
                                Text(stringResource(R.string.packs_delete))
                            }
                            HorizontalDivider()
                        }
                    }
                    if (installed.isEmpty()) item { Text(stringResource(R.string.packs_empty)) }
                }
                deleting?.let { entry ->
                    AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.packs_delete)) },
                        text = { Text(stringResource(R.string.packs_delete_confirm)) },
                        confirmButton = { TextButton(onClick = {
                            deleting = null
                            operation { store.delete(entry.id); R.string.packs_deleted }
                        }) { Text(stringResource(R.string.packs_delete)) } },
                        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
                }
            }
        }
    }
    // Check cancellation between archive blocks/frames. Called on the import worker.
    private var importJob: Job? = null
    private fun currentCoroutineContextCheck() { importJob?.ensureActive() }
    private fun operation(block: () -> Int?) {
        if (busy) return
        busy = true
        notice = null
        scope.launch {
            try {
                val job = currentCoroutineContext()[Job]
                importJob = job
                notice = withContext(Dispatchers.IO) { block() }
            } catch (e: CancellationException) { throw e }
            catch (e: PackFailure) { notice = when (e.reason) {
                "busy" -> R.string.packs_busy
                "duplicate" -> R.string.packs_duplicate
                "incompatible" -> R.string.packs_incompatible
                "large" -> R.string.packs_large
                "storage" -> R.string.packs_storage
                else -> R.string.packs_invalid
            } }
            catch (_: java.util.zip.ZipException) { notice = R.string.packs_invalid }
            catch (_: java.io.IOException) { notice = R.string.packs_storage }
            catch (_: SecurityException) { notice = R.string.packs_storage }
            catch (_: Exception) { notice = R.string.packs_invalid }
            finally {
                try { installed = withContext(Dispatchers.IO) { store.list() } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { notice = R.string.packs_storage }
                busy = false
                importJob = null
            }
        }
    }
    override fun onResume() { super.onResume(); if (::store.isInitialized) operation { null } }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
