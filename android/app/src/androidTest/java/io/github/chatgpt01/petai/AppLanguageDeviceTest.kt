package io.github.chatgpt01.petai

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Device tests: language persistence/recreation and independent context resources. */
class AppLanguageDeviceTest {
    @Test fun languagePersistsAcrossRecreationWithoutChangingSystemLocale() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = AppLanguage.selected(app)
        val systemLocales = app.resources.configuration.locales.toLanguageTags()
        try {
            assertTrue(AppLanguage.save(app, "zh-CN"))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { assertEquals("和主宠聊天", it.getString(R.string.chat_open)) }
                assertTrue(AppLanguage.save(app, "en"))
                scenario.recreate()
                scenario.onActivity {
                    assertEquals("Chat with main pet", it.getString(R.string.chat_open))
                    assertEquals("Model settings", it.getString(R.string.model_settings))
                    assertEquals("Plugin management", it.getString(R.string.plugins_title))
                }
                assertTrue(AppLanguage.save(app, "zh-CN"))
                scenario.recreate()
                scenario.onActivity { assertEquals("语音设置", it.getString(R.string.voice_settings)) }
            }
            assertEquals(systemLocales, app.resources.configuration.locales.toLanguageTags())
        } finally { AppLanguage.save(app, previous) }
    }
}
