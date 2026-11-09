package io.github.chatgpt01.petai

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.chatgpt01.petai.ui.AnimationManagementActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Needs a device; never imports into or deletes the user's library automatically. */
class AnimationManagementDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<AnimationManagementActivity>()
    @Test fun managerExposesLocalImportAndProtectsBundledAnimations() {
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("import_animation_pack").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("animation_management").assertExists()
        compose.onNodeWithTag("import_animation_pack").assertExists()
        val ctx = compose.activity
        val info = ctx.packageManager.getActivityInfo(android.content.ComponentName(ctx, AnimationManagementActivity::class.java), 0)
        assertFalse(info.exported)
        assertEquals(11, AssetCatalog.load(ctx.assets).clips.size)
        // Core assets remain in the APK and have no writable external directory.
        assertTrue(AssetCatalog.load(ctx.assets).clips.all { it.installedDirectory == null })
    }
}
