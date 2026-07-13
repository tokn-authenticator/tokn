package me.diamondforge.tokn.backup.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupWriterTest {

    @Test
    fun `backup file name uses enc kv for encrypted`() {
        val name = backupFileName(encrypted = true, timestamp = 0L)
        assertTrue(name.startsWith("tokn-backup-"))
        assertTrue(name.endsWith(".enc.kv"))
    }

    @Test
    fun `backup file name uses json for plaintext`() {
        assertTrue(backupFileName(encrypted = false, timestamp = 0L).endsWith(".json"))
    }

    @Test
    fun `parse round trips a generated name`() {
        val name = backupFileName(encrypted = true, timestamp = daysAgo(0))
        assertEquals(parseBackupTimestamp(name)!! / 1000, daysAgo(0) / 1000)
    }

    @Test
    fun `parse rejects unrelated names`() {
        assertNull(parseBackupTimestamp("notes.txt"))
        assertNull(parseBackupTimestamp("tokn-backup-nope.enc.kv"))
        assertNull(parseBackupTimestamp("tokn-backup-20201302-000000.json"))
    }

    @Test
    fun `prune keeps newest N and drops oldest`() {
        val pruned = filesToPrune(listOf(today, dayBeforeYesterday, yesterday), versionsToKeep = 1)
        assertEquals(listOf(dayBeforeYesterday, yesterday), pruned)
    }

    @Test
    fun `prune ignores files that do not match the pattern`() {
        assertEquals(
            listOf(yesterday),
            filesToPrune(listOf(yesterday, "random.json", today), versionsToKeep = 1),
        )
    }

    @Test
    fun `prune returns nothing when under the limit or infinite`() {
        val names = listOf(today)
        assertTrue(filesToPrune(names, versionsToKeep = 5).isEmpty())
        assertTrue(filesToPrune(names, versionsToKeep = 0).isEmpty())
    }

    private val dayBeforeYesterday = backupFileName(encrypted = true, timestamp = daysAgo(2))
    private val yesterday = backupFileName(encrypted = true, timestamp = daysAgo(1))
    private val today = backupFileName(encrypted = true, timestamp = daysAgo(0))

    private fun daysAgo(days: Int): Long =
        System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
}
