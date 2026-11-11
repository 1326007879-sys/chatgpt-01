package io.github.chatgpt01.petai

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.rule.GrantPermissionRule
import io.github.chatgpt01.petai.reminders.*
import io.github.chatgpt01.petai.ui.ReminderActivity
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Dedicated test device only. Uses a unique host reminder; never reads note/plugin credentials. */
class ReminderDeliveryDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ReminderActivity>()
    @get:Rule val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @Test fun reminderPageAndPlatformComponentsArePrivate() {
        compose.waitUntil(20000) { compose.onAllNodesWithTag("save_reminder").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reminders_page").assertExists()
        compose.onNodeWithTag("save_reminder").assertExists()
        val context = compose.activity
        assertFalse(context.packageManager.getActivityInfo(ComponentName(context,ReminderActivity::class.java),0).exported)
        assertFalse(context.packageManager.getReceiverInfo(ComponentName(context,ReminderReceiver::class.java),0).exported)
        assertFalse(context.packageManager.getServiceInfo(ComponentName(context,ReminderSpeechService::class.java),0).exported)
    }
    @Test fun dueNotificationAndCancelUsePersistedRecord() {
        val context = compose.activity.applicationContext
        Assume.assumeTrue(AndroidReminders.ready(context))
        val id = "test-" + UUID.randomUUID()
        val engine = AndroidReminders.coordinator(context)
        try {
            AndroidReminders.worker.submit {
                val at = System.currentTimeMillis()-60000
                engine.save(id,AndroidReminders.HOST,ReminderDraft("reminder device test",at,"UTC"),at-1)
                AndroidReminders.process(context,true)
                assertNull(engine.list(AndroidReminders.HOST).single { it.id == id }.nextAt)
            }.get(20,TimeUnit.SECONDS)
            val manager = context.getSystemService(NotificationManager::class.java)
            compose.waitUntil(20000) { manager.activeNotifications.any { it.tag == id } }
            val notification = manager.activeNotifications.single { it.tag == id }.notification
            assertNotNull(notification.contentIntent)
            assertTrue(notification.actions.isNotEmpty())
        } finally {
            AndroidReminders.worker.submit {
                engine.cancel(id,AndroidReminders.HOST)
                AndroidReminders.cancelNotification(context,id)
                AndroidReminders.process(context,false)
            }.get(20,TimeUnit.SECONDS)
        }
    }
    @Test fun exactAlarmWakesReceiverAndPostsNotification() {
        val context = compose.activity.applicationContext
        val alarm = context.getSystemService(android.app.AlarmManager::class.java)
        Assume.assumeTrue(AndroidReminders.ready(context))
        Assume.assumeTrue(android.os.Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms())
        val id = "alarm-test-" + UUID.randomUUID()
        val engine = AndroidReminders.coordinator(context)
        try {
            AndroidReminders.worker.submit {
                val now = System.currentTimeMillis()
                engine.save(id,AndroidReminders.HOST,ReminderDraft("alarm receiver test",now+5000,"UTC"),now)
                AndroidReminders.process(context,false)
            }.get(20,TimeUnit.SECONDS)
            val manager = context.getSystemService(NotificationManager::class.java)
            compose.waitUntil(60000) { manager.activeNotifications.any { it.tag == id } }
            AndroidReminders.worker.submit {
                assertNull(engine.list(AndroidReminders.HOST).single { it.id == id }.nextAt)
            }.get(20,TimeUnit.SECONDS)
        } finally {
            AndroidReminders.worker.submit {
                engine.cancel(id,AndroidReminders.HOST)
                AndroidReminders.cancelNotification(context,id)
                AndroidReminders.process(context,false)
            }.get(20,TimeUnit.SECONDS)
        }
    }

    @Test fun editingSelectionSurvivesActivityRecreation() {
        val context = compose.activity.applicationContext
        val id = "recreate-test-" + UUID.randomUUID()
        val engine = AndroidReminders.coordinator(context)
        try {
            AndroidReminders.worker.submit {
                val now = System.currentTimeMillis()
                engine.save(id,AndroidReminders.HOST,ReminderDraft("edit state test",now+600000,"UTC"),now)
            }.get(20,TimeUnit.SECONDS)
            compose.activityRule.scenario.recreate()
            compose.waitUntil(20000) { compose.onAllNodesWithTag("edit_reminder_$id").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("edit_reminder_$id").performScrollTo().performClick()
            compose.activityRule.scenario.recreate()
            compose.waitUntil(20000) { compose.onAllNodesWithTag("save_reminder").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(compose.activity.getString(R.string.reminder_new)).assertExists()
        } finally {
            AndroidReminders.worker.submit {
                engine.cancel(id,AndroidReminders.HOST)
                AndroidReminders.cancelNotification(context,id)
                AndroidReminders.process(context,false)
            }.get(20,TimeUnit.SECONDS)
        }
    }

}
