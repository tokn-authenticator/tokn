package me.diamondforge.tokn.sync

import kotlinx.coroutines.runBlocking
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.AddAccountUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncImporterTest {

    private fun importer(initial: List<OtpAccount> = emptyList()): Pair<SyncImporter, FakeAccountRepository> {
        val repo = FakeAccountRepository(initial)
        val sut = SyncImporter(
            getAccountsUseCase = GetAccountsUseCase(repo),
            addAccountUseCase = AddAccountUseCase(repo),
        )
        return sut to repo
    }

    private fun account(secret: String, issuer: String = "i", name: String = "n", id: Long = 0) =
        OtpAccount(id = id, issuer = issuer, accountName = name, secret = secret)

    @Test
    fun `empty incoming list imports nothing`() = runBlocking {
        val (sut, _) = importer()
        val summary = sut.import(emptyList())
        assertEquals(SyncImporter.Summary(imported = 0, skipped = 0), summary)
    }

    @Test
    fun `all new accounts are imported`() = runBlocking {
        val (sut, repo) = importer()
        val summary = sut.import(
            listOf(account("AAAA1111"), account("BBBB2222"), account("CCCC3333")),
        )
        assertEquals(SyncImporter.Summary(3, 0), summary)
        assertEquals(3, repo.snapshot.size)
    }

    @Test
    fun `already-present secret is skipped`() = runBlocking {
        val (sut, repo) = importer(initial = listOf(account("AAAA1111", id = 1)))
        val summary = sut.import(listOf(account("AAAA1111"), account("BBBB2222")))
        assertEquals(SyncImporter.Summary(imported = 1, skipped = 1), summary)
        assertEquals(2, repo.snapshot.size)
    }

    @Test
    fun `secret comparison normalizes whitespace dashes and case`() = runBlocking {
        val (sut, repo) = importer(initial = listOf(account("AAAA1111", id = 1)))
        val summary = sut.import(
            listOf(
                account("aaaa-1111"),
                account(" aaaa 1111 "),
                account("AAAA1111"),
            ),
        )
        assertEquals(SyncImporter.Summary(imported = 0, skipped = 3), summary)
        assertEquals(1, repo.snapshot.size)
    }

    @Test
    fun `duplicates within the incoming batch only import once`() = runBlocking {
        val (sut, repo) = importer()
        val summary = sut.import(
            listOf(account("AAAA1111"), account("aaaa1111"), account("AAAA-1111")),
        )
        assertEquals(SyncImporter.Summary(imported = 1, skipped = 2), summary)
        assertEquals(1, repo.snapshot.size)
    }

    @Test
    fun `imported account id is reset before insert`() = runBlocking {
        val (sut, repo) = importer()
        sut.import(listOf(account("AAAA1111", id = 999L)))
        // The fake repo assigns its own id starting from 1; if id=999 had leaked
        // through we'd see it on the stored row.
        val stored = repo.snapshot.single()
        assertTrue("id should be assigned by the repo, got ${stored.id}", stored.id in 1L..10L)
    }
}
