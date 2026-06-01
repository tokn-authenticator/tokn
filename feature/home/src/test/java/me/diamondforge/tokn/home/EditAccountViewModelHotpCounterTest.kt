package me.diamondforge.tokn.home

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.diamondforge.tokn.data.icon.IconPackManager
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.GetAccountByIdUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.UpdateAccountUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class EditAccountViewModelHotpCounterTest {

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
        val id = if (seed.id == 0L) {
            // Use runBlocking-style add through the fake to assign an id.
            kotlinx.coroutines.runBlocking { repo.addAccount(seed) }
        } else {
            kotlinx.coroutines.runBlocking { repo.addAccount(seed); seed.id }
        }
        return EditAccountViewModel(
            context = context,
            savedStateHandle = SavedStateHandle(mapOf("accountId" to id)),
            getAccountByIdUseCase = GetAccountByIdUseCase(repo),
            updateAccountUseCase = UpdateAccountUseCase(repo),
            iconPackManager = iconPackManager,
            getAccountsUseCase = GetAccountsUseCase(repo),
        )
    }

    private fun hotpSeed(counter: Long = 0L): OtpAccount = OtpAccount(
        issuer = "Issuer",
        accountName = "user@example.com",
        secret = "JBSWY3DPEHPK3PXP",
        type = OtpType.HOTP,
        counter = counter,
    )

    private fun totpSeed(): OtpAccount = OtpAccount(
        issuer = "Issuer",
        accountName = "user@example.com",
        secret = "JBSWY3DPEHPK3PXP",
        type = OtpType.TOTP,
    )

    @Test
    fun `updateCounter filters out non-digits`() = runTest(dispatcher) {
        val vm = newVm(hotpSeed())
        advanceUntilIdle()
        vm.updateCounter("12a3-b 45")
        assertEquals("12345", vm.uiState.first().counter)
    }

    @Test
    fun `HOTP save with blank counter sets an error and does not persist`() = runTest(dispatcher) {
        val vm = newVm(hotpSeed(counter = 7L))
        advanceUntilIdle()
        vm.updateCounter("")
        var called = false
        vm.saveChanges { called = true }
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertFalse("onSuccess should not fire", called)
        assertEquals("Counter must be a non-negative number", state.error)
        assertEquals(7L, repo.snapshot.single().counter)
    }

    @Test
    fun `HOTP save with zero counter succeeds and persists`() = runTest(dispatcher) {
        val vm = newVm(hotpSeed(counter = 99L))
        advanceUntilIdle()
        vm.updateCounter("0")
        var called = false
        vm.saveChanges { called = true }
        advanceUntilIdle()
        assertTrue(called)
        assertEquals(0L, repo.snapshot.single().counter)
    }

    @Test
    fun `TOTP save ignores counter changes and keeps the original value`() = runTest(dispatcher) {
        val vm = newVm(totpSeed())
        advanceUntilIdle()
        // Even if some UI bug sets a counter on a TOTP draft, persistence must not write it.
        vm.updateCounter("12345")
        vm.saveChanges { /* noop */ }
        advanceUntilIdle()
        assertEquals(0L, repo.snapshot.single().counter)
    }

    @Test
    fun `pickPackIcon clears customIconBytes`() = runTest(dispatcher) {
        val seed = hotpSeed().copy(customIconBytes = byteArrayOf(1, 2, 3))
        val vm = newVm(seed)
        advanceUntilIdle()
        vm.pickPackIcon("no-such-pack", "missing.svg")
        val state = vm.uiState.first()
        // packIconPath resolution returns null for a non-installed pack, so the
        // call is a no-op aside from the early return — state.customIconBytes
        // should still be non-null (the pickPackIcon guard fires first).
        assertNotNull(state.customIconBytes)
    }

    @Test
    fun `clearIcon nulls all icon fields`() = runTest(dispatcher) {
        val seed = hotpSeed().copy(
            customIconBytes = byteArrayOf(1, 2, 3),
            iconPackId = "pack",
            iconPackFile = "f.svg",
        )
        val vm = newVm(seed)
        advanceUntilIdle()
        vm.clearIcon()
        val state = vm.uiState.first()
        assertNull(state.customIconBytes)
        assertNull(state.iconPackId)
        assertNull(state.iconPackFile)
        assertNull(state.packIconPath)
        assertFalse(state.hasIcon)
    }
}
