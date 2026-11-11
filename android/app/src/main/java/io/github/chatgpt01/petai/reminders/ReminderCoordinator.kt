package io.github.chatgpt01.petai.reminders

/** Module 2 supplies AlarmManager: one wakeup, idempotent replacement/cancellation, no repeating alarm. */
fun interface ReminderWakeup { fun replace(atEpochMs: Long?) }

/** Internal host API v1, not an exported Binder endpoint and not an expansion of plugin protocol v1. */
class ReminderCoordinator(private val store: ReminderStore, private val wakeup: ReminderWakeup) {
    fun save(id: String, owner: ReminderOwner, draft: ReminderDraft, now: Long): Reminder = store.transaction { items ->
        require(draft.firstAt > now) { "Reminder time must be in the future" }
        val previous = items.find { it.id == id }
        require(previous == null || previous.owner == owner) { "Owner mismatch" }
        val row = Reminder(id, owner, draft, (previous?.revision ?: 0) + 1)
        items.removeAll { it.id == id }; items.add(row)
        row
    }

    fun list(owner: ReminderOwner): List<Reminder> = store.transaction { it.filter { row -> row.owner == owner }.toList() }

    fun cancel(id: String, owner: ReminderOwner): Boolean = store.transaction { items ->
        items.removeAll { it.id == id && it.owner == owner }
    }

    /** Disable, uninstall or a changed certificate/grant revokes all old owner's pending records. */
    fun revoke(owner: ReminderOwner): Int = store.transaction { items ->
        val before = items.size; items.removeAll { it.owner == owner }; before - items.size
    }

    /** Called after mutations and on startup/boot/clock change. Read+replace share the store lock. */
    fun reconcile(now: Long, authorized: (ReminderOwner) -> Boolean) = store.transaction { items ->
        val next = items.filter { it.nextAt != null && authorized(it.owner) }.minOfOrNull { it.nextAt!! }
        wakeup.replace(next?.coerceAtLeast(now))
    }

    /** No mutation before notification delivery; missed-time policy belongs to the delivery adapter. */
    fun due(now: Long, authorized: (ReminderOwner) -> Boolean): List<Pair<ReminderOccurrence, Reminder>> = store.transaction { items ->
        items.filter { it.nextAt != null && it.nextAt <= now && authorized(it.owner) }
            .sortedBy { it.nextAt }.map { ReminderOccurrence(it.id, it.revision, it.generation, it.nextAt!!) to it }
    }

    /** Delivery adapter must post synchronously, not re-enter this repository, and throw on failure.
     * Serialized with edits/revocation across all repositories for this file. A process death after
     * posting but before commit may retry: use stable notification id and onlyAlertOnce in module 2.
     */
    fun deliver(token: ReminderOccurrence, now: Long, authorized: (ReminderOwner) -> Boolean,
                post: (Reminder) -> Unit): Boolean = store.transaction { items ->
        val index = matching(items, token, now)
        if (index < 0 || !authorized(items[index].owner)) false else {
            post(items[index])
            advance(items, index, now)
            true
        }
    }

    /** Explicit missed-event acknowledgement; delivery should use deliver() for authorization checks. */
    fun acknowledge(token: ReminderOccurrence, now: Long): Boolean = store.transaction { items ->
        val index = matching(items, token, now)
        if (index < 0) false else { advance(items, index, now); true }
    }

    private fun matching(items: List<Reminder>, token: ReminderOccurrence, now: Long): Int =
        if (now < token.at) -1 else items.indexOfFirst {
            it.id == token.id && it.revision == token.revision && it.generation == token.generation && it.nextAt == token.at
        }

    private fun advance(items: MutableList<Reminder>, index: Int, now: Long) {
        val row = items[index]
        items[index] = row.copy(revision = row.revision + 1,
            nextAt = if (row.draft.repeat == ReminderRepeat.DAILY) row.draft.after(now) else null)
    }
}
