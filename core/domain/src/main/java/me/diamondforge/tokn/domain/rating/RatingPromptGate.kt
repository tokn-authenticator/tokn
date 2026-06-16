package me.diamondforge.tokn.domain.rating

object RatingPromptGate {

    const val MIN_DAYS_INSTALLED = 5
    const val MIN_LAUNCHES = 5
    const val SNOOZE_DAYS = 14

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    fun isEligible(
        now: Long,
        firstInstallTime: Long,
        launchCount: Int,
        hasTokens: Boolean,
        fromPlayStore: Boolean,
        handled: Boolean,
        snoozedUntil: Long,
    ): Boolean =
        !handled &&
            fromPlayStore &&
            hasTokens &&
            launchCount >= MIN_LAUNCHES &&
            now - firstInstallTime >= MIN_DAYS_INSTALLED * MILLIS_PER_DAY &&
            now >= snoozedUntil

    fun snoozedUntil(now: Long): Long = now + SNOOZE_DAYS * MILLIS_PER_DAY
}