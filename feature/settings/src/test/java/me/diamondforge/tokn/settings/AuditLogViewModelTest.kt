package me.diamondforge.tokn.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.concurrent.Executor
import me.diamondforge.tokn.audit.AuditCategory
import me.diamondforge.tokn.audit.AuditDatabase
import me.diamondforge.tokn.audit.AuditEventType
import me.diamondforge.tokn.audit.AuditLogDao
import me.diamondforge.tokn.audit.AuditLogEntry
import me.diamondforge.tokn.audit.AuditLogger
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.GetAccountsByIdsUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuditLogViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: AuditDatabase
    private lateinit var dao: AuditLogDao
    private lateinit var prefs: FakeUserPreferences
    private lateinit var accounts: FakeAccountRepository
    private lateinit var auditLogger: FakeAuditLogger

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        val direct = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, AuditDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(direct)
            .setTransactionExecutor(direct)
            .build()
        dao = db.auditLogDao()
        prefs = FakeUserPreferences(context)
        accounts = FakeAccountRepository()
        auditLogger = FakeAuditLogger()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun newVm() = AuditLogViewModel(
        dao,
        prefs,
        GetAccountsByIdsUseCase(accounts),
        auditLogger,
    )

    private suspend fun insert(
        type: AuditEventType,
        targetId: Long? = null,
        detail: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        dao.insert(
            AuditLogEntry(
                type = type.name,
                category = type.category.name,
                timestamp = timestamp,
                targetId = targetId,
                detail = detail,
            ),
        )
    }

    private fun TestScope.rows(vm: AuditLogViewModel): () -> AuditLogUiState {
        var latest = vm.uiState.value
        backgroundScope.launch { vm.uiState.collect { latest = it } }
        runCurrent()
        return { latest }
    }

    private fun TestScope.settings(vm: AuditLogViewModel): () -> AuditLogSettingsState {
        var latest = vm.settingsState.value
        backgroundScope.launch { vm.settingsState.collect { latest = it } }
        runCurrent()
        return { latest }
    }

    @Test
    fun `rows resolve the issuer of a still-existing account`() = runTest(dispatcher) {
        val id = accounts.addAccount(OtpAccount(issuer = "GitHub", accountName = "me", secret = "JBSWY3DPEHPK3PXP"))
        insert(AuditEventType.ITEM_ADDED, targetId = id)
        val current = rows(newVm())
        advanceUntilIdle()

        assertEquals("GitHub", current().items.single().targetName)
    }

    @Test
    fun `rows fall back to a null target name for a purged account`() = runTest(dispatcher) {
        insert(AuditEventType.ITEM_ADDED, targetId = 999L)
        val current = rows(newVm())
        advanceUntilIdle()

        assertNull(current().items.single().targetName)
    }

    @Test
    fun `category filter narrows the visible rows`() = runTest(dispatcher) {
        insert(AuditEventType.VAULT_UNLOCKED_PASSWORD)
        insert(AuditEventType.ITEM_ADDED)
        val vm = newVm()
        val current = rows(vm)
        advanceUntilIdle()

        vm.toggleCategory(AuditCategory.ITEMS)
        advanceUntilIdle()

        assertEquals(listOf(AuditEventType.ITEM_ADDED), current().items.map { it.type })
    }

    @Test
    fun `search matches against the detail text`() = runTest(dispatcher) {
        insert(AuditEventType.GROUP_RENAMED, detail = "Work -> Office")
        insert(AuditEventType.VAULT_UNLOCKED_PASSWORD)
        val vm = newVm()
        val current = rows(vm)
        advanceUntilIdle()

        vm.setSearchQuery("office")
        advanceUntilIdle()

        assertEquals(listOf(AuditEventType.GROUP_RENAMED), current().items.map { it.type })
    }

    @Test
    fun `date range filter excludes rows outside the window`() = runTest(dispatcher) {
        val now = System.currentTimeMillis()
        insert(AuditEventType.ITEM_ADDED, timestamp = now - 10 * DAY_MS)
        insert(AuditEventType.ITEM_EDITED, timestamp = now)
        val vm = newVm()
        val current = rows(vm)
        advanceUntilIdle()

        vm.setDateRange((now - DAY_MS)..(now + DAY_MS))
        advanceUntilIdle()

        assertEquals(listOf(AuditEventType.ITEM_EDITED), current().items.map { it.type })
    }

    @Test
    fun `clearFilters resets category search and date range`() = runTest(dispatcher) {
        val vm = newVm()
        rows(vm)
        advanceUntilIdle()

        vm.toggleCategory(AuditCategory.ITEMS)
        vm.setSearchQuery("x")
        vm.setDateRange(0L..1L)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasActiveFilters)

        vm.clearFilters()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hasActiveFilters)
    }

    @Test
    fun `clearLog empties the dao`() = runTest(dispatcher) {
        insert(AuditEventType.ITEM_ADDED)
        val vm = newVm()
        val current = rows(vm)
        advanceUntilIdle()
        assertEquals(1, current().items.size)

        vm.clearLog()
        advanceUntilIdle()

        assertTrue(current().items.isEmpty())
    }

    @Test
    fun `init prunes entries older than the retention window`() = runTest(dispatcher) {
        prefs.setAuditRetentionDays(30)
        insert(AuditEventType.ITEM_ADDED, timestamp = System.currentTimeMillis() - 40L * DAY_MS)
        val current = rows(newVm())
        advanceUntilIdle()

        assertTrue(current().items.isEmpty())
    }

    @Test
    fun `setAuditLoggingEnabled writes through and logs the toggle`() = runTest(dispatcher) {
        val vm = newVm()
        val current = settings(vm)
        advanceUntilIdle()

        vm.setAuditLoggingEnabled(false)
        advanceUntilIdle()

        assertFalse(current().enabled)
        assertEquals(AuditEventType.AUDIT_LOG_DISABLED, auditLogger.logged.last().type)
    }

    @Test
    fun `setAuditRetentionDays writes through and logs the new value`() = runTest(dispatcher) {
        val vm = newVm()
        val current = settings(vm)
        advanceUntilIdle()

        vm.setAuditRetentionDays(30)
        advanceUntilIdle()

        assertEquals(30, current().retentionDays)
        assertEquals(AuditEventType.AUDIT_LOG_RETENTION_CHANGED to "30", auditLogger.logged.last().let { it.type to it.detail })
    }

    @Test
    fun `setCategoryLoggingEnabled toggles the category both ways`() = runTest(dispatcher) {
        val vm = newVm()
        val current = settings(vm)
        advanceUntilIdle()

        vm.setCategoryLoggingEnabled(AuditCategory.ITEMS, false)
        advanceUntilIdle()
        assertTrue(AuditCategory.ITEMS in current().disabledCategories)
        assertEquals(
            AuditEventType.AUDIT_LOG_CATEGORY_DISABLED to "ITEMS",
            auditLogger.logged.last().let { it.type to it.detail },
        )

        vm.setCategoryLoggingEnabled(AuditCategory.ITEMS, true)
        advanceUntilIdle()
        assertFalse(AuditCategory.ITEMS in current().disabledCategories)
        assertEquals(
            AuditEventType.AUDIT_LOG_CATEGORY_ENABLED to "ITEMS",
            auditLogger.logged.last().let { it.type to it.detail },
        )
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

private class FakeAuditLogger : AuditLogger {
    data class LoggedEvent(val type: AuditEventType, val targetId: Long?, val detail: String?)

    val logged = mutableListOf<LoggedEvent>()

    override fun log(type: AuditEventType, targetId: Long?, detail: String?) {
        logged += LoggedEvent(type, targetId, detail)
    }

    override fun setEnabled(enabled: Boolean) = Unit
    override fun setDisabledCategories(categories: Set<AuditCategory>) = Unit
}
