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
        repo = AccountRepositoryImpl(db.otpAccountDao(), db)
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
}
