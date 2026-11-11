package io.github.chatgpt01.petai.ui

import android.os.Bundle
import androidx.activity.compose.setContent
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
import io.github.chatgpt01.petai.*
import io.github.chatgpt01.petai.R
import io.github.chatgpt01.petai.animation.*
import io.github.chatgpt01.petai.chat.ui.ChatTheme
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

class OnlineAnimationsActivity : LocalizedActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val downloader = AnimationDownloader()
    private lateinit var store: AnimationPackStore
    private lateinit var bundled: ByteArray
    private var catalog by mutableStateOf<List<OnlineAnimation>>(emptyList())
    private var selected by mutableStateOf<Set<String>>(emptySet())
    private var installed by mutableStateOf<Set<String>>(emptySet())
    private var failures by mutableStateOf<Set<String>>(emptySet())
    private var busy by mutableStateOf(false)
    private var progress by mutableStateOf("")
    private var notice by mutableStateOf<Int?>(null)
    private var job: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        store = AnimationPackStore(File(noBackupFilesDir,"animations"))
        bundled = resources.openRawResource(R.raw.online_animations).use { it.readBytes() }
        catalog = OnlineAnimationCatalog.parse(bundled)
        loadInstalled()
        setContent { ChatTheme { Page() } }
    }
    private fun loadInstalled() {
        scope.launch { try { installed = withContext(Dispatchers.IO) { store.list().filter { it.manifest != null }.map { it.id }.toSet() } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { notice = R.string.online_failed } }
    }
    private fun refresh() {
        if (busy) return
        busy = true; notice = null
        job = scope.launch {
            val token = currentCoroutineContext()[Job]!!
            try { catalog = withContext(Dispatchers.IO) { downloader.catalog(OnlineAnimationCatalog.sha(bundled)) { token.ensureActive() } }; loadInstalled() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { token.ensureActive(); notice = R.string.online_refresh_failed }
            finally { busy = false }
        }
    }
    private fun install(ids: Set<String>) {
        if (busy) return
        val queue = catalog.filter { it.id in ids && it.id !in installed }
        if (queue.isEmpty()) return
        busy = true; notice = null; failures = emptySet()
        job = scope.launch {
            val token = currentCoroutineContext()[Job]!!
            try {
                for ((index,item) in queue.withIndex()) {
                    token.ensureActive()
                    val name = if (AppLanguage.selected(this@OnlineAnimationsActivity) == "en") item.nameEn else item.name
                    val temporary = File(cacheDir, "animation-${UUID.randomUUID()}.part")
                    var lastPercent = -1
                    try {
                        withContext(Dispatchers.IO) {
                            downloader.download(item, temporary, { token.ensureActive() }) { bytes ->
                                val percent = (bytes * 100 / item.bytes).toInt()
                                if (percent != lastPercent) { lastPercent = percent; scope.launch { progress = "${index+1}/${queue.size} · $name · $percent%" } }
                            }
                            token.ensureActive()
                            temporary.inputStream().use { store.installVerified(it, ::validAnimationFrame, item.id, item.version) { token.ensureActive() } }
                        }
                        installed = installed + item.id; selected = selected - item.id
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { token.ensureActive(); failures = failures + item.id }
                    finally { withContext(NonCancellable + Dispatchers.IO) { temporary.delete() } }
                }
                notice = if (failures.isEmpty()) R.string.online_done else R.string.online_failed
            } finally { busy = false; progress = ""; loadInstalled() }
        }
    }
    @Composable private fun Page() {
        val runtime by PetRuntime.state.collectAsState()
        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp).testTag("online_animations"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                TextButton(onClick = { finish() }) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.online_animations_title),style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.online_hint))
                if (runtime.running) Button(onClick = { PetOverlayService.command(this@OnlineAnimationsActivity, PetOverlayService.STOP) }) { Text(stringResource(R.string.packs_stop)) }
                OutlinedButton(onClick = ::refresh, enabled = !busy) { Text(stringResource(R.string.online_refresh)) }
                Text(stringResource(R.string.online_selected, selected.size, catalog.filter { it.id in selected }.sumOf { it.bytes }/1048576.0))
                Button(onClick = { install(selected) }, enabled = !busy && selected.isNotEmpty() && !runtime.running) { Text(stringResource(R.string.online_install)) }
                if (failures.isNotEmpty()) OutlinedButton(onClick = { install(failures) }, enabled = !busy && !runtime.running) { Text(stringResource(R.string.online_retry)) }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth()); Text(progress)
                    TextButton(onClick = { job?.cancel(); downloader.cancel(); notice = R.string.online_cancelled }) { Text(stringResource(R.string.cancel)) }
                }
                notice?.let { Text(stringResource(it), Modifier.testTag("online_notice")) }
            }
            items(catalog, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(item.id in selected, { checked -> selected = if (checked) selected + item.id else selected - item.id }, enabled = !busy && item.id !in installed)
                    Column {
                        Text(if (AppLanguage.selected(this@OnlineAnimationsActivity) == "en") item.nameEn else item.name)
                        Text(stringResource(R.string.packs_size, item.bytes / 1048576.0))
                        if (item.id in installed) Text(stringResource(R.string.online_installed))
                        if (item.id in failures) Text(stringResource(R.string.online_item_failed))
                    }
                }
            }
        }
    }
    override fun onDestroy() { job?.cancel(); downloader.cancel(); scope.cancel(); super.onDestroy() }
}
