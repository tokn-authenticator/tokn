package me.diamondforge.tokn.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.diamondforge.tokn.data.db.AppDatabase
import me.diamondforge.tokn.data.db.entity.OtpAccountEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OtpAccountDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: OtpAccountDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.otpAccountDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entity(
        id: Long = 0,
        sortOrder: Int = 0,
        counter: Long = 0,
    ) = OtpAccountEntity(
        id = id,
        issuer = "issuer",
        accountName = "name",
        secret = "JBSWY3DPEHPK3PXP",
        algorithm = "SHA1",
        digits = 6,
        period = 30,
        counter = counter,
        type = "TOTP",
        sortOrder = sortOrder,
    )

    @Test
    fun `insert returns the row id and the row can be fetched`() = runBlocking {
        val id = dao.insert(entity())
        assertEquals(id, dao.getAccountById(id)?.id)
    }

    @Test
    fun `getAllAccounts is ordered by sortOrder`() = runBlocking {
        dao.insert(entity(sortOrder = 2))
        dao.insert(entity(sortOrder = 0))
        dao.insert(entity(sortOrder = 1))

        assertEquals(listOf(0, 1, 2), dao.getAllAccounts().first().map { it.sortOrder })
    }

    @Test
    fun `deleteById removes only the target`() = runBlocking {
        val a = dao.insert(entity())
        val b = dao.insert(entity())
        dao.deleteById(a)

        assertNull(dao.getAccountById(a))
        assertEquals(b, dao.getAccountById(b)?.id)
    }

    @Test
    fun `deleteByIds removes the whole set`() = runBlocking {
        val a = dao.insert(entity())
        val b = dao.insert(entity())
        val c = dao.insert(entity())
        dao.deleteByIds(setOf(a, c))

        assertEquals(listOf(b), dao.getAllAccountsOnce().map { it.id })
    }

    @Test
    fun `incrementCounter advances the counter`() = runBlocking {
        val id = dao.insert(entity(counter = 4))
        dao.incrementCounter(id)
        assertEquals(5L, dao.getAccountById(id)?.counter)
    }

    @Test
    fun `recordUsage bumps the count and stores the timestamp`() = runBlocking {
        val id = dao.insert(entity())
        dao.recordUsage(id, 1_700_000_000_000L)

        val row = dao.getAccountById(id)!!
        assertEquals(1, row.usageCount)
        assertEquals(1_700_000_000_000L, row.lastUsedAt)
    }

    @Test
    fun `updateSortOrder rewrites a single row`() = runBlocking {
        val id = dao.insert(entity(sortOrder = 0))
        dao.updateSortOrder(id, 9)
        assertEquals(9, dao.getAccountById(id)?.sortOrder)
    }

    @Test
    fun `insert replaces on id conflict`() = runBlocking {
        val id = dao.insert(entity())
        dao.insert(entity(id = id).copy(issuer = "replaced"))

        assertEquals(1, dao.getAllAccountsOnce().size)
        assertEquals("replaced", dao.getAccountById(id)?.issuer)
    }

    @Test
    fun `softDeleteByIds hides rows from active queries and shows them in trash`() = runBlocking {
        val a = dao.insert(entity())
        val b = dao.insert(entity())
        dao.softDeleteByIds(setOf(a), 1_000L)

        assertEquals(listOf(b), dao.getAllAccountsOnce().map { it.id })
        assertNull(dao.getAccountById(a))
        assertEquals(listOf(a), dao.getTrashedAccounts().first().map { it.id })
        assertEquals(1_000L, dao.getTrashedAccounts().first().first().deletedAt)
    }

    @Test
    fun `restoreByIds brings a trashed row back`() = runBlocking {
        val a = dao.insert(entity())
        dao.softDeleteByIds(setOf(a), 1_000L)
        dao.restoreByIds(setOf(a))

        assertEquals(listOf(a), dao.getAllAccountsOnce().map { it.id })
        assertEquals(emptyList<Long>(), dao.getTrashedAccounts().first().map { it.id })
    }

    @Test
    fun `purgeExpired removes only rows trashed before the cutoff`() = runBlocking {
        val old = dao.insert(entity())
        val fresh = dao.insert(entity())
        dao.softDeleteByIds(setOf(old), 1_000L)
        dao.softDeleteByIds(setOf(fresh), 5_000L)

        val removed = dao.purgeExpired(3_000L)

        assertEquals(1, removed)
        assertEquals(listOf(fresh), dao.getTrashedAccounts().first().map { it.id })
    }

    @Test
    fun `purgeExpired leaves active rows alone`() = runBlocking {
        val active = dao.insert(entity())
        dao.purgeExpired(Long.MAX_VALUE)

        assertEquals(listOf(active), dao.getAllAccountsOnce().map { it.id })
    }
}
