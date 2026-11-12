package io.github.chatgpt01.petai.chat.voice

import android.content.Context
import android.content.pm.PackageManager

/** This prototype only trusts our separately installed engine with the same signing identity. */
object NeuralEngine {
    const val PACKAGE = "io.github.chatgpt01.voice"
    const val PREFIX = "petai.neural."
    fun trusted(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE,0)
        context.packageManager.checkSignatures(context.packageName,PACKAGE) == PackageManager.SIGNATURE_MATCH
    }.getOrDefault(false)
    fun accepts(requestedEngine: String?, voice: String): Boolean = requestedEngine != PACKAGE || voice.startsWith(PREFIX)
}
