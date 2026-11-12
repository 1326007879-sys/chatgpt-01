package io.github.chatgpt01.petai.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.chatgpt01.petai.AppLanguage
import io.github.chatgpt01.petai.R
import io.github.chatgpt01.petai.plugins.PluginRegistry
import io.github.chatgpt01.petai.ui.ReminderActivity
import java.util.concurrent.Executors

/** One application process owns all reminder mutations and platform effects, on a serial worker. */
object AndroidReminders {
    val worker = Executors.newSingleThreadExecutor()
    val HOST = ReminderOwner("host", "v1")
    private const val CHANNEL = "reminders_v1"
    fun store(c: Context) = ReminderStore(c.noBackupFilesDir.toPath().resolve("reminders/items.json"))
    fun coordinator(c: Context) = ReminderCoordinator(store(c)) { at -> replaceWakeup(c, at) }
    private fun replaceWakeup(c: Context, at: Long?, preferExact: Boolean = true) {
        val alarm = c.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(c, 0, Intent(c, ReminderReceiver::class.java).setAction("pet.reminders.WAKE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        var exact = false
        if (at == null) alarm.cancel(pending)
        else if (preferExact && (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms())) {
            try { alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending); exact = true }
            catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) }
        } else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        c.getSharedPreferences("reminder_status", Context.MODE_PRIVATE).edit()
            .putLong("scheduled_at", at ?: 0L)
            .putBoolean("exact", exact).apply()
    }
    fun authorized(c: Context, owner: ReminderOwner): Boolean {
        if (owner == HOST) return true
        val component = ComponentName.unflattenFromString(owner.component) ?: return false
        // Storage/package-manager errors must propagate: a transient failure is not a revocation.
        val registry = PluginRegistry(c)
        val entry = registry.scan().firstOrNull { it.component == component } ?: return false
        return registry.enabled(entry) && entry.stamp == owner.grantFingerprint
    }
    fun ready(c: Context): Boolean = (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(c).areNotificationsEnabled() &&
        c.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    /** Capture a revocation event: rapid re-enabling must not resurrect the old scheduled records. */
    fun revokeComponent(c: Context, component: String) {
        val app = c.applicationContext
        worker.execute {
            try {
                store(app).transaction { items ->
                    val revoked = items.filter { it.owner.component == component }
                    revoked.forEach { cancelNotification(app, it.id) }
                    if (revoked.isNotEmpty()) app.stopService(Intent(app, ReminderSpeechService::class.java))
                    items.removeAll { it.owner.component == component }
                }
                process(app, false)
            } catch (_: Exception) { recover(app) }
        }
    }
    fun refresh(c: Context, deliver: Boolean = false) {
        val app = c.applicationContext
        worker.execute {
            try { process(app, deliver); status(app, null) }
            catch (_: Exception) { recover(app) }
        }
    }
    fun recover(c: Context) {
        status(c, AppLanguage.wrap(c).getString(R.string.reminder_schedule_error))
        runCatching {
            replaceWakeup(c, System.currentTimeMillis() + 15 * 60_000, preferExact = false)
        }
    }
    fun status(c: Context, error: String?) {
        c.getSharedPreferences("reminder_status", Context.MODE_PRIVATE).edit().putString("error", error).apply()
    }
    fun process(c: Context, deliver: Boolean) {
        val allowed = store(c).transaction { items -> items.map { it.owner }.distinct().associateWith { authorized(c, it) } }
        store(c).transaction { items ->
            val revoked = items.filter { allowed[it.owner] != true }
            if (revoked.isNotEmpty()) c.stopService(Intent(c, ReminderSpeechService::class.java))
            revoked.forEach { cancelNotification(c, it.id) }
            items.removeAll { allowed[it.owner] != true }
        }
        val engine = coordinator(c)
        val now = System.currentTimeMillis()
        if (deliver && !ready(c)) {
            // Preserve pending reminders, retry after a bounded delay; show the permission issue in UI.
            if (engine.due(now) { allowed[it] == true }.isNotEmpty()) {
                throw IllegalStateException("notifications disabled")
            }
        }
        if (deliver) {
            engine.due(now) { allowed[it] == true }.take(20).forEach { (token, _) ->
                var spoken: Reminder? = null
                engine.deliver(token, now, { authorized(c, it) }) { row ->
                    post(c, row, now - token.at > 15 * 60_000)
                    c.getSharedPreferences("reminder_status", Context.MODE_PRIVATE).edit()
                        .putLong("delivered_at", now).apply()
                    if (row.draft.speak && now - token.at <= 15 * 60_000) spoken = row
                }
                spoken?.let { row ->
                    // Background-start restrictions may reject this. The notification's read action remains.
                    runCatching { c.startForegroundService(Intent(c, ReminderSpeechService::class.java)
                        .putExtra("id", row.id).putExtra("generation", row.generation).putExtra("automatic", true)) }
                }
            }
        }
        engine.reconcile(now) { allowed[it] == true }
    }
    fun cancelNotification(c: Context, id: String) = c.getSystemService(NotificationManager::class.java).cancel(id, 1)
    private fun post(c: Context, row: Reminder, missed: Boolean) {
        check(ready(c))
        val localized = AppLanguage.wrap(c)
        val manager = c.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, localized.getString(R.string.reminder_title), NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(c, ReminderActivity::class.java).setData(Uri.parse("petai://reminder/${row.id}")).putExtra("editId", row.id)
        val p = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val read = PendingIntent.getForegroundService(c, 0, Intent(c, ReminderSpeechService::class.java)
            .setData(Uri.parse("petai://read/${row.id}")).putExtra("id", row.id).putExtra("generation", row.generation), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(row.id, 1, NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(localized.getString(if (missed) R.string.reminder_missed else R.string.reminder_title))
            .setContentText(row.draft.text).setStyle(NotificationCompat.BigTextStyle().bigText(row.draft.text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).setAutoCancel(true)
            .setContentIntent(p).addAction(0, localized.getString(R.string.reminder_read), read).build())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.getSharedPreferences("reminder_status", Context.MODE_PRIVATE).edit()
            .putLong("receiver_at", System.currentTimeMillis()).apply()
        val pending = goAsync()
        AndroidReminders.worker.execute {
            try {
                AndroidReminders.process(context.applicationContext, intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED)
                AndroidReminders.status(context, null)
            } catch (_: Exception) { AndroidReminders.recover(context) }
            finally { pending.finish() }
        }
    }
}
