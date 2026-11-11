package io.github.chatgpt01.petai.reminders

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Host-owned identity. Never take owner or grantFingerprint from plugin-supplied JSON. */
data class ReminderOwner(val component: String, val grantFingerprint: String) {
    init { require(component.isNotBlank() && component.length <= 256); require(grantFingerprint.length in 1..256) }
}

enum class ReminderRepeat { ONCE, DAILY }

data class ReminderDraft(
    val text: String,
    val firstAt: Long,
    val zoneId: String,
    val repeat: ReminderRepeat = ReminderRepeat.ONCE,
    val localTime: String = "00:00",
    val speak: Boolean = false,
    val voiceProvider: String = "system",
    val voiceId: String = "",
) {
    init {
        require(text.isNotBlank() && text.length <= 1000)
        require(firstAt in 1..253402214400000L)
        ZoneId.of(zoneId)
        require(localTime.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")))
        val time = LocalTime.parse(localTime)
        if (repeat == ReminderRepeat.DAILY) {
            val instant = Instant.ofEpochMilli(firstAt)
            require(instant.atZone(ZoneId.of(zoneId)).toLocalDate().atTime(time)
                .atZone(ZoneId.of(zoneId)).toInstant() == instant)
        }
        // Future voice plugins require a separately granted provider; no arbitrary component loading.
        require(voiceProvider == "system" && voiceId.length <= 256)
    }

    /** Fixed chosen timezone. DST gap shifts forward; overlap uses the earlier offset, once a day. */
    fun after(now: Long): Long {
        require(repeat == ReminderRepeat.DAILY)
        val zone = ZoneId.of(zoneId)
        val time = LocalTime.parse(localTime)
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val today = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
        return if (today > now) today else date.plusDays(1).atTime(time).atZone(zone).toInstant().toEpochMilli()
    }
}

data class Reminder(
    val id: String,
    val owner: ReminderOwner,
    val draft: ReminderDraft,
    val revision: Long = 1,
    val generation: String = java.util.UUID.randomUUID().toString(),
    val nextAt: Long? = draft.firstAt,
) {
    init {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
        require(revision > 0)
        java.util.UUID.fromString(generation)
        require(nextAt == null || nextAt in draft.firstAt..253402214400000L)
    }
}

/** A delivery token becomes invalid when edited/cancelled. Notification identity should use id. */
data class ReminderOccurrence(val id: String, val revision: Long, val generation: String, val at: Long)
