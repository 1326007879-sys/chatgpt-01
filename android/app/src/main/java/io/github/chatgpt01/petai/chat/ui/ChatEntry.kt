package io.github.chatgpt01.petai.chat.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.github.chatgpt01.petai.R

/** Both user entry points target the same private, process-owned main-pet conversation. */
object ChatEntry {
    fun intent(context: Context) = Intent(context, ChatActivity::class.java).addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun open(context: Context): Boolean = try {
        context.startActivity(intent(context))
        true
    } catch (_: RuntimeException) {
        Toast.makeText(context, R.string.chat_open_failed, Toast.LENGTH_LONG).show()
        false
    }
}
