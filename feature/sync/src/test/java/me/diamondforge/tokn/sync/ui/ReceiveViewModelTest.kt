package me.diamondforge.tokn.sync.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.ImportAccountsUseCase
import me.diamondforge.tokn.security.EncryptionManager
import me.diamondforge.tokn.sync.AppInfo
import me.diamondforge.tokn.sync.SyncProtocol
import me.diamondforge.tokn.sync.qr.QrChunkCodec
import org.json.JSONObject
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
class ReceiveViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var recvRepo: FakeAccountRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        recvRepo = FakeAccountRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun receiver() = ReceiveViewModel(
        context = context,
        encryptionManager = EncryptionManager(),
        importAccountsUseCase = ImportAccountsUseCase(recvRepo),
        appInfo = AppInfo(context),
    )

    @Test
    fun `frames are reassembled and duplicates ignored`() {
        val vm = receiver()
        val frames = QrChunkCodec.encode(ByteArray(1500) { it.toByte() })
        assertTrue(frames.size > 1)

        vm.onQrFrameScanned(frames[0])
        vm.onQrFrameScanned(frames[0])
        assertEquals(1, vm.uiState.value.qrSeen)
        assertFalse(vm.uiState.value.qrComplete)

        frames.drop(1).forEach { vm.onQrFrameScanned(it) }

        assertTrue(vm.uiState.value.qrComplete)
        assertEquals(frames.size, vm.uiState.value.qrTotal)
    }

    @Test
    fun `a newer protocol surfaces a version mismatch instead of completing`() {
        val vm = receiver()
        val wrapper = JSONObject().apply {
            put("protocol", SyncProtocol.VERSION + 1)
            put("app", "Tokn")
            put("build", 999L)
        }.toString()

        QrChunkCodec.encode(wrapper.toByteArray()).forEach { vm.onQrFrameScanned(it) }

        assertNotNull(vm.uiState.value.versionMismatch)
        assertFalse(vm.uiState.value.qrComplete)
    }

    @Test
    fun `a full send to receive QR round trip imports the vault`() = runTest(dispatcher) {
        val frames = sentFrames("correct horse")

        val vm = receiver()
        frames.forEach { vm.onQrFrameScanned(it) }
        assertTrue(vm.uiState.value.qrComplete)

        vm.decryptAndImport("correct horse")
        advanceUntilIdle()

        assertEquals(ReceiveUiState.Status.Done, vm.uiState.value.status)
        assertNotNull(vm.uiState.value.importSummary)
        assertEquals(2, recvRepo.snapshot.size)
    }

    @Test
    fun `the wrong passphrase reports a friendly error`() = runTest(dispatcher) {
        val frames = sentFrames("correct horse")

        val vm = receiver()
        frames.forEach { vm.onQrFrameScanned(it) }

        vm.decryptAndImport("battery staple")
        advanceUntilIdle()

        assertNull(vm.uiState.value.importSummary)
        assertEquals(
            context.getString(me.diamondforge.tokn.sync.R.string.sync_qr_wrong_passphrase),
            vm.uiState.value.errorMessage,
        )
        assertTrue(recvRepo.snapshot.isEmpty())
    }

    private suspend fun sentFrames(passphrase: String): List<String> {
        val sendRepo = FakeAccountRepository(
            listOf(
                OtpAccount(issuer = "ACME", accountName = "alice", secret = "JBSWY3DPEHPK3PXP"),
                OtpAccount(issuer = "Globex", accountName = "bob", secret = "KZXW6YTBOI======"),
            ),
        )
        val sendVm = SendViewModel(
            context = context,
            getAccountsUseCase = GetAccountsUseCase(sendRepo),
            encryptionManager = EncryptionManager(),
            appInfo = AppInfo(context),
            vaultAuthGate = newVaultAuthGate(context),
        )
        sendVm.prepareQr(passphrase)
        return sendVm.uiState.first { it.qrFrames.isNotEmpty() }.qrFrames
    }
}
