package me.diamondforge.tokn.home

import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.diamondforge.tokn.data.icon.IconPackManager
import me.diamondforge.tokn.data.preferences.AppPreferencesRepository
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.DeleteAccountUseCase
import me.diamondforge.tokn.domain.usecase.DeleteAccountsUseCase
import me.diamondforge.tokn.domain.usecase.GenerateOtpUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.IncrementHotpCounterUseCase
import me.diamondforge.tokn.domain.usecase.ReorderAccountsUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the tap-to-reveal lifecycle and HOTP copy/increment semantics introduced in
 * v1.4.1 (commit 9dae80e):
 *
 *  1. The tap-to-reveal preference flows into UI state.
 *  2. `reveal()` records the displayed code so the id appears in `revealedIds`.
 *  3. Copying an HOTP code drops the reveal record AND advances the counter, so the next
 *     render never briefly leaks the freshly generated code unmasked.
 *  4. Copying a TOTP code leaves the counter alone.
 *
 * Note on time: [HomeViewModel] starts a perpetual 1-second ticker in its init block. We
 * deliberately drive the scheduler with [runCurrent] (which runs only tasks queued at the
 * current virtual instant) instead of `advanceUntilIdle()` — the latter would chase the
 * ticker's never-ending stream of `delay`s forever. Each test cancels the viewModelScope
 * at the end so runTest's own cleanup doesn't hit the same trap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeViewModelRevealTest {

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
        deleteAccountUseCase = DeleteAccountUseCase(repo),
        deleteAccountsUseCase = DeleteAccountsUseCase(repo),
        reorderAccountsUseCase = ReorderAccountsUseCase(repo),
        generateOtpUseCase = GenerateOtpUseCase(),
        incrementHotpCounterUseCase = IncrementHotpCounterUseCase(repo),
        appPreferences = appPrefs,
        userPreferences = userPrefs,
        iconPackManager = iconPackManager,
    )

    /** Collects [HomeViewModel.uiState] into a holder driven by [runCurrent]. */
    private fun TestScope.latestState(vm: HomeViewModel): () -> HomeUiState {
        var latest = vm.uiState.value
        backgroundScope.launch { vm.uiState.collect { latest = it } }
        runCurrent()
        return { latest }
    }

    @Test
    fun `tapToRevealEnabled flag flows from preferences into UI state`() = runTest(dispatcher) {
        userPrefs.tap.value = true
        val vm = newVm()
        val state = latestState(vm)
        assertTrue(state().tapToRevealEnabled)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `reveal on TOTP marks the id as revealed for the current code`() = runTest(dispatcher) {
        val acctId = runBlocking { repo.addAccount(totp("JBSWY3DPEHPK3PXP")) }
        val vm = newVm()
        val state = latestState(vm)
        val item = state().items.single { it.account.id == acctId }

        vm.reveal(item)
        runCurrent()
        assertTrue(state().revealedIds.contains(acctId))
        vm.viewModelScope.cancel()
    }

    @Test
    fun `copyToClipboard on HOTP advances counter and drops the reveal`() = runTest(dispatcher) {
        val acctId = runBlocking { repo.addAccount(hotp(counter = 0L)) }
        val vm = newVm()
        val state = latestState(vm)
        val item = state().items.single { it.account.id == acctId }

        vm.reveal(item)
        runCurrent()
        assertTrue("should be revealed before copy", state().revealedIds.contains(acctId))

        vm.copyToClipboard(item)
        runCurrent()

        // Counter advanced via the repository.
        assertEquals(1L, repo.snapshot.single { it.id == acctId }.counter)
        // Reveal record dropped so the next render doesn't flash the freshly generated code.
        assertFalse(state().revealedIds.contains(acctId))

        // Clipboard got something labelled "OTP".
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val primary = cm.primaryClip
        assertNotNull(primary)
        assertEquals("OTP", primary!!.description.label)

        vm.viewModelScope.cancel()
    }

    @Test
    fun `copyToClipboard on TOTP does not advance any counter`() = runTest(dispatcher) {
        val acctId = runBlocking { repo.addAccount(totp("JBSWY3DPEHPK3PXP")) }
        val vm = newVm()
        val state = latestState(vm)
        val item = state().items.single { it.account.id == acctId }

        vm.copyToClipboard(item)
        runCurrent()

        assertEquals(0L, repo.snapshot.single { it.id == acctId }.counter)
        vm.viewModelScope.cancel()
    }
}

private fun totp(secret: String): OtpAccount = OtpAccount(
    issuer = "Issuer",
    accountName = "user",
    secret = secret,
    type = OtpType.TOTP,
)

private fun hotp(counter: Long): OtpAccount = OtpAccount(
    issuer = "Issuer",
    accountName = "user",
    secret = "JBSWY3DPEHPK3PXP",
    type = OtpType.HOTP,
    counter = counter,
)

private class FakeAppPreferences(context: Context) : AppPreferencesRepository(context) {
    val fetch = MutableStateFlow(false)
    override val iconFetchEnabled = fetch
}

private class FakeUserPreferences(context: Context) : UserPreferencesRepository(context) {
    val tap = MutableStateFlow(false)
    override val tapToRevealEnabled = tap
}