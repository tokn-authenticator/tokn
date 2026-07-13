package me.diamondforge.tokn.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.PurgeExpiredTrashUseCase.Companion.RETENTION_MS
import org.junit.Assert.assertEquals
import org.junit.Test

class PurgeExpiredTrashUseCaseTest {

    private val base = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun account(id: Long) =
        OtpAccount(id = id, issuer = "i", accountName = "n", secret = "JBSWY3DPEHPK3PXP")

    @Test
    fun `item deleted 29 days ago survives`() = runBlocking {
        val repo = FakeAccountRepository(listOf(account(1)), clock = { base })
        repo.deleteAccounts(setOf(1))

        val purged = PurgeExpiredTrashUseCase(repo)(now = base + 29 * day)

        assertEquals(0, purged)
        assertEquals(listOf(1L), repo.getTrashedAccounts().first().map { it.id })
    }

    @Test
    fun `item deleted 31 days ago is purged`() = runBlocking {
        val repo = FakeAccountRepository(listOf(account(1)), clock = { base })
        repo.deleteAccounts(setOf(1))

        val purged = PurgeExpiredTrashUseCase(repo)(now = base + 31 * day)

        assertEquals(1, purged)
        assertEquals(emptyList<Long>(), repo.getTrashedAccounts().first().map { it.id })
    }

    @Test
    fun `retention window is 30 days`() {
        assertEquals(30L * day, RETENTION_MS)
    }
}
