package me.diamondforge.tokn.domain.usecase

import kotlinx.coroutines.runBlocking
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class RenameGroupUseCaseTest {

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

    private fun setup(initial: List<OtpAccount>): Pair<RenameGroupUseCase, FakeAccountRepository> {
        val repo = FakeAccountRepository(initial)
        return RenameGroupUseCase(repo) to repo
    }

    @Test
    fun `rename rewrites every matching account and returns affected count`() = runBlocking {
        val (sut, repo) = setup(
            listOf(
                account(1, listOf("Work")),
                account(2, listOf("Personal")),
                account(3, listOf("Work", "VIP")),
            ),
        )

        val count = sut("Work", "Office")

        assertEquals(2, count)
        assertEquals(listOf("Office"), repo.snapshot.first { it.id == 1L }.groups)
        assertEquals(listOf("Personal"), repo.snapshot.first { it.id == 2L }.groups)
        assertEquals(listOf("Office", "VIP"), repo.snapshot.first { it.id == 3L }.groups)
    }

    @Test
    fun `rename matches case-insensitively and applies the new casing`() = runBlocking {
        val (sut, repo) = setup(
            listOf(
                account(1, listOf("work")),
                account(2, listOf("WORK", "Personal")),
            ),
        )

        val count = sut("Work", "Office")

        assertEquals(2, count)
        assertEquals(listOf("Office"), repo.snapshot.first { it.id == 1L }.groups)
        assertEquals(listOf("Office", "Personal"), repo.snapshot.first { it.id == 2L }.groups)
    }

    @Test
    fun `rename onto existing group dedupes case-insensitively`() = runBlocking {
        val (sut, repo) = setup(
            listOf(
                account(1, listOf("Work", "Office")),
                account(2, listOf("Office", "Work")),
                account(3, listOf("Personal")),
            ),
        )

        val count = sut("Work", "Office")

        assertEquals(2, count)
        assertEquals(listOf("Office"), repo.snapshot.first { it.id == 1L }.groups)
        assertEquals(listOf("Office"), repo.snapshot.first { it.id == 2L }.groups)
        assertEquals(listOf("Personal"), repo.snapshot.first { it.id == 3L }.groups)
    }

    @Test
    fun `rename to same casing is a no-op`() = runBlocking {
        val (sut, repo) = setup(
            listOf(
                account(1, listOf("Work")),
                account(2, listOf("Work", "Personal")),
            ),
        )

        val count = sut("Work", "Work")

        assertEquals(0, count)
        assertEquals(listOf("Work"), repo.snapshot.first { it.id == 1L }.groups)
        assertEquals(listOf("Work", "Personal"), repo.snapshot.first { it.id == 2L }.groups)
    }

    @Test
    fun `rename normalizes casing on every account when only case changes`() = runBlocking {
        val (sut, repo) = setup(
            listOf(
                account(1, listOf("work")),
                account(2, listOf("WORK")),
                account(3, listOf("Work")),
            ),
        )

        val count = sut("work", "Work")

        assertEquals(2, count)
        repo.snapshot.forEach { acc -> assertEquals(listOf("Work"), acc.groups) }
    }

    @Test
    fun `rename of missing group changes nothing`() = runBlocking {
        val (sut, repo) = setup(listOf(account(1, listOf("Personal"))))

        val count = sut("Work", "Office")

        assertEquals(0, count)
        assertEquals(listOf("Personal"), repo.snapshot.first().groups)
    }

    @Test
    fun `blank target name is rejected`() = runBlocking {
        val (sut, repo) = setup(listOf(account(1, listOf("Work"))))

        assertEquals(0, sut("Work", "   "))
        assertEquals(0, sut("Work", ""))
        assertEquals(listOf("Work"), repo.snapshot.first().groups)
    }

    @Test
    fun `target name is trimmed before storing`() = runBlocking {
        val (sut, repo) = setup(listOf(account(1, listOf("Work"))))

        val count = sut("Work", "  Office  ")

        assertEquals(1, count)
        assertEquals(listOf("Office"), repo.snapshot.first().groups)
    }
}
