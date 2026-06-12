package me.diamondforge.tokn.domain.security

/** Escalating cadence: a correct entry widens the gap to the next rung, a wrong one narrows it. */
object PasswordReminderSchedule {

    private val LADDER_DAYS = intArrayOf(2, 7, 14, 28, 40)

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    private const val SNOOZE_DAYS = 1L

    fun intervalDays(stage: Int): Int = LADDER_DAYS[stage.coerceIn(0, LADDER_DAYS.lastIndex)]

    fun isDue(now: Long, lastShownAt: Long, stage: Int): Boolean =
        now - lastShownAt >= intervalDays(stage) * MILLIS_PER_DAY

    /** Whole days until the next prompt, rounded up; 0 once it is due. For the settings blurb. */
    fun daysUntilDue(now: Long, lastShownAt: Long, stage: Int): Int {
        val remaining = lastShownAt + intervalDays(stage) * MILLIS_PER_DAY - now
        if (remaining <= 0L) return 0
        return ((remaining + MILLIS_PER_DAY - 1L) / MILLIS_PER_DAY).toInt()
    }

    fun nextStageOnSuccess(stage: Int): Int =
        (stage + 1).coerceIn(0, LADDER_DAYS.lastIndex)

    // Two rungs back, not one, so a forgotten password returns sooner.
    fun nextStageOnFailure(stage: Int): Int =
        (stage - 2).coerceIn(0, LADDER_DAYS.lastIndex)

    // Derived so a snooze needs no separate "snoozed until" key.
    fun snoozedLastShownAt(now: Long, stage: Int): Long =
        now - (intervalDays(stage) - SNOOZE_DAYS) * MILLIS_PER_DAY
}
