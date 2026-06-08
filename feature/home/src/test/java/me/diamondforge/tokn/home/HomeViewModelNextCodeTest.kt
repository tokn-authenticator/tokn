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
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.DeleteAccountUseCase
import me.diamondforge.tokn.domain.usecase.DeleteAccountsUseCase
import me.diamondforge.tokn.domain.usecase.GenerateOtpUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.IncrementHotpCounterUseCase
import me.diamondforge.tokn.domain.usecase.RecordUsageUseCase
import me.diamondforge.tokn.domain.usecase.ReorderAccountsUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the "show next code" preview: the upcoming TOTP code that renders
 * under the current one when the preference is on.
 *
 *  1. Off by default, every item's [AccountItem.nextCode] is null.
 *  2. On, a TOTP item's next code is the OTP for the window immediately after
 *     the one currently shown.
 *  3. HOTP never gets a next code (no time rollover), even with the pref on.
 *
 * Time handling follows [HomeViewModelRevealTest]: the perpetual 1-second
 * ticker is driven with [runCurrent], and the scope is cancelled per test so
 * runTest's cleanup doesn't chase the ticker forever. Value assertions resolve
 * the exact counter window the view model used (allowing a +/-1 skew against
 * the test's own wall-clock read) so a period boundary landing mid-test can't
 * make this flaky.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeViewModelNextCodeTest {

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

    @Test
    fun `next code is absent when the preference is off`() = runTest(dispatcher) {
        val acctId = runBlocking { repo.addAccount(totp("JBSWY3DPEHPK3PXP")) }
        val vm = newVm()
        val state = latestState(vm)

        val item = state().items.single { it.account.id == acctId }
        assertNull(item.nextCode)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `next code is the upcoming TOTP code when the preference is on`() = runTest(dispatcher) {
        userPrefs.showNext.value = true
        val account = totp("JBSWY3DPEHPK3PXP")
        val acctId = runBlocking { repo.addAccount(account) }
        val vm = newVm()
        val state = latestState(vm)

        val item = state().items.single { it.account.id == acctId }
        assertNotNull(item.nextCode)
        assertEquals(expectedNextCode(account, item.otpResult.code), item.nextCode)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `HOTP never gets a next code`() = runTest(dispatcher) {
        userPrefs.showNext.value = true
        val acctId = runBlocking { repo.addAccount(hotp(counter = 0L)) }
        val vm = newVm()
        val state = latestState(vm)

        val item = state().items.single { it.account.id == acctId }
        assertNull(item.nextCode)
        vm.viewModelScope.cancel()
    }

    /**
     * Resolves which counter window produced [currentCode] (the window the view
     * model actually used, within +/-1 of now to absorb a boundary crossing)
     * and returns the OTP for the window right after it.
     */
    private fun expectedNextCode(account: OtpAccount, currentCode: String): String {
        val gen = GenerateOtpUseCase()
        val periodMillis = account.period * 1000L
        val base = System.currentTimeMillis() / periodMillis
        val current = listOf(base, base - 1, base + 1)
            .firstOrNull { c -> gen(account, c * periodMillis).code == currentCode }
            ?: base
        return gen(account, (current + 1) * periodMillis).code
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
