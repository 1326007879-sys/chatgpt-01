package io.github.chatgpt01.petai.reminders

import android.app.*
import android.content.*
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.chatgpt01.petai.AppLanguage
import io.github.chatgpt01.petai.R
import io.github.chatgpt01.petai.chat.voice.SystemSpeechOutput
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Replaceable, host-selected speech backend. Future plugins must implement an authorized adapter. */
interface ReminderVoiceProvider : AutoCloseable {
    suspend fun speak(text: String, voiceId: String)
    fun stop()
}
class SystemReminderVoice(context: Context, provider: String = "system", voiceId: String = "") : ReminderVoiceProvider {
    private val output = SystemSpeechOutput(context, useSavedEngine=false,
        enginePackage=if(provider=="neural") io.github.chatgpt01.petai.chat.voice.NeuralEngine.PACKAGE else null,
        requestedVoice=voiceId.takeIf { it.isNotEmpty() })
    override suspend fun speak(text: String, voiceId: String) {
        withTimeout((text.length * 250L + 10_000).coerceIn(40_000L, 240_000L)) {
            val ready = output.state.first { !it.loading }
            check(ready.canSpeak)
            if (voiceId.isNotEmpty()) check(ready.selected == voiceId) { "Voice unavailable" }
            output.speak(text)
            val done = output.state.first { !it.speaking }
            check(done.notice == null) { "Speech failed" }
        }
    }
    override fun stop() = output.stop()
    override fun close() = output.close()
}

/** Short-lived foreground playback: notification survives even when speech cannot start. */
class ReminderSpeechService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var voice: ReminderVoiceProvider? = null
    override fun onBind(intent: Intent?): IBinder? = null
    private val queue = java.util.ArrayDeque<Intent>()
    private var latestStart = 0
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStart = startId
        if (intent?.action == "STOP") {
            queue.clear(); job?.cancel(); voice?.stop(); stopSelf(); return START_NOT_STICKY
        }
        if (intent == null || queue.size >= 20) { if (job == null) stopSelf(); return START_NOT_STICKY }
        val strings = AppLanguage.wrap(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("reminder_speech", strings.getString(R.string.reminder_read), NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, ReminderSpeechService::class.java).setAction("STOP"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            startForeground(8042, NotificationCompat.Builder(this, "reminder_speech")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle(strings.getString(R.string.reminder_read))
                .setOngoing(true).addAction(0, strings.getString(R.string.stop), stop).build())
        } catch (_: RuntimeException) { stopSelf(); return START_NOT_STICKY }
        queue.addLast(Intent(intent))
        if (job?.isActive == true) return START_NOT_STICKY
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                while (queue.isNotEmpty()) {
                    val request = queue.removeFirst()
                    try {
                        val id = request.getStringExtra("id") ?: continue
                        val row = withContext(Dispatchers.IO) {
                            AndroidReminders.store(this@ReminderSpeechService).transaction { it.firstOrNull { row -> row.id == id } }
                                ?.takeIf { AndroidReminders.authorized(this@ReminderSpeechService, it.owner) }
                        } ?: continue
                        if (request.getStringExtra("generation") != row.generation) continue
                        if (!ReminderSpeechPolicy.allowed(request.getBooleanExtra("automatic", false),
                                getSystemService(KeyguardManager::class.java).isDeviceLocked,
                                getSystemService(android.media.AudioManager::class.java).ringerMode == android.media.AudioManager.RINGER_MODE_NORMAL,
                                nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL)) continue
                        val provider = SystemReminderVoice(this@ReminderSpeechService,row.draft.voiceProvider,row.draft.voiceId)
                        voice = provider
                        provider.speak(row.draft.text, row.draft.voiceId)
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { AndroidReminders.status(this@ReminderSpeechService, strings.getString(R.string.reminder_speech_error)) }
                    finally { voice?.close(); voice = null }
                }
            } finally { job = null; stopForeground(STOP_FOREGROUND_REMOVE); stopSelfResult(latestStart) }
        }
        job?.start()
        return START_NOT_STICKY
    }
    override fun onDestroy() { scope.cancel(); voice?.close(); voice = null; super.onDestroy() }
}
