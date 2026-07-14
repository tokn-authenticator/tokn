package me.diamondforge.tokn.audit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuditLoggerImplTest {

    private lateinit var db: AuditDatabase
    private lateinit var dao: AuditLogDao
    private lateinit var logger: AuditLoggerImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val direct = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, AuditDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(direct)
            .setTransactionExecutor(direct)
            .build()
        dao = db.auditLogDao()
        logger = AuditLoggerImpl(dao, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
    }

    @After
    fun tearDown() = db.close()

    private fun entries() = runBlocking { dao.getAll().first() }

    @Test
    fun `log writes an entry tagged with the type's category`() {
        logger.log(AuditEventType.ITEM_ADDED, targetId = 7L, detail = "note")

        val entry = entries().single()
        assertEquals(AuditEventType.ITEM_ADDED.name, entry.type)
        assertEquals(AuditCategory.ITEMS.name, entry.category)
        assertEquals(7L, entry.targetId)
        assertEquals("note", entry.detail)
    }

    @Test
    fun `disabled logger writes nothing`() {
        logger.setEnabled(false)

        logger.log(AuditEventType.ITEM_ADDED)

        assertTrue(entries().isEmpty())
    }

    @Test
    fun `re-enabling the logger resumes writes`() {
        logger.setEnabled(false)
        logger.log(AuditEventType.ITEM_ADDED)
        logger.setEnabled(true)
        logger.log(AuditEventType.ITEM_EDITED)

        assertEquals(listOf(AuditEventType.ITEM_EDITED.name), entries().map { it.type })
    }

    @Test
    fun `a disabled category is skipped while other categories keep logging`() {
        logger.setDisabledCategories(setOf(AuditCategory.ITEMS))

        logger.log(AuditEventType.ITEM_ADDED)
        logger.log(AuditEventType.VAULT_UNLOCKED_PASSWORD)

        assertEquals(listOf(AuditEventType.VAULT_UNLOCKED_PASSWORD.name), entries().map { it.type })
    }

    @Test
    fun `clearing the disabled categories resumes logging for them`() {
        logger.setDisabledCategories(setOf(AuditCategory.ITEMS))
        logger.log(AuditEventType.ITEM_ADDED)

        logger.setDisabledCategories(emptySet())
        logger.log(AuditEventType.ITEM_EDITED)

        assertEquals(listOf(AuditEventType.ITEM_EDITED.name), entries().map { it.type })
    }
}
