package io.github.chatgpt01.petai

import android.content.ComponentName
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.chatgpt01.petai.ui.PluginManagementActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PluginEntryIntegrationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<PluginManagementActivity>()
    @Test fun placeholderPageIsPrivateAndExplainsItsScope() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val info = context.packageManager.getActivityInfo(ComponentName(context, PluginManagementActivity::class.java), 0)
        assertFalse(info.exported)
        compose.onNodeWithTag("plugins_page").assertExists()
        compose.onNodeWithText(context.getString(R.string.plugins_pending)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.plugins_empty)).assertIsDisplayed()
    }
}
