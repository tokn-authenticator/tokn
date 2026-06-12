package me.diamondforge.tokn.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountUseCasesTest {

    private fun account(
        id: Long = 0,
        secret: String = "JBSWY3DPEHPK3PXP",
        sortOrder: Int = 0,
    ) = OtpAccount(
        id = id,
        issuer = "issuer$id",
        accountName = "name$id",
        secret = secret,
        sortOrder = sortOrder,
    )

    @Test
    fun `add returns the assigned id and stores the account`() = runBlocking {
        val repo = FakeAccountRepository()
        val id = AddAccountUseCase(repo)(account())

        assertEquals(1L, id)
        assertEquals(1, repo.snapshot.size)
    }

    @Test
    fun `update replaces the account with matching id`() = runBlocking {
        val repo = FakeAccountRepository(listOf(account(1)))
        UpdateAccountUseCase(repo)(account(1).copy(issuer = "renamed"))

        assertEquals("renamed", repo.snapshot.single().issuer)
    }

    @Test
    fun `delete removes a single account`() = runBlocking {
        val repo = FakeAccountRepository(listOf(account(1), account(2)))
        DeleteAccountUseCase(repo)(1)

        assertEquals(listOf(2L), repo.snapshot.map { it.id })
    }

    @Test
    fun `deleteAccounts removes the whole set`() = runBlocking {
        val repo = FakeAccountRepository(listOf(account(1), account(2), account(3)))
        DeleteAccountsUseCase(repo)(setOf(1, 3))

        assertEquals(listOf(2L), repo.snapshot.map { it.id })
    }

    @Test
    fun `deleteAccounts with empty set is a no-op`() = runBlocking {
        val repo = FakeAccountRepository(listOf(account(1), account(2)))
        DeleteAccountsUseCase(repo)(emptySet())

        assertEquals(2, repo.snapshot.size)
    }

    @Test
    fun `getAccountById returns the match or null`() = runBlocking {
        val repo = FakeAccountRepository(listOf(account(1)))

        assertEquals(1L, GetAccountByIdUseCase(repo)(1)?.id)
        assertNull(GetAccountByIdUseCase(repo)(99))
    }

    @Test
    fun `getAccounts emits the current vault`() = runBlocking {
        val repo = FakeAccountRepository(listOf(account(1), account(2)))

        assertEquals(2, GetAccountsUseCase(repo)().first().size)
    }

    @Test
    fun `incrementHotpCounter advances only the targeted account`() = runBlocking {
        val repo = FakeAccountRepository(listOf(account(1), account(2)))
        IncrementHotpCounterUseCase(repo)(1)

        assertEquals(1L, repo.snapshot.first { it.id == 1L }.counter)
        assertEquals(0L, repo.snapshot.first { it.id == 2L }.counter)
    }

    @Test
    fun `recordUsage bumps count and stamps the timestamp`() = runBlocking {
        val repo = FakeAccountRepository(listOf(account(1)))
        RecordUsageUseCase(repo)(1, timestamp = 1_700_000_000_000L)

        val acc = repo.snapshot.single()
        assertEquals(1, acc.usageCount)
        assertEquals(1_700_000_000_000L, acc.lastUsedAt)
    }

    @Test
    fun `reorder rewrites sortOrder to match the supplied order`() = runBlocking {
        val repo = FakeAccountRepository(
            listOf(account(1, sortOrder = 0), account(2, sortOrder = 1), account(3, sortOrder = 2)),
        )

        val reversed = listOf(
            repo.snapshot.first { it.id == 3L },
            repo.snapshot.first { it.id == 2L },
            repo.snapshot.first { it.id == 1L },
        )
        ReorderAccountsUseCase(repo)(reversed)

        assertEquals(0, repo.snapshot.first { it.id == 3L }.sortOrder)
        assertEquals(1, repo.snapshot.first { it.id == 2L }.sortOrder)
        assertEquals(2, repo.snapshot.first { it.id == 1L }.sortOrder)
    }

    @Test
    fun `add assigns sequential ids across multiple inserts`() = runBlocking {
        val repo = FakeAccountRepository()
        val add = AddAccountUseCase(repo)

        val first = add(account())
        val second = add(account())

        assertEquals(1L, first)
        assertEquals(2L, second)
        assertTrue(repo.snapshot.map { it.id }.containsAll(listOf(1L, 2L)))
    }
}
