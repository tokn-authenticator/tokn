package me.diamondforge.tokn.add

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.diamondforge.tokn.audit.NoopAuditLogger
import me.diamondforge.tokn.data.icon.IconPackManager
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.AddAccountUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.security.LockManager
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
class AddAccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var repo: FakeAccountRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        repo = FakeAccountRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm() = AddAccountViewModel(
        context = context,
        addAccountUseCase = AddAccountUseCase(repo),
        lockManager = LockManager(NoopAuditLogger),
        iconPackManager = IconPackManager(context),
        getAccountsUseCase = GetAccountsUseCase(repo),
    )

    @Test
    fun `a blank secret is rejected before saving`() = runTest(dispatcher) {
        val vm = newVm()
        vm.updateAccountName("alice")

        vm.saveAccount { fail() }
        advanceUntilIdle()

        assertEquals(context.getString(R.string.add_error_secret_required), vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSaving)
        assertTrue(repo.snapshot.isEmpty())
    }

    @Test
    fun `a non base32 secret is rejected`() = runTest(dispatcher) {
        val vm = newVm()
        vm.updateSecret("10101")
        vm.updateAccountName("alice")

        vm.saveAccount { fail() }
        advanceUntilIdle()

        assertEquals(context.getString(R.string.add_error_invalid_secret), vm.uiState.value.error)
        assertTrue(repo.snapshot.isEmpty())
    }

    @Test
    fun `a blank account name is rejected`() = runTest(dispatcher) {
        val vm = newVm()
        vm.updateSecret("JBSWY3DPEHPK3PXP")

        vm.saveAccount { fail() }
        advanceUntilIdle()

        assertEquals(
            context.getString(R.string.add_error_account_name_required),
            vm.uiState.value.error
        )
    }

    @Test
    fun `a valid entry is persisted with a normalized secret`() = runTest(dispatcher) {
        val vm = newVm()
        vm.updateIssuer("  ACME  ")
        vm.updateAccountName("  alice  ")
        vm.updateSecret("jbswy3 dpehpk3pxp")

        var saved = false
        vm.saveAccount { saved = true }
        advanceUntilIdle()

        assertTrue(saved)
        val account = repo.snapshot.single()
        assertEquals("ACME", account.issuer)
        assertEquals("alice", account.accountName)
        assertEquals("JBSWY3DPEHPK3PXP", account.secret)
    }

    @Test
    fun `a valid qr payload becomes a parsed account`() = runTest(dispatcher) {
        val vm = newVm()
        vm.onQrScanned("otpauth://totp/ACME:alice?secret=JBSWY3DPEHPK3PXP&issuer=ACME")

        val parsed = vm.uiState.value.parsedAccount
        assertNotNull(parsed)
        assertEquals("alice", parsed!!.accountName)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `an unparseable qr payload sets an error`() = runTest(dispatcher) {
        val vm = newVm()
        vm.onQrScanned("not a uri")

        assertEquals(context.getString(R.string.add_invalid_qr_code), vm.uiState.value.error)
        assertNull(vm.uiState.value.parsedAccount)
    }

    @Test
    fun `applying a parsed account fills the form and clears the pending parse`() =
        runTest(dispatcher) {
            val vm = newVm()
            vm.onQrScanned("otpauth://totp/ACME:alice?secret=JBSWY3DPEHPK3PXP&issuer=ACME&digits=8")
            vm.applyParsedAccount()

            val state = vm.uiState.value
            assertEquals("alice", state.accountName)
            assertEquals("JBSWY3DPEHPK3PXP", state.secret)
            assertEquals(8, state.digits)
            assertEquals(OtpType.TOTP, state.type)
            assertNull(state.parsedAccount)
        }

    @Test
    fun `field setters update the form state`() = runTest(dispatcher) {
        val vm = newVm()
        vm.updateIssuer("Acme")
        vm.updateDigits(8)
        vm.updatePeriod(60)
        vm.updateGroups(listOf("Work"))

        val state = vm.uiState.value
        assertEquals("Acme", state.issuer)
        assertEquals(8, state.digits)
        assertEquals(60, state.period)
        assertEquals(listOf("Work"), state.groups)
    }

    @Test
    fun `clearError removes a pending error`() = runTest(dispatcher) {
        val vm = newVm()
        vm.onQrScanned("not a uri")
        assertNotNull(vm.uiState.value.error)

        vm.clearError()
        assertNull(vm.uiState.value.error)
    }

    private fun fail(): Nothing = throw AssertionError("onSuccess should not be called")
}
