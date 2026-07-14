package me.diamondforge.tokn.rating

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.diamondforge.tokn.data.preferences.FakePreferencesDataStore
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.security.vault.VaultSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RatingPromptViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var prefs: FakePrefs
    private lateinit var session: VaultSession

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        prefs = FakePrefs(context)
        session = VaultSession()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(
        install: InstallInfoProvider = FakeInstall(),
        accounts: List<OtpAccount> = listOf(account()),
        unlocked: Boolean = true,
    ): RatingPromptViewModel {
        if (unlocked) session.unlock(ByteArray(32))
        val useCase = GetAccountsUseCase(FakeAccountRepository(accounts))
        return RatingPromptViewModel(
            preferences = prefs,
            installInfo = install,
            getAccounts = Lazy { useCase },
            vaultSession = session,
        )
    }

    private fun TestScope.promptValue(vm: RatingPromptViewModel): Boolean {
        var latest = false
        backgroundScope.launch { vm.shouldPrompt.collect { latest = it } }
        runCurrent()
        return latest
    }

    @Test
    fun `prompts when every condition is satisfied`() = runTest(dispatcher) {
        assertTrue(promptValue(newVm()))
    }

    @Test
    fun `does not prompt while the vault is locked`() = runTest(dispatcher) {
        assertFalse(promptValue(newVm(unlocked = false)))
    }

    @Test
    fun `does not prompt without tokens`() = runTest(dispatcher) {
        assertFalse(promptValue(newVm(accounts = emptyList())))
    }

    @Test
    fun `does not prompt when not installed from the Play Store`() = runTest(dispatcher) {
        assertFalse(promptValue(newVm(install = FakeInstall(playStore = false))))
    }

    @Test
    fun `does not prompt below the launch threshold`() = runTest(dispatcher) {
        prefs.launchCount.value = 4
        assertFalse(promptValue(newVm()))
    }

    @Test
    fun `does not prompt before five days installed`() = runTest(dispatcher) {
        val twoDays = 2L * 24 * 60 * 60 * 1000
        assertFalse(promptValue(newVm(install = FakeInstall(firstInstall = System.currentTimeMillis() - twoDays))))
    }

    @Test
    fun `does not prompt once handled`() = runTest(dispatcher) {
        prefs.handled.value = true
        assertFalse(promptValue(newVm()))
    }

    @Test
    fun `recordLaunch increments up to the threshold then stops`() = runTest(dispatcher) {
        prefs.launchCount.value = 3
        val vm = newVm()

        vm.recordLaunch()
        runCurrent()
        assertEquals(4, prefs.launchCount.value)

        prefs.launchCount.value = 5
        vm.recordLaunch()
        runCurrent()
        assertEquals(5, prefs.launchCount.value)
    }

    @Test
    fun `markHandled sets the terminal flag`() = runTest(dispatcher) {
        val vm = newVm()
        vm.markHandled()
        runCurrent()
        assertTrue(prefs.handled.value)
    }

    @Test
    fun `snooze pushes the next prompt into the future`() = runTest(dispatcher) {
        val vm = newVm()
        vm.snooze()
        runCurrent()
        assertTrue(prefs.snoozedUntil.value > System.currentTimeMillis())
    }

    private fun account() =
        OtpAccount(issuer = "Acme", accountName = "a@b.c", secret = "JBSWY3DPEHPK3PXP")

    private class FakeInstall(
        private val firstInstall: Long = System.currentTimeMillis() - 6L * 24 * 60 * 60 * 1000,
        private val playStore: Boolean = true,
    ) : InstallInfoProvider {
        override fun firstInstallTime(): Long = firstInstall
        override fun isFromPlayStore(): Boolean = playStore
    }

    private class FakePrefs(context: Context) :
        UserPreferencesRepository(FakePreferencesDataStore()) {
        val launchCount = MutableStateFlow(5)
        val handled = MutableStateFlow(false)
        val snoozedUntil = MutableStateFlow(0L)
        override val ratingPromptLaunchCount = launchCount
        override val ratingPromptHandled = handled
        override val ratingPromptSnoozedUntil = snoozedUntil
        override suspend fun setRatingPromptLaunchCount(count: Int) {
            launchCount.value = count
        }

        override suspend fun setRatingPromptHandled(handled: Boolean) {
            this.handled.value = handled
        }

        override suspend fun setRatingPromptSnoozedUntil(timestamp: Long) {
            snoozedUntil.value = timestamp
        }
    }
}
