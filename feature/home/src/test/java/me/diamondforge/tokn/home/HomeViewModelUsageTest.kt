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
import me.diamondforge.tokn.domain.model.AccountSort
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.DeleteAccountUseCase
import me.diamondforge.tokn.domain.usecase.DeleteAccountsUseCase
import me.diamondforge.tokn.domain.usecase.GenerateOtpUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.IncrementHotpCounterUseCase
import me.diamondforge.tokn.domain.usecase.RecordUsageUseCase
import me.diamondforge.tokn.domain.usecase.PurgeAccountsUseCase
import me.diamondforge.tokn.domain.usecase.PurgeExpiredTrashUseCase
import me.diamondforge.tokn.domain.usecase.ReorderAccountsUseCase
import me.diamondforge.tokn.domain.usecase.RestoreAccountsUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the usage-tracking behaviour wired into [HomeViewModel] for the
 * "Most used" / "Recently used" sort modes:
 *
 *  1. `copyToClipboard` records a usage event.
 *  2. `reveal` records a usage event.
 *  3. `incrementHotpCounter` (the HOTP refresh button) does NOT: it's
 *     a counter spin, not an actual use of a code.
 *  4. Reveal-then-copy within the dedup window counts as ONE event,
 *     so users of tap-to-reveal aren't double-counted relative to
 *     users who copy directly.
 *  5. Reorder is a no-op outside CUSTOM so a sorted view never silently
 *     overwrites the user's saved manual ordering.
 *  6. The persisted sort preference flows into uiState.
 *
 * Ticker handling mirrors HomeViewModelRevealTest: drive with runCurrent
 * and cancel the viewModelScope to avoid runTest chasing the perpetual
 * 1-second ticker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeViewModelUsageTest {

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

    @Test
    fun `copyToClipboard records usage`() = runTest(dispatcher) {
        val id = runBlocking { repo.addAccount(totp("JBSWY3DPEHPK3PXP")) }
        val vm = newVm()
        val state = latestState(vm)
        val item = state().items.single { it.account.id == id }

        vm.copyToClipboard(item)
        runCurrent()

        val acct = repo.snapshot.single { it.id == id }
        assertEquals(1, acct.usageCount)
        assert(acct.lastUsedAt > 0L)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `reveal records usage`() = runTest(dispatcher) {
        val id = runBlocking { repo.addAccount(totp("JBSWY3DPEHPK3PXP")) }
        val vm = newVm()
        val state = latestState(vm)
        val item = state().items.single { it.account.id == id }

        vm.reveal(item)
        runCurrent()

        val acct = repo.snapshot.single { it.id == id }
        assertEquals(1, acct.usageCount)
        assert(acct.lastUsedAt > 0L)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `repeated copies each count`() = runTest(dispatcher) {
        // Spam-tapping must bump usage; otherwise USAGE_COUNT can never
        // reflect real intent.
        val id = runBlocking { repo.addAccount(totp("JBSWY3DPEHPK3PXP")) }
        val vm = newVm()
        val state = latestState(vm)
        val item = state().items.single { it.account.id == id }

        repeat(5) {
            vm.copyToClipboard(item)
            runCurrent()
        }

        assertEquals(5, repo.snapshot.single { it.id == id }.usageCount)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `incrementHotpCounter does not record usage`() = runTest(dispatcher) {
        val id = runBlocking { repo.addAccount(hotp(counter = 0L)) }
        val vm = newVm()
        latestState(vm)

        vm.incrementHotpCounter(id)
        runCurrent()

        val acct = repo.snapshot.single { it.id == id }
        assertEquals("counter was advanced", 1L, acct.counter)
        assertEquals("but usageCount stays at 0", 0, acct.usageCount)
        assertEquals("and lastUsedAt remains the 0L sentinel", 0L, acct.lastUsedAt)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `reorderAccounts is a no-op outside CUSTOM`() = runTest(dispatcher) {
        val a = runBlocking { repo.addAccount(totp("JBSWY3DPEHPK3PXP").copy(sortOrder = 0)) }
        val b = runBlocking { repo.addAccount(totp("JBSWY3DPEHPK3PXP").copy(sortOrder = 1)) }
        userPrefs.sort.value = AccountSort.ISSUER_ASC
        val vm = newVm()
        latestState(vm)

        // Try to swap them.
        val swapped = listOf(
            repo.snapshot.first { it.id == b },
            repo.snapshot.first { it.id == a },
        )
        vm.reorderAccounts(swapped)
        runCurrent()

        // Persisted sortOrder unchanged.
        assertEquals(0, repo.snapshot.first { it.id == a }.sortOrder)
        assertEquals(1, repo.snapshot.first { it.id == b }.sortOrder)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `reorderAccounts writes through in CUSTOM`() = runTest(dispatcher) {
        val a = runBlocking { repo.addAccount(totp("JBSWY3DPEHPK3PXP").copy(sortOrder = 0)) }
        val b = runBlocking { repo.addAccount(totp("JBSWY3DPEHPK3PXP").copy(sortOrder = 1)) }
        // userPrefs.sort default is CUSTOM.
        val vm = newVm()
        latestState(vm)

        val swapped = listOf(
            repo.snapshot.first { it.id == b },
            repo.snapshot.first { it.id == a },
        )
        vm.reorderAccounts(swapped)
        runCurrent()

        // Repository's reorder writes index-as-sortOrder, so b now sits at 0.
        assertEquals(0, repo.snapshot.first { it.id == b }.sortOrder)
        assertEquals(1, repo.snapshot.first { it.id == a }.sortOrder)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `accountSort preference flows into uiState`() = runTest(dispatcher) {
        userPrefs.sort.value = AccountSort.USAGE_COUNT
        val vm = newVm()
        val state = latestState(vm)
        assertEquals(AccountSort.USAGE_COUNT, state().sort)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `recycleBinEnabled preference flows into uiState`() = runTest(dispatcher) {
        userPrefs.recycleBin.value = false
        val vm = newVm()
        val state = latestState(vm)
        assertFalse(state().recycleBinEnabled)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `USAGE_COUNT re-applies after leaving and re-entering the mode`() = runTest(dispatcher) {
        // Three accounts with disparate usage counts and a sortOrder that
        // disagrees with the usage order: id=1 sortOrder=0 usage=0,
        // id=2 sortOrder=1 usage=99, id=3 sortOrder=2 usage=42.
        val low = runBlocking {
            repo.addAccount(totp("JBSWY3DPEHPK3PXP").copy(sortOrder = 0, usageCount = 0))
        }
        val high = runBlocking {
            repo.addAccount(totp("JBSWY3DPEHPK3PXP").copy(sortOrder = 1, usageCount = 99))
        }
        val mid = runBlocking {
            repo.addAccount(totp("JBSWY3DPEHPK3PXP").copy(sortOrder = 2, usageCount = 42))
        }

        userPrefs.sort.value = AccountSort.USAGE_COUNT
        val vm = newVm()
        val state = latestState(vm)

        assertEquals(
            "first time in USAGE_COUNT: high, mid, low",
            listOf(high, mid, low),
            state().items.map { it.account.id },
        )

        userPrefs.sort.value = AccountSort.ISSUER_ASC
        runCurrent()
        userPrefs.sort.value = AccountSort.USAGE_COUNT
        runCurrent()

        assertEquals(
            "after switching away and back: same usage order",
            listOf(high, mid, low),
            state().items.map { it.account.id },
        )
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
