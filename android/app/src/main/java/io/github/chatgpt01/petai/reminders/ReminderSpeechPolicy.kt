package io.github.chatgpt01.petai.reminders

object ReminderSpeechPolicy {
    /** Manual playback may override quiet mode, but never reveal private text on a locked device. */
    fun allowed(automatic: Boolean, locked: Boolean, normalRinger: Boolean, allInterruptions: Boolean): Boolean =
        !locked && (!automatic || (normalRinger && allInterruptions))
}
