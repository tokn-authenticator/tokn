package me.diamondforge.tokn.home

import me.diamondforge.tokn.domain.model.AccountSort
import me.diamondforge.tokn.domain.model.OtpAccount
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the pure sort comparator used by [HomeViewModel]'s combine chain.
 * Each case checks ordering and the sortOrder tiebreaker. lastUsedAt = 0L
 * stands for "never used" and naturally sinks to the bottom of LAST_USED
 * without any null-special-casing.
 */
class AccountSortTest {

    private val alpha =
        account(id = 1, issuer = "Alpha", name = "a@x", sortOrder = 2, usage = 5, last = 1_000L)
    private val bravo =
        account(id = 2, issuer = "Bravo", name = "b@x", sortOrder = 0, usage = 10, last = 0L)
    private val charlie =
        account(id = 3, issuer = "charlie", name = "C@x", sortOrder = 1, usage = 0, last = 3_000L)

    private val fixture = listOf(alpha, bravo, charlie)

    @Test
    fun `CUSTOM preserves the input order`() {
        assertEquals(listOf(1L, 2L, 3L), ids(fixture.sortedFor(AccountSort.CUSTOM)))
    }

    @Test
    fun `ISSUER_ASC is case-insensitive`() {
        // alpha, Bravo, charlie -- charlie is lowercase, must still sort after Bravo.
        assertEquals(listOf(1L, 2L, 3L), ids(fixture.sortedFor(AccountSort.ISSUER_ASC)))
    }

    @Test
    fun `ISSUER_DESC reverses issuer order`() {
        assertEquals(listOf(3L, 2L, 1L), ids(fixture.sortedFor(AccountSort.ISSUER_DESC)))
    }

    @Test
    fun `NAME_ASC sorts by account name case-insensitively`() {
        assertEquals(listOf(1L, 2L, 3L), ids(fixture.sortedFor(AccountSort.NAME_ASC)))
    }

    @Test
    fun `NAME_DESC reverses account name order`() {
        assertEquals(listOf(3L, 2L, 1L), ids(fixture.sortedFor(AccountSort.NAME_DESC)))
    }

    @Test
    fun `USAGE_COUNT puts most used first`() {
        // bravo=10, alpha=5, charlie=0
        assertEquals(listOf(2L, 1L, 3L), ids(fixture.sortedFor(AccountSort.USAGE_COUNT)))
    }

    @Test
    fun `LAST_USED puts most recently used first and never-used last`() {
        // charlie=3000, alpha=1000, bravo=0 (never used)
        assertEquals(listOf(3L, 1L, 2L), ids(fixture.sortedFor(AccountSort.LAST_USED)))
    }

    @Test
    fun `USAGE_COUNT ties break on sortOrder ascending`() {
        val a = account(id = 10, issuer = "A", name = "a", sortOrder = 2, usage = 7, last = 0L)
        val b = account(id = 11, issuer = "B", name = "b", sortOrder = 0, usage = 7, last = 0L)
        assertEquals(listOf(11L, 10L), ids(listOf(a, b).sortedFor(AccountSort.USAGE_COUNT)))
    }

    @Test
    fun `LAST_USED never-used entries tie-break on sortOrder ascending`() {
        val x = account(id = 20, issuer = "X", name = "x", sortOrder = 1, usage = 0, last = 0L)
        val y = account(id = 21, issuer = "Y", name = "y", sortOrder = 0, usage = 0, last = 0L)
        val z = account(id = 22, issuer = "Z", name = "z", sortOrder = 0, usage = 0, last = 5_000L)
        // z has a real timestamp -- first. x and y are 0 (never used), tie-break by sortOrder asc.
        assertEquals(listOf(22L, 21L, 20L), ids(listOf(x, y, z).sortedFor(AccountSort.LAST_USED)))
    }

    private fun ids(items: List<OtpAccount>): List<Long> = items.map { it.id }

    private fun account(
        id: Long,
        issuer: String,
        name: String,
        sortOrder: Int,
        usage: Int,
        last: Long,
    ) = OtpAccount(
        id = id,
        issuer = issuer,
        accountName = name,
        secret = "JBSWY3DPEHPK3PXP",
        sortOrder = sortOrder,
        usageCount = usage,
        lastUsedAt = last,
    )
}
