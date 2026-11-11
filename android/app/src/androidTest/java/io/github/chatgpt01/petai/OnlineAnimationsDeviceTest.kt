package io.github.chatgpt01.petai

import android.content.ComponentName
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.chatgpt01.petai.ui.OnlineAnimationsActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OnlineAnimationsDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<OnlineAnimationsActivity>()
    @Test fun bundledDirectoryCanSelectWithoutDownloading() {
        compose.onNodeWithTag("online_animations").assertExists()
        val ctx = compose.activity
        assertFalse(ctx.packageManager.getActivityInfo(ComponentName(ctx,OnlineAnimationsActivity::class.java),0).exported)
        org.junit.Assume.assumeFalse(java.io.File(ctx.noBackupFilesDir,"animations/ext_ec4103857fc37f6d5ce12255").exists())
        compose.onNodeWithTag("online_animations").performScrollToIndex(1)
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithTag("online_animations").performScrollToIndex(0)
        val size = 13960138 / 1048576.0
        compose.onNodeWithText(ctx.getString(R.string.online_selected,1,size)).assertExists()
        // No download action is pressed: tests never install or remove the user's animation library.
    }
}
