package me.diamondforge.tokn.onboarding

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.AddAccountUseCase
import me.diamondforge.tokn.importer.ImportOutcome
import me.diamondforge.tokn.importer.ImporterRegistry
import me.diamondforge.tokn.security.BiometricHelper
import me.diamondforge.tokn.security.LockManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var prefs: FakeOnboardingPreferences
    private lateinit var vault: FakeOnboardingVault
    private lateinit var repo: FakeAccountRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        prefs = FakeOnboardingPreferences(context)
        vault = FakeOnboardingVault(context)
        repo = FakeAccountRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(registry: ImporterRegistry = ImporterRegistry(emptySet())) = OnboardingViewModel(
        context = context,
        preferences = prefs,
        vaultManager = vault,
        addAccountUseCase = AddAccountUseCase(repo),
        lockManager = LockManager(),
        importerRegistry = registry,
        biometricHelper = BiometricHelper(context),
    )

    private fun registryOf(vararg importers: FakeImporter) = ImporterRegistry(importers.toSet())

    private fun uriFor(bytes: ByteArray): Uri {
        val uri = Uri.parse("content://tokn.test/backup")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        return uri
    }

    @Test
    fun `finish with no encryption disables the vault and marks onboarding done`() = runTest(dispatcher) {
        val vm = newVm()
        vm.setCryptType(CryptType.NONE)

        val done = MutableStateFlow(false)
        vm.finish { done.value = true }
        done.first { it }

        assertFalse(vault.passwordSet.value)
        assertTrue(vault.biometricDisabled.value)
        assertEquals(false, prefs.encryption.value)
        assertEquals(false, prefs.biometric.value)
        assertTrue(prefs.onboardingDoneFlag.value)
    }

    @Test
    fun `finish with a password enables encryption and seeds the reminder`() = runTest(dispatcher) {
        val vm = newVm()
        vm.setCryptType(CryptType.PASSWORD)
        vm.setPassword("hunter2")

        val done = MutableStateFlow(false)
        vm.finish { done.value = true }
        done.first { it }

        assertTrue(vault.passwordSet.value)
        assertTrue(vault.biometricDisabled.value)
        assertEquals(true, prefs.encryption.value)
        assertEquals(false, prefs.biometric.value)
        assertEquals(0, prefs.reminderStage.value)
        assertTrue(prefs.reminderLastShownAt.value > 0L)
    }

    @Test
    fun `finish with biometric keeps the keystore slot and enables both`() = runTest(dispatcher) {
        val vm = newVm()
        vm.setCryptType(CryptType.BIOMETRIC)
        vm.setPassword("hunter2")

        val done = MutableStateFlow(false)
        vm.finish { done.value = true }
        done.first { it }

        assertTrue(vault.passwordSet.value)
        assertFalse(vault.biometricDisabled.value)
        assertEquals(true, prefs.encryption.value)
        assertEquals(true, prefs.biometric.value)
    }

    @Test
    fun `finish without a chosen method does nothing`() = runTest(dispatcher) {
        val vm = newVm()
        var completed = false
        vm.finish { completed = true }
        advanceUntilIdle()

        assertFalse(completed)
        assertFalse(prefs.onboardingDoneFlag.value)
    }

    @Test
    fun `importing a tokn vault adds the accounts`() = runTest(dispatcher) {
        val accounts = listOf(
            OtpAccount(issuer = "ACME", accountName = "alice", secret = "JBSWY3DPEHPK3PXP"),
        )
        val vm = newVm(registryOf(FakeImporter("tokn", ImportOutcome.Success(accounts))))

        vm.importBackup(uriFor("payload".toByteArray()))
        vm.uiState.first { it.importedCount != null }

        assertEquals(1, vm.uiState.value.importedCount)
        assertEquals(1, repo.snapshot.size)
    }

    @Test
    fun `an encrypted tokn vault asks for a password`() = runTest(dispatcher) {
        val vm = newVm(registryOf(FakeImporter("tokn", ImportOutcome.NeedsPassword)))

        vm.importBackup(uriFor("payload".toByteArray()))
        vm.uiState.first { it.pendingTokn != null }

        assertNotNull(vm.uiState.value.pendingTokn)
        assertEquals(null, vm.uiState.value.importError)
    }

    @Test
    fun `a wrong password keeps the pending import and flags the error`() = runTest(dispatcher) {
        val vm = newVm(registryOf(FakeImporter("tokn", ImportOutcome.WrongPassword())))

        vm.importBackup(uriFor("payload".toByteArray()), password = "nope")
        vm.uiState.first { it.importError == ImportError.WrongPassword }

        assertNotNull(vm.uiState.value.pendingTokn)
    }

    @Test
    fun `a foreign export is redirected to settings`() = runTest(dispatcher) {
        val accounts = listOf(OtpAccount(issuer = "x", accountName = "y", secret = "JBSWY3DPEHPK3PXP"))
        val vm = newVm(registryOf(FakeImporter("aegis", ImportOutcome.Success(accounts))))

        vm.importBackup(uriFor("payload".toByteArray()))
        vm.uiState.first { it.importError == ImportError.Redirect }

        assertTrue(repo.snapshot.isEmpty())
    }

    @Test
    fun `an unrecognized file is reported as invalid`() = runTest(dispatcher) {
        val vm = newVm(registryOf(FakeImporter("tokn", ImportOutcome.Unsupported, handles = false)))

        vm.importBackup(uriFor("payload".toByteArray()))
        vm.uiState.first { it.importError == ImportError.Invalid }

        assertTrue(repo.snapshot.isEmpty())
    }
}
