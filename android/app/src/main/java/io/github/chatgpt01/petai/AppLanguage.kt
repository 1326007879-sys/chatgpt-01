package io.github.chatgpt01.petai

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.ComponentActivity
import java.util.Locale

/** One persisted language for all app pages and overlay UI; default to simplified Chinese. */
object AppLanguage {
    const val STORE = "app_language"
    const val KEY = "language"
    fun selected(context: Context): String = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        .getString(KEY, "zh-CN").let { if (it == "en") "en" else "zh-CN" }
    fun save(context: Context, tag: String): Boolean {
        require(tag in setOf("zh-CN", "en"))
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit()
    }
    fun wrap(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(Locale.forLanguageTag(selected(context))))
        return context.createConfigurationContext(configuration)
    }
}

/** Recreate a returning page if another page changed the shared language. */
open class LocalizedActivity : ComponentActivity() {
    private var applied = ""
    override fun attachBaseContext(newBase: Context) {
        applied = AppLanguage.selected(newBase)
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }
    override fun onResume() {
        super.onResume()
        if (applied != AppLanguage.selected(baseContext)) recreate()
    }
}
