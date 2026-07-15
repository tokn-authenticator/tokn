package me.diamondforge.tokn.home

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.diamondforge.tokn.data.icon.IconPackManager
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.AddAccountsToGroupsUseCase
import me.diamondforge.tokn.domain.usecase.DeleteAccountUseCase
import me.diamondforge.tokn.domain.usecase.DeleteAccountsUseCase
import me.diamondforge.tokn.domain.usecase.GenerateOtpUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.IncrementHotpCounterUseCase
import me.diamondforge.tokn.domain.usecase.ListGroupsUseCase
import me.diamondforge.tokn.domain.usecase.PurgeAccountsUseCase
import me.diamondforge.tokn.domain.usecase.PurgeExpiredTrashUseCase
import me.diamondforge.tokn.domain.usecase.RecordUsageUseCase
import me.diamondforge.tokn.domain.usecase.ReorderAccountsUseCase
import me.diamondforge.tokn.domain.usecase.RestoreAccountsUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Multi-select group filter on the home screen:
 *
 *  1. With no chips selected, every account is visible.
 *  2. Toggling a chip OR-filters: an account stays visible if any of its
 *     groups matches any selected chip.
 *  3. clearGroupFilter wipes the selection.
 *  4. Deleting the last account of a group prunes that group from the
 *     active selection so the filter doesn't permanently hide rows.
 *
 * Driven the same way as [HomeViewModelRevealTest] (runCurrent + manual
 * scope cancel) to avoid chasing the perpetual 1-second ticker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeViewModelGroupFilterTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var repo: FakeAccountRepository
    private lateinit var iconPackManager: IconPackManager
    private lateinit var appPrefs: FakeAppPreferences
    private lateinit var userPrefs: FakeUserPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        repo = FakeAccountRepository()
        iconPackManager = IconPackManager(context)
        appPrefs = FakeAppPreferences(context)
        userPrefs = FakeUserPreferences(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(): HomeViewModel = HomeViewModel(
        context = context,
        getAccountsUseCase = GetAccountsUseCase(repo),
        listGroupsUseCase = ListGroupsUseCase(repo),
        addAccountsToGroupsUseCase = AddAccountsToGroupsUseCase(repo),
        deleteAccountUseCase = DeleteAccountUseCase(repo),
        deleteAccountsUseCase = DeleteAccountsUseCase(repo),
        restoreAccountsUseCase = RestoreAccountsUseCase(repo),
        purgeAccountsUseCase = PurgeAccountsUseCase(repo),
        purgeExpiredTrashUseCase = PurgeExpiredTrashUseCase(repo),
        reorderAccountsUseCase = ReorderAccountsUseCase(repo),
        generateOtpUseCase = GenerateOtpUseCase(),
        incrementHotpCounterUseCase = IncrementHotpCounterUseCase(repo),
        recordUsageUseCase = RecordUsageUseCase(repo),
        appPreferences = appPrefs,
        userPreferences = userPrefs,
        iconPackManager = iconPackManager,
    )

    private fun TestScope.latestState(vm: HomeViewModel): () -> HomeUiState {
        var latest = vm.uiState.value
        backgroundScope.launch { vm.uiState.collect { latest = it } }
        runCurrent()
        return { latest }
    }

    private fun totp(name: String, groups: List<String>): OtpAccount = OtpAccount(
        issuer = name,
        accountName = "$name@example",
        secret = "JBSWY3DPEHPK3PXP",
        groups = groups,
    )

    @Test
    fun `no selection shows every account`() = runTest(dispatcher) {
        runBlocking {
            repo.addAccount(totp("A", listOf("Work")))
            repo.addAccount(totp("B", listOf("Personal")))
            repo.addAccount(totp("C", emptyList()))
        }
        val vm = newVm()
        val state = latestState(vm)

        assertEquals(3, state().items.size)
        assertTrue(state().selectedGroups.isEmpty())
        vm.viewModelScope.cancel()
    }

    @Test
    fun `toggling one chip filters to its members only`() = runTest(dispatcher) {
        runBlocking {
            repo.addAccount(totp("A", listOf("Work")))
            repo.addAccount(totp("B", listOf("Personal")))
            repo.addAccount(totp("C", emptyList()))
        }
        val vm = newVm()
        val state = latestState(vm)

        vm.toggleGroupFilter("Work")
        runCurrent()
        assertEquals(listOf("A"), state().items.map { it.account.issuer })
        vm.viewModelScope.cancel()
    }

    @Test
    fun `multiple chips OR together`() = runTest(dispatcher) {
        runBlocking {
            repo.addAccount(totp("A", listOf("Work")))
            repo.addAccount(totp("B", listOf("Personal")))
            repo.addAccount(totp("C", emptyList()))
        }
        val vm = newVm()
        val state = latestState(vm)

        vm.toggleGroupFilter("Work")
        vm.toggleGroupFilter("Personal")
        runCurrent()
        assertEquals(setOf("A", "B"), state().items.map { it.account.issuer }.toSet())
        vm.viewModelScope.cancel()
    }

    @Test
    fun `account with multiple groups matches if any group is selected`() = runTest(dispatcher) {
        runBlocking {
            repo.addAccount(totp("Multi", listOf("Work", "Critical")))
            repo.addAccount(totp("WorkOnly", listOf("Work")))
            repo.addAccount(totp("Other", listOf("Personal")))
        }
        val vm = newVm()
        val state = latestState(vm)

        vm.toggleGroupFilter("Critical")
        runCurrent()
        assertEquals(listOf("Multi"), state().items.map { it.account.issuer })
        vm.viewModelScope.cancel()
    }

    @Test
    fun `toggling the same chip again removes it`() = runTest(dispatcher) {
        runBlocking { repo.addAccount(totp("A", listOf("Work"))) }
        val vm = newVm()
        val state = latestState(vm)

        vm.toggleGroupFilter("Work")
        runCurrent()
        assertEquals(setOf("Work"), state().selectedGroups)

        vm.toggleGroupFilter("Work")
        runCurrent()
        assertTrue(state().selectedGroups.isEmpty())
        vm.viewModelScope.cancel()
    }

    @Test
    fun `clearGroupFilter wipes the selection`() = runTest(dispatcher) {
        runBlocking {
            repo.addAccount(totp("A", listOf("Work")))
            repo.addAccount(totp("B", listOf("Personal")))
        }
        val vm = newVm()
        val state = latestState(vm)

        vm.toggleGroupFilter("Work")
        vm.toggleGroupFilter("Personal")
        runCurrent()
        assertEquals(setOf("Work", "Personal"), state().selectedGroups)

        vm.clearGroupFilter()
        runCurrent()
        assertTrue(state().selectedGroups.isEmpty())
        assertEquals(2, state().items.size)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `deleting the last account of a group prunes it from the selection`() =
        runTest(dispatcher) {
            // Without pruning, the filter would point at a group nothing
            // belongs to and silently hide every row from the user.
            val id = runBlocking { repo.addAccount(totp("A", listOf("Work"))) }
            runBlocking { repo.addAccount(totp("B", listOf("Personal"))) }
            val vm = newVm()
            val state = latestState(vm)

            vm.toggleGroupFilter("Work")
            runCurrent()
            assertEquals(setOf("Work"), state().selectedGroups)

            vm.deleteAccount(id)
            runCurrent()
            assertTrue(state().selectedGroups.isEmpty())
            assertEquals(listOf("B"), state().items.map { it.account.issuer })
            vm.viewModelScope.cancel()
        }
}
