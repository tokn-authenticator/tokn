package me.diamondforge.tokn.domain.usecase

import kotlinx.coroutines.runBlocking
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteGroupUseCaseTest {

    private fun account(
        id: Long,
        groups: List<String>,
        secret: String = "AAAA1111",
    ): OtpAccount = OtpAccount(
        id = id,
        issuer = "issuer$id",
        accountName = "name$id",
        secret = secret,
        groups = groups,
    )

    private fun setup(initial: List<OtpAccount>): Pair<DeleteGroupUseCase, FakeAccountRepository> {
        val repo = FakeAccountRepository(initial)
        return DeleteGroupUseCase(repo) to repo
    }

    @Test
    fun `delete strips the group from every account and returns affected count`() = runBlocking {
        val (sut, repo) = setup(
            listOf(
                account(1, listOf("Work")),
                account(2, listOf("Work", "Personal")),
                account(3, listOf("Personal")),
            ),
        )

        val count = sut("Work")

        assertEquals(2, count)
        assertTrue(repo.snapshot.first { it.id == 1L }.groups.isEmpty())
        assertEquals(listOf("Personal"), repo.snapshot.first { it.id == 2L }.groups)
        assertEquals(listOf("Personal"), repo.snapshot.first { it.id == 3L }.groups)
    }

    @Test
    fun `delete matches case-insensitively`() = runBlocking {
        val (sut, repo) = setup(
            listOf(
                account(1, listOf("work")),
                account(2, listOf("WORK", "Personal")),
            ),
        )

        val count = sut("Work")

        assertEquals(2, count)
        assertTrue(repo.snapshot.first { it.id == 1L }.groups.isEmpty())
        assertEquals(listOf("Personal"), repo.snapshot.first { it.id == 2L }.groups)
    }

    @Test
    fun `delete of missing group changes nothing`() = runBlocking {
        val (sut, repo) = setup(listOf(account(1, listOf("Personal"))))

        val count = sut("Work")

        assertEquals(0, count)
        assertEquals(listOf("Personal"), repo.snapshot.first().groups)
    }

    @Test
    fun `blank name is rejected`() = runBlocking {
        val (sut, repo) = setup(listOf(account(1, listOf("Work"))))

        assertEquals(0, sut(""))
        assertEquals(0, sut("   "))
        assertEquals(listOf("Work"), repo.snapshot.first().groups)
    }

    @Test
    fun `accounts the group never touched are left untouched`() = runBlocking {
        val (sut, repo) = setup(
            listOf(
                account(1, listOf("Work")),
                account(2, emptyList()),
            ),
        )

        val count = sut("Work")

        assertEquals(1, count)
        assertTrue(repo.snapshot.first { it.id == 1L }.groups.isEmpty())
        assertTrue(repo.snapshot.first { it.id == 2L }.groups.isEmpty())
    }
}
