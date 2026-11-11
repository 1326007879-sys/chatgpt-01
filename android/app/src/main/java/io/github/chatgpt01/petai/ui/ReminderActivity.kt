package io.github.chatgpt01.petai.ui

import android.Manifest
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.chatgpt01.petai.*
import io.github.chatgpt01.petai.R
import io.github.chatgpt01.petai.chat.ui.ChatTheme
import io.github.chatgpt01.petai.reminders.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.*
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ReminderActivity : LocalizedActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var rows by mutableStateOf<List<Reminder>>(emptyList())
    private var notice by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var editing by mutableStateOf<Reminder?>(null)
    private var initial by mutableStateOf(false)
    private var selectionWasSaved = false
    private var restoredSelection: String? = null
    private var refreshTick by mutableIntStateOf(0)
    private suspend fun <T> serial(block: () -> T): T = suspendCancellableCoroutine { continuation ->
        AndroidReminders.worker.execute {
            try { val value = block(); if (continuation.isActive) continuation.resume(value) }
            catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
        }
    }
    private fun action(block: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                serial { block(); AndroidReminders.process(applicationContext, false) }
                reload()
                notice = getString(R.string.reminder_saved)
            } catch (e: CancellationException) { throw e }
            catch (_: IllegalArgumentException) { notice = getString(R.string.reminder_save_error) }
            catch (_: Exception) { AndroidReminders.recover(applicationContext); notice = getString(R.string.reminder_save_error) }
            finally { busy = false; refreshTick++ }
        }
    }
    private suspend fun reload() {
        rows = serial { AndroidReminders.store(applicationContext).transaction { it.toList() } }
        if (!initial) {
            val requested = if (selectionWasSaved) restoredSelection else intent.getStringExtra("editId")
            editing = rows.firstOrNull { it.id == requested }
            initial = true
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        selectionWasSaved = savedInstanceState?.getBoolean("hasEditSelection", false) ?: false
        restoredSelection = savedInstanceState?.getString("editSelection")
        setContent { ChatTheme { Page() } }
    }
    override fun onResume() {
        super.onResume(); refreshTick++
        scope.launch { try { serial { AndroidReminders.process(applicationContext, false) }; reload() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { notice = getString(R.string.reminder_schedule_error) } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("hasEditSelection", true)
        outState.putString("editSelection", editing?.id)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    @Composable private fun Page() {
        if (!initial) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
                TextButton(onClick = { finish() }) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.reminder_title))
                if (notice.isNotEmpty()) Text(notice)
            }
            return
        }
        val suggested = remember { ZonedDateTime.now().plusMinutes(5) }
        val row = editing
        val key = row?.id ?: "new"
        val draftId = rememberSaveable(key) { UUID.randomUUID().toString() }
        var text by rememberSaveable(key) { mutableStateOf(row?.draft?.text ?: intent.getStringExtra("text").orEmpty().take(1000)) }
        var date by rememberSaveable(key) { mutableStateOf(row?.draft?.let { Instant.ofEpochMilli(it.firstAt).atZone(ZoneId.of(it.zoneId)).toLocalDate().toString() } ?: suggested.toLocalDate().toString()) }
        var time by rememberSaveable(key) { mutableStateOf(row?.draft?.let { if (it.repeat == ReminderRepeat.DAILY) it.localTime else Instant.ofEpochMilli(it.firstAt).atZone(ZoneId.of(it.zoneId)).toLocalTime().toString().take(5) } ?: suggested.toLocalTime().toString().take(5)) }
        var daily by rememberSaveable(key) { mutableStateOf(row?.draft?.repeat == ReminderRepeat.DAILY) }
        var speak by rememberSaveable(key) { mutableStateOf(row?.draft?.speak ?: false) }
        val zone = row?.draft?.zoneId ?: ZoneId.systemDefault().id
        val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshTick++ }
        // Read on each resume/permission callback; this state is deliberately not cached across settings changes.
        val permissionsReady = remember(refreshTick) { AndroidReminders.ready(this) }
        val exact = remember(refreshTick) { Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp).testTag("reminders_page"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = { finish() }) { Text(stringResource(R.string.back)) }
            Text(stringResource(R.string.reminder_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.reminder_hint))
            if (!permissionsReady) Button(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else runCatching { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }
            }) { Text(stringResource(R.string.reminder_allow_notifications)) }
            if (!exact) OutlinedButton(onClick = { runCatching { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) } }) { Text(stringResource(R.string.reminder_allow_exact)) }
            Text(stringResource(if (exact) R.string.reminder_exact_status else R.string.reminder_inexact_status))
            val error = getSharedPreferences("reminder_status", MODE_PRIVATE).getString("error", null)
            if (error != null) Text(error)
            if (notice.isNotEmpty()) Text(notice, Modifier.testTag("reminder_notice"))
            OutlinedTextField(text, { if (it.length <= 1000) text = it }, label = { Text(stringResource(R.string.reminder_content)) }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            OutlinedTextField(date, { date = it.take(10) }, label = { Text(stringResource(R.string.reminder_date)) }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(time, { time = it.take(5) }, label = { Text(stringResource(R.string.reminder_time)) }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Row {
                TextButton(enabled = !busy, onClick = {
                    val current = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
                    DatePickerDialog(this@ReminderActivity, { _, y, m, d -> date = LocalDate.of(y,m+1,d).toString() }, current.year,current.monthValue-1,current.dayOfMonth).show()
                }) { Text(stringResource(R.string.reminder_pick_date)) }
                TextButton(enabled = !busy, onClick = {
                    val current = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.now())
                    TimePickerDialog(this@ReminderActivity, { _, h, m -> time = String.format(java.util.Locale.ROOT,"%02d:%02d",h,m) },current.hour,current.minute,true).show()
                }) { Text(stringResource(R.string.reminder_pick_time)) }
            }
            Text(stringResource(R.string.reminder_zone, zone))
            Row { Checkbox(daily, { daily = it }, enabled = !busy); Text(stringResource(R.string.reminder_daily)) }
            Row { Checkbox(speak, { speak = it }, enabled = !busy); Text(stringResource(R.string.reminder_speak)) }
            Text(stringResource(R.string.reminder_voice_hint))
            Button(enabled = !busy && permissionsReady, onClick = {
                val owner = row?.owner ?: intent.getStringExtra("component")?.let { component ->
                    intent.getStringExtra("stamp")?.let { stamp -> runCatching { ReminderOwner(component, stamp) }.getOrNull() }
                } ?: AndroidReminders.HOST
                val id = row?.id ?: intent.getStringExtra("sourceReminderId") ?: draftId
                action {
                    check(AndroidReminders.authorized(applicationContext, owner))
                    val local = LocalDate.parse(date).atTime(LocalTime.parse(time))
                    val now = System.currentTimeMillis()
                    val draft = ReminderDraft(text, local.atZone(ZoneId.of(zone)).toInstant().toEpochMilli(), zone,
                        if (daily) ReminderRepeat.DAILY else ReminderRepeat.ONCE, time, speak)
                    AndroidReminders.coordinator(applicationContext).save(id, owner, draft, now)
                    AndroidReminders.cancelNotification(applicationContext, id)
                    stopService(Intent(this@ReminderActivity, ReminderSpeechService::class.java))
                }
            }, modifier = Modifier.testTag("save_reminder")) { Text(stringResource(R.string.reminder_save)) }
            if (row != null) TextButton(onClick = { editing = null }, enabled = !busy) { Text(stringResource(R.string.reminder_new)) }
            HorizontalDivider()
            Text(stringResource(R.string.reminder_list), style = MaterialTheme.typography.titleMedium)
            rows.forEach { entry ->
                Text(entry.draft.text)
                Text(entry.nextAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(entry.draft.zoneId)).toString() } ?: getString(R.string.reminder_completed))
                Row {
                    TextButton(onClick = { editing = entry }, enabled = !busy, modifier = Modifier.testTag("edit_reminder_${entry.id}")) { Text(stringResource(R.string.reminder_edit)) }
                    TextButton(onClick = { action { AndroidReminders.coordinator(applicationContext).cancel(entry.id, entry.owner); AndroidReminders.cancelNotification(applicationContext, entry.id); stopService(Intent(this@ReminderActivity, ReminderSpeechService::class.java)) } }, enabled = !busy) { Text(stringResource(R.string.reminder_delete)) }
                }
                HorizontalDivider()
            }
        }
    }
}
