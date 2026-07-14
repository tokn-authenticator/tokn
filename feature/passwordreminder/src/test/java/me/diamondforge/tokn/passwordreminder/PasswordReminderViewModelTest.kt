package me.diamondforge.tokn.passwordreminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import me.diamondforge.tokn.security.KeystoreManager
import me.diamondforge.tokn.security.VaultPasswordManager
import me.diamondforge.tokn.security.vault.VaultManager
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
class PasswordReminderViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var prefs: FakePrefs
    private lateinit var vault: FakeVault

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        prefs = FakePrefs(context)
        vault = FakeVault(context)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm() = PasswordReminderViewModel(prefs, vault)

    private fun TestScope.promptValue(vm: PasswordReminderViewModel): Boolean {
        var latest = false
        backgroundScope.launch { vm.shouldPrompt.collect { latest = it } }
        runCurrent()
        return latest
    }

    @Test
    fun `prompts when enabled and overdue`() = runTest(dispatcher) {
        prefs.enabled.value = true
        prefs.lastShownAt.value = 0L
        assertTrue(promptValue(newVm()))
    }

    @Test
    fun `never prompts while disabled`() = runTest(dispatcher) {
        prefs.enabled.value = false
        prefs.lastShownAt.value = 0L
        assertFalse(promptValue(newVm()))
    }

    @Test
    fun `does not prompt before the interval elapses`() = runTest(dispatcher) {
        prefs.enabled.value = true
        prefs.lastShownAt.value = System.currentTimeMillis()
        assertFalse(promptValue(newVm()))
    }

    @Test
    fun `recordSuccess climbs the ladder and stamps the time`() = runTest(dispatcher) {
        prefs.stage.value = 0
        val vm = newVm()

        vm.recordSuccess()
        runCurrent()

        assertEquals(1, prefs.stage.value)
        assertTrue(prefs.lastShownAt.value > 0L)
    }

    @Test
    fun `recordFailure drops two rungs`() = runTest(dispatcher) {
        prefs.stage.value = 3
        val vm = newVm()

        vm.recordFailure()
        runCurrent()

        assertEquals(1, prefs.stage.value)
    }

    @Test
    fun `snooze moves the timestamp without touching the stage`() = runTest(dispatcher) {
        prefs.stage.value = 2
        prefs.lastShownAt.value = 0L
        val vm = newVm()

        vm.snooze()
        runCurrent()

        assertEquals(2, prefs.stage.value)
        assertTrue(prefs.lastShownAt.value > 0L)
    }

    @Test
    fun `markSeen stamps the time and leaves the stage alone`() = runTest(dispatcher) {
        prefs.stage.value = 1
        val vm = newVm()

        vm.markSeen()
        runCurrent()

        assertEquals(1, prefs.stage.value)
        assertTrue(prefs.lastShownAt.value > 0L)
    }

    @Test
    fun `verify delegates to the vault`() = runTest(dispatcher) {
        val vm = newVm()
        assertTrue(vm.verify("pw"))
        assertFalse(vm.verify("nope"))
    }

    private class FakePrefs(context: Context) :
        UserPreferencesRepository(FakePreferencesDataStore()) {
        val enabled = MutableStateFlow(true)
        val lastShownAt = MutableStateFlow(0L)
        val stage = MutableStateFlow(0)
        override val passwordReminderEnabled = enabled
        override val passwordReminderLastShownAt = lastShownAt
        override val passwordReminderStage = stage
        override suspend fun setPasswordReminderLastShownAt(timestamp: Long) {
            lastShownAt.value = timestamp
        }

        override suspend fun setPasswordReminderStage(stage: Int) {
            this.stage.value = stage
        }
    }

    private class FakeVault(context: Context) : VaultManager(
        context,
        KeystoreManager(context),
        VaultSession(),
        VaultPasswordManager(KeystoreManager(context), context),
    ) {
        override fun verifyPassword(password: String): Boolean = password == "pw"
    }
}
