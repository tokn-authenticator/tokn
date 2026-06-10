package me.diamondforge.tokn.passwordreminder

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
}
