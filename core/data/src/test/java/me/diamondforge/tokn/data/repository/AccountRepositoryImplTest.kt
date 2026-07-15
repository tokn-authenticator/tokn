package me.diamondforge.tokn.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.diamondforge.tokn.data.db.AppDatabase
import me.diamondforge.tokn.domain.model.OtpAccount
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: AccountRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = AccountRepositoryImpl(db.otpAccountDao(), db.groupDao(), db)
    }

    @After
    fun tearDown() = db.close()

    private fun account(groups: List<String>) =
        OtpAccount(issuer = "i", accountName = "n", secret = "JBSWY3DPEHPK3PXP", groups = groups)

    private suspend fun groupsOf(id: Long) = repo.getAccountById(id)!!.groups

    @Test
    fun `added accounts surface through the flow`() = runBlocking {
        repo.addAccount(account(listOf("Work")))
        assertEquals(1, repo.getAccounts().first().size)
    }

    @Test
    fun `renameGroup rewrites matches case-insensitively and reports the count`() = runBlocking {
        val a = repo.addAccount(account(listOf("work")))
        val b = repo.addAccount(account(listOf("WORK", "Personal")))
        val c = repo.addAccount(account(listOf("Personal")))

        val changed = repo.renameGroup("Work", "Office")

        assertEquals(2, changed)
        assertEquals(listOf("Office"), groupsOf(a))
        assertEquals(listOf("Office", "Personal"), groupsOf(b))
        assertEquals(listOf("Personal"), groupsOf(c))
    }

    @Test
    fun `renameGroup onto an existing group dedupes`() = runBlocking {
        val a = repo.addAccount(account(listOf("Work", "Office")))
        val changed = repo.renameGroup("Work", "Office")

        assertEquals(1, changed)
        assertEquals(listOf("Office"), groupsOf(a))
    }

    @Test
    fun `renameGroup with no matches changes nothing`() = runBlocking {
        repo.addAccount(account(listOf("Personal")))
        assertEquals(0, repo.renameGroup("Work", "Office"))
    }

    @Test
    fun `removeGroup strips the group case-insensitively`() = runBlocking {
        val a = repo.addAccount(account(listOf("Work", "VIP")))
        val b = repo.addAccount(account(listOf("work")))

        val changed = repo.removeGroup("WORK")

        assertEquals(2, changed)
        assertEquals(listOf("VIP"), groupsOf(a))
        assertEquals(emptyList<String>(), groupsOf(b))
    }

    @Test
    fun `deleteAccounts soft-deletes so rows move to trash and leave the active flow`() =
        runBlocking {
            val a = repo.addAccount(account(emptyList()))
            val b = repo.addAccount(account(emptyList()))

            repo.deleteAccounts(setOf(a))

            assertEquals(listOf(b), repo.getAccounts().first().map { it.id })
            assertEquals(listOf(a), repo.getTrashedAccounts().first().map { it.id })
        }

    @Test
    fun `restoreAccounts returns a trashed account to the active flow`() = runBlocking {
        val a = repo.addAccount(account(emptyList()))
        repo.deleteAccounts(setOf(a))
        repo.restoreAccounts(setOf(a))

        assertEquals(listOf(a), repo.getAccounts().first().map { it.id })
        assertEquals(emptyList<Long>(), repo.getTrashedAccounts().first().map { it.id })
    }

    @Test
    fun `purgeAccounts permanently removes a trashed account`() = runBlocking {
        val a = repo.addAccount(account(emptyList()))
        repo.deleteAccounts(setOf(a))
        repo.purgeAccounts(setOf(a))

        assertEquals(emptyList<Long>(), repo.getTrashedAccounts().first().map { it.id })
    }

    @Test
    fun `purgeExpiredTrash removes only rows deleted before the cutoff`() = runBlocking {
        val a = repo.addAccount(account(emptyList()))
        repo.deleteAccounts(setOf(a))

        assertEquals(0, repo.purgeExpiredTrash(0L))
        assertEquals(1, repo.purgeExpiredTrash(System.currentTimeMillis() + 1_000L))
        assertEquals(emptyList<Long>(), repo.getTrashedAccounts().first().map { it.id })
    }

    @Test
    fun `reorderAccounts writes sortOrder by list index`() = runBlocking {
        val a = repo.addAccount(account(emptyList()))
        val b = repo.addAccount(account(emptyList()))
        val c = repo.addAccount(account(emptyList()))

        repo.reorderAccounts(
            listOf(repo.getAccountById(c)!!, repo.getAccountById(a)!!, repo.getAccountById(b)!!),
        )

        assertEquals(0, repo.getAccountById(c)!!.sortOrder)
        assertEquals(1, repo.getAccountById(a)!!.sortOrder)
        assertEquals(2, repo.getAccountById(b)!!.sortOrder)
    }

    @Test
    fun `listGroups materializes group names already living on accounts`() = runBlocking {
        repo.addAccount(account(listOf("Work", "Personal")))

        val groups = repo.listGroups().first().map { it.name }.toSet()

        assertEquals(setOf("Work", "Personal"), groups)
    }

    @Test
    fun `createGroup makes an empty group visible even with no accounts`() = runBlocking {
        repo.createGroup("Empty", colorArgb = 0xFF112233.toInt())

        val groups = repo.listGroups().first()

        assertEquals(1, groups.size)
        assertEquals("Empty", groups.first().name)
        assertEquals(0xFF112233.toInt(), groups.first().colorArgb)
    }

    @Test
    fun `createGroup is idempotent case-insensitively`() = runBlocking {
        val first = repo.createGroup("Work")
        val second = repo.createGroup("work")

        assertEquals(first, second)
        assertEquals(1, repo.listGroups().first().size)
    }

    @Test
    fun `addAccountsToGroups adds and materializes new group names`() = runBlocking {
        val a = repo.addAccount(account(listOf("Work")))
        val b = repo.addAccount(account(emptyList()))

        val changed = repo.addAccountsToGroups(setOf(a, b), setOf("Work", "New"))

        assertEquals(2, changed)
        assertEquals(listOf("Work", "New"), groupsOf(a))
        assertEquals(listOf("Work", "New"), groupsOf(b))
        assertEquals(setOf("Work", "New"), repo.listGroups().first().map { it.name }.toSet())
    }

    @Test
    fun `renameGroup onto a name that already has its own declared group merges the rows`() =
        runBlocking {
            val a = repo.addAccount(account(listOf("Work")))
            repo.createGroup("Office", colorArgb = 0xFF445566.toInt())

            val changed = repo.renameGroup("Work", "Office")

            assertEquals(1, changed)
            assertEquals(listOf("Office"), groupsOf(a))
            val groups = repo.listGroups().first()
            assertEquals(1, groups.size)
            assertEquals(0xFF445566.toInt(), groups.first().colorArgb)
        }

    @Test
    fun `removeGroup also removes the declared group row`() = runBlocking {
        val a = repo.addAccount(account(listOf("Work")))
        repo.removeGroup("Work")

        assertEquals(emptyList<String>(), groupsOf(a))
        assertEquals(emptyList<String>(), repo.listGroups().first().map { it.name })
    }

    @Test
    fun `reorderGroups persists the given order by name`() = runBlocking {
        repo.createGroup("B")
        repo.createGroup("A")

        repo.reorderGroups(listOf("A", "B"))

        assertEquals(listOf("A", "B"), repo.listGroups().first().map { it.name })
    }
}
