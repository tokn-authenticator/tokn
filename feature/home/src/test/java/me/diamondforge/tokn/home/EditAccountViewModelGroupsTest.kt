package me.diamondforge.tokn.home

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.diamondforge.tokn.data.icon.IconPackManager
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.GetAccountByIdUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.UpdateAccountUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers groups state on the edit screen: initial value loaded from the
 * stored account, `updateGroups` is a straight passthrough, and the saved
 * domain account carries the same list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditAccountViewModelGroupsTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var repo: FakeAccountRepository
    private lateinit var iconPackManager: IconPackManager

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        repo = FakeAccountRepository()
        iconPackManager = IconPackManager(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(seed: OtpAccount): EditAccountViewModel {
        val id = runBlocking { repo.addAccount(seed) }
        return EditAccountViewModel(
            context = context,
            savedStateHandle = SavedStateHandle(mapOf("accountId" to id)),
            getAccountByIdUseCase = GetAccountByIdUseCase(repo),
            updateAccountUseCase = UpdateAccountUseCase(repo),
            iconPackManager = iconPackManager,
            getAccountsUseCase = GetAccountsUseCase(repo),
        )
    }

    private fun totpSeed(groups: List<String> = emptyList()) = OtpAccount(
        issuer = "Issuer",
        accountName = "user",
        secret = "JBSWY3DPEHPK3PXP",
        groups = groups,
    )

    @Test
    fun `groups state mirrors the stored account on load`() = runTest(dispatcher) {
        val vm = newVm(totpSeed(groups = listOf("Work", "Personal")))
        advanceUntilIdle()
        assertEquals(listOf("Work", "Personal"), vm.uiState.value.groups)
    }

    @Test
    fun `updateGroups replaces the list`() = runTest(dispatcher) {
        val vm = newVm(totpSeed(groups = listOf("Old")))
        advanceUntilIdle()

        vm.updateGroups(listOf("Work", "Critical"))
        assertEquals(listOf("Work", "Critical"), vm.uiState.value.groups)

        vm.updateGroups(emptyList())
        assertEquals(emptyList<String>(), vm.uiState.value.groups)
    }

    @Test
    fun `saveChanges persists the current groups list verbatim`() = runTest(dispatcher) {
        val vm = newVm(totpSeed())
        advanceUntilIdle()

        vm.updateGroups(listOf("Work", "Side projects"))
        var savedCalled = false
        vm.saveChanges { savedCalled = true }
        advanceUntilIdle()

        assertEquals(true, savedCalled)
        val persisted = repo.snapshot.single()
        assertEquals(listOf("Work", "Side projects"), persisted.groups)
    }
}
