package io.github.chatgpt01.petai

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.chatgpt01.petai.chat.ui.ChatActivity
import io.github.chatgpt01.petai.chat.ui.ChatEntry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Requires a device. No model credentials, network request or permission changes. */
class ChatEntryDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun resumedChat() = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<ChatActivity>().firstOrNull()

    @After fun closeChatPage() {
        instrumentation.runOnMainSync { resumedChat()?.finish() }
        instrumentation.waitForIdleSync()
    }
    @Test fun entryIntentHasPrivateExplicitDestinationAndNoPayload() {
        val intent = ChatEntry.intent(context)
        assertEquals(ChatActivity::class.java.name, intent.component!!.className)
        assertEquals(context.packageName, intent.component!!.packageName)
        assertTrue(intent.extras == null || intent.extras!!.isEmpty)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK == 0)
        @Suppress("DEPRECATION")
        val info = context.packageManager.getActivityInfo(intent.component!!, 0)
        assertFalse(info.exported)
    }
    @Test fun mainEntryOpensChatAndReturnsWithoutChangingPetState() {
        val wasRunning = PetRuntime.state.value.running
        compose.onNodeWithTag("open_chat").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("和桌宠聊聊").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithTag("open_chat").assertExists()
        assertEquals(wasRunning, PetRuntime.state.value.running)
    }
    @Test fun applicationContextEntryReusesExistingChatPage() {
        compose.onNodeWithTag("open_chat").performScrollTo().performClick()
        awaitCondition { resumedChat() != null }
        var first: ChatActivity? = null
        instrumentation.runOnMainSync { first = resumedChat(); assertTrue(ChatEntry.open(context)) }
        awaitCondition { resumedChat() === first }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { assertSame(first, resumedChat()) }
    }
}
