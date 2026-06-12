package me.diamondforge.tokn.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordReminderScheduleTest {

    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_700_000_000_000L

    @Test
    fun intervalClampsToLadderBounds() {
        assertEquals(2, PasswordReminderSchedule.intervalDays(0))
        assertEquals(2, PasswordReminderSchedule.intervalDays(-5))
        assertEquals(40, PasswordReminderSchedule.intervalDays(99))
    }

    @Test
    fun notDueBeforeInterval() {
        assertFalse(PasswordReminderSchedule.isDue(now, now - 1 * day, 0))
    }

    @Test
    fun dueAtAndAfterInterval() {
        assertTrue(PasswordReminderSchedule.isDue(now, now - 2 * day, 0))
        assertTrue(PasswordReminderSchedule.isDue(now, now - 3 * day, 0))
    }

    @Test
    fun higherStageWaitsLonger() {
        assertFalse(PasswordReminderSchedule.isDue(now, now - 13 * day, 2))
        assertTrue(PasswordReminderSchedule.isDue(now, now - 14 * day, 2))
    }

    @Test
    fun successAdvancesAndCaps() {
        assertEquals(1, PasswordReminderSchedule.nextStageOnSuccess(0))
        assertEquals(4, PasswordReminderSchedule.nextStageOnSuccess(4))
        assertEquals(4, PasswordReminderSchedule.nextStageOnSuccess(99))
    }

    @Test
    fun failureDropsTwoStagesNeverBelowZero() {
        assertEquals(1, PasswordReminderSchedule.nextStageOnFailure(3))
        assertEquals(0, PasswordReminderSchedule.nextStageOnFailure(1))
        assertEquals(0, PasswordReminderSchedule.nextStageOnFailure(0))
    }

    @Test
    fun snoozeMakesItDueAboutOneDayLater() {
        val snoozed = PasswordReminderSchedule.snoozedLastShownAt(now, 3)
        assertFalse(PasswordReminderSchedule.isDue(now, snoozed, 3))
        assertFalse(PasswordReminderSchedule.isDue(now + 23 * 60 * 60 * 1000L, snoozed, 3))
        assertTrue(PasswordReminderSchedule.isDue(now + day, snoozed, 3))
    }

    @Test
    fun daysUntilDueCountsWholeDaysRoundingUp() {
        // Shown just now at stage 0 (2-day interval): two days remain.
        assertEquals(2, PasswordReminderSchedule.daysUntilDue(now, now, 0))
        // One full day has passed: one day remains.
        assertEquals(1, PasswordReminderSchedule.daysUntilDue(now, now - 1 * day, 0))
        // A partial day left still rounds up to one.
        assertEquals(1, PasswordReminderSchedule.daysUntilDue(now, now - 1 * day - 12 * 60 * 60 * 1000L, 0))
    }

    @Test
    fun daysUntilDueIsZeroWhenDueOrOverdue() {
        assertEquals(0, PasswordReminderSchedule.daysUntilDue(now, now - 2 * day, 0))
        assertEquals(0, PasswordReminderSchedule.daysUntilDue(now, now - 10 * day, 0))
    }

    @Test
    fun daysUntilDueUsesStageInterval() {
        // Stage 2 is a 14-day interval.
        assertEquals(14, PasswordReminderSchedule.daysUntilDue(now, now, 2))
    }
}
