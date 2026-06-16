package me.diamondforge.tokn.domain.rating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingPromptGateTest {

    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_700_000_000_000L
    private fun eligible(
        firstInstallTime: Long = now - 5 * day,
        launchCount: Int = 5,
        hasTokens: Boolean = true,
        fromPlayStore: Boolean = true,
        handled: Boolean = false,
        snoozedUntil: Long = 0L,
    ) = RatingPromptGate.isEligible(
        now = now,
        firstInstallTime = firstInstallTime,
        launchCount = launchCount,
        hasTokens = hasTokens,
        fromPlayStore = fromPlayStore,
        handled = handled,
        snoozedUntil = snoozedUntil,
    )

    @Test
    fun eligibleWhenEveryConditionMet() {
        assertTrue(eligible())
    }

    @Test
    fun notEligibleBeforeFiveDaysInstalled() {
        assertFalse(eligible(firstInstallTime = now - 4 * day))
        assertTrue(eligible(firstInstallTime = now - 5 * day))
    }

    @Test
    fun notEligibleBelowFiveLaunches() {
        assertFalse(eligible(launchCount = 4))
        assertTrue(eligible(launchCount = 5))
    }

    @Test
    fun notEligibleWithoutTokens() {
        assertFalse(eligible(hasTokens = false))
    }

    @Test
    fun notEligibleWhenNotFromPlayStore() {
        assertFalse(eligible(fromPlayStore = false))
    }

    @Test
    fun notEligibleOnceHandled() {
        assertFalse(eligible(handled = true))
    }

    @Test
    fun notEligibleWhileSnoozed() {
        assertFalse(eligible(snoozedUntil = now + day))
        assertTrue(eligible(snoozedUntil = now))
    }

    @Test
    fun snoozeMovesTargetTwoWeeksOut() {
        assertEquals(now + 14 * day, RatingPromptGate.snoozedUntil(now))
    }
}