package me.diamondforge.tokn.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.diamondforge.tokn.data.preferences.ThemeMode
import me.diamondforge.tokn.domain.model.TapBehavior
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
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var prefs: FakeUserPreferences
    private lateinit var appPrefs: FakeAppPreferences
    private lateinit var vault: FakeVaultManager

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        prefs = FakeUserPreferences(context)
        appPrefs = FakeAppPreferences(context)
        vault = FakeVaultManager(context)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm() = SettingsViewModel(prefs, appPrefs, vault)

    private fun TestScope.state(vm: SettingsViewModel): () -> SettingsUiState {
        var latest = vm.uiState.value
        backgroundScope.launch { vm.uiState.collect { latest = it } }
        runCurrent()
        return { latest }
    }

    @Test
    fun `uiState mirrors the preference flows`() = runTest(dispatcher) {
        prefs.theme.value = ThemeMode.DARK
        prefs.autoLock.value = 30
        prefs.tapBehaviorState.value = TapBehavior.DOUBLE
        appPrefs.iconFetch.value = true
        val current = state(newVm())

        assertEquals(ThemeMode.DARK, current().themeMode)
        assertEquals(30, current().autoLockTimeoutSeconds)
        assertEquals(TapBehavior.DOUBLE, current().tapBehavior)
        assertTrue(current().iconFetchEnabled)
    }

    @Test
    fun `uiState reports days until the next password reminder`() = runTest(dispatcher) {
        prefs.reminderEnabled.value = true
        prefs.reminderLastShownAt.value = System.currentTimeMillis()
        prefs.reminderStage.value = 0
        val current = state(newVm())

        // Stage 0 is a 2-day interval, just shown, so two whole days remain.
        assertEquals(2, current().passwordReminderNextDays)
    }

    @Test
    fun `uiState reports zero days once the reminder is overdue`() = runTest(dispatcher) {
        prefs.reminderEnabled.value = true
        prefs.reminderLastShownAt.value = System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000L
        prefs.reminderStage.value = 0
        val current = state(newVm())

        assertEquals(0, current().passwordReminderNextDays)
    }

    @Test
    fun `simple setters write through to preferences`() = runTest(dispatcher) {
        val vm = newVm()
        state(vm)

        vm.setThemeMode(ThemeMode.LIGHT)
        vm.setScreenshotsEnabled(true)
        vm.setShowNextCodeEnabled(true)
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, prefs.theme.value)
        assertTrue(prefs.screenshots.value)
        assertTrue(prefs.showNext.value)
    }

    @Test
    fun `setupEncryption sets a password and seeds the reminder`() = runTest(dispatcher) {
        prefs.encryption.value = false
        val vm = newVm()

        vm.setupEncryption("hunter2")
        prefs.reminderLastShownAt.first { it > 0L }

        assertTrue(vault.passwordSet.value)
        assertTrue(prefs.encryption.value)
        assertEquals(0, prefs.reminderStage.value)
    }

    @Test
    fun `disableEncryption with the correct password clears password and biometric`() = runTest(dispatcher) {
        prefs.encryption.value = true
        prefs.biometric.value = true
        vault.verifyResult = true
        val vm = newVm()

        vm.disableEncryption("hunter2")
        prefs.biometric.first { !it }

        assertTrue(vault.passwordRemoved.value)
        assertFalse(prefs.encryption.value)
    }

    @Test
    fun `disableEncryption with a wrong password raises the error and changes nothing`() = runTest(dispatcher) {
        prefs.encryption.value = true
        vault.verifyResult = false
        val vm = newVm()

        vm.disableEncryption("wrong")
        vm.uiState.first { it.passwordVerificationFailed }

        assertFalse(vault.passwordRemoved.value)
        assertTrue(prefs.encryption.value)

        vm.clearPasswordError()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.passwordVerificationFailed)
    }

    @Test
    fun `disabling biometric flips the pref and tears down the keystore slot`() = runTest(dispatcher) {
        prefs.biometric.value = true
        val vm = newVm()

        vm.setBiometricEnabled(false)
        advanceUntilIdle()
        vault.biometricDisabled.first { it }

        assertFalse(prefs.biometric.value)
        assertTrue(vault.biometricDisabled.value)
    }

    @Test
    fun `enabling biometric only flips the pref`() = runTest(dispatcher) {
        prefs.biometric.value = false
        val vm = newVm()

        vm.setBiometricEnabled(true)
        advanceUntilIdle()

        assertFalse(vault.biometricDisabled.value)
        assertTrue(prefs.biometric.value)
    }
}
