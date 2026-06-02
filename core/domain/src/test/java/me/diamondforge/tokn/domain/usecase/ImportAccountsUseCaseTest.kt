package me.diamondforge.tokn.domain.usecase

import kotlinx.coroutines.runBlocking
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportAccountsUseCaseTest {

    private fun setup(initial: List<OtpAccount> = emptyList()): Pair<ImportAccountsUseCase, FakeAccountRepository> {
        val repo = FakeAccountRepository(initial)
        return ImportAccountsUseCase(repo) to repo
    }

    private fun account(secret: String, issuer: String = "i", name: String = "n", id: Long = 0) =
        OtpAccount(id = id, issuer = issuer, accountName = name, secret = secret)

    @Test
    fun `empty incoming list imports nothing`() = runBlocking {
        val (sut, _) = setup()
        val summary = sut(emptyList())
        assertEquals(ImportAccountsUseCase.Summary(imported = 0, skipped = 0), summary)
    }

    @Test
    fun `all new accounts are imported`() = runBlocking {
        val (sut, repo) = setup()
        val summary = sut(
            listOf(account("AAAA1111"), account("BBBB2222"), account("CCCC3333")),
        )
        assertEquals(ImportAccountsUseCase.Summary(3, 0), summary)
        assertEquals(3, repo.snapshot.size)
    }

    @Test
    fun `already-present secret is skipped`() = runBlocking {
        val (sut, repo) = setup(initial = listOf(account("AAAA1111", id = 1)))
        val summary = sut(listOf(account("AAAA1111"), account("BBBB2222")))
        assertEquals(ImportAccountsUseCase.Summary(imported = 1, skipped = 1), summary)
        assertEquals(2, repo.snapshot.size)
    }

    @Test
    fun `secret comparison normalizes whitespace dashes and case`() = runBlocking {
        val (sut, repo) = setup(initial = listOf(account("AAAA1111", id = 1)))
        val summary = sut(
            listOf(
                account("aaaa-1111"),
                account(" aaaa 1111 "),
                account("AAAA1111"),
            ),
        )
        assertEquals(ImportAccountsUseCase.Summary(imported = 0, skipped = 3), summary)
        assertEquals(1, repo.snapshot.size)
    }

    @Test
    fun `duplicates within the incoming batch only import once`() = runBlocking {
        val (sut, repo) = setup()
        val summary = sut(
            listOf(account("AAAA1111"), account("aaaa1111"), account("AAAA-1111")),
        )
        assertEquals(ImportAccountsUseCase.Summary(imported = 1, skipped = 2), summary)
        assertEquals(1, repo.snapshot.size)
    }

    @Test
    fun `imported account id is reset before insert`() = runBlocking {
        val (sut, repo) = setup()
        sut(listOf(account("AAAA1111", id = 999L)))
        val stored = repo.snapshot.single()
        assertTrue("id should be assigned by the repo, got ${stored.id}", stored.id in 1L..10L)
    }

    @Test
    fun `found equals imported plus skipped`() = runBlocking {
        val (sut, _) = setup(initial = listOf(account("AAAA1111", id = 1)))
        val summary = sut(listOf(account("AAAA1111"), account("BBBB2222")))
        assertEquals(2, summary.found)
    }
}
