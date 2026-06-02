package me.diamondforge.tokn.backup.qr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.ImportAccountsUseCase
import me.diamondforge.tokn.importer.otpauth.OtpAuthMigrationImporter
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
class MigrationScanViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeAccountRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeAccountRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = MigrationScanViewModel(
        importer = OtpAuthMigrationImporter(),
        importAccountsUseCase = ImportAccountsUseCase(repo),
        lockManager = LockManager(),
    )

    private fun entry(secret: String, name: String, issuer: String = "Issuer") =
        MigrationEntry(secret = secret.toByteArray(), name = name, issuer = issuer)

    @Test
    fun `scanning all batches of one vault reaches completion`() = runTest(dispatcher) {
        val vm = newVm()
        val batchId = 77
        vm.onScanned(
            MigrationFixture.buildUri(
                listOf(entry("AAAA", "a")),
                batchIndex = 0,
                batchSize = 3,
                batchId = batchId
            )
        )
        vm.onScanned(
            MigrationFixture.buildUri(
                listOf(entry("BBBB", "b")),
                batchIndex = 1,
                batchSize = 3,
                batchId = batchId
            )
        )
        vm.onScanned(
            MigrationFixture.buildUri(
                listOf(entry("CCCC", "c")),
                batchIndex = 2,
                batchSize = 3,
                batchId = batchId
            )
        )

        val state = vm.uiState.value
        assertEquals(3, state.scanned)
        assertEquals(3, state.expectedTotal)
        assertTrue(state.isComplete)
        assertFalse(state.isPartial)
    }

    @Test
    fun `isPartial is true while batches are missing`() = runTest(dispatcher) {
        val vm = newVm()
        vm.onScanned(
            MigrationFixture.buildUri(
                listOf(entry("AAAA", "a")),
                batchIndex = 0,
                batchSize = 2,
                batchId = 1
            )
        )
        val state = vm.uiState.value
        assertEquals(1, state.scanned)
        assertEquals(2, state.expectedTotal)
        assertTrue(state.isPartial)
        assertFalse(state.isComplete)
    }

    @Test
    fun `duplicate batch index does not advance the count`() = runTest(dispatcher) {
        val vm = newVm()
        val uri = MigrationFixture.buildUri(
            listOf(entry("AAAA", "a")),
            batchIndex = 0,
            batchSize = 2,
            batchId = 1
        )
        vm.onScanned(uri)
        val firstDup = vm.uiState.value.justDuplicate
        vm.onScanned(uri)
        assertEquals(1, vm.uiState.value.scanned)
        assertTrue("duplicate bump should have changed", vm.uiState.value.justDuplicate != firstDup)
    }

    @Test
    fun `scanning a different vault surfaces a cross-vault prompt without losing progress`() =
        runTest(dispatcher) {
            val vm = newVm()
            val first = MigrationFixture.buildUri(
                listOf(entry("AAAA", "a")),
                batchIndex = 0,
                batchSize = 2,
                batchId = 100
            )
            val other = MigrationFixture.buildUri(
                listOf(entry("BBBB", "b")),
                batchIndex = 0,
                batchSize = 2,
                batchId = 200
            )
            vm.onScanned(first)
            vm.onScanned(other)

            assertNotNull(vm.uiState.value.crossVaultPending)
            assertEquals(1, vm.uiState.value.scanned)  // unchanged
        }

    @Test
    fun `confirmCrossVault replaces the session with the new vault`() = runTest(dispatcher) {
        val vm = newVm()
        vm.onScanned(
            MigrationFixture.buildUri(
                listOf(entry("AAAA", "a")),
                batchIndex = 0,
                batchSize = 2,
                batchId = 100
            )
        )
        vm.onScanned(
            MigrationFixture.buildUri(
                listOf(entry("BBBB", "b")),
                batchIndex = 0,
                batchSize = 3,
                batchId = 200
            )
        )
        vm.confirmCrossVault()

        val state = vm.uiState.value
        assertNull(state.crossVaultPending)
        assertEquals(1, state.scanned)
        assertEquals(3, state.expectedTotal)  // now tracking the new vault
    }

    @Test
    fun `dismissCrossVault clears the prompt and keeps original progress`() = runTest(dispatcher) {
        val vm = newVm()
        vm.onScanned(
            MigrationFixture.buildUri(
                listOf(entry("AAAA", "a")),
                batchIndex = 0,
                batchSize = 2,
                batchId = 100
            )
        )
        vm.onScanned(
            MigrationFixture.buildUri(
                listOf(entry("BBBB", "b")),
                batchIndex = 0,
                batchSize = 2,
                batchId = 200
            )
        )
        vm.dismissCrossVault()

        assertNull(vm.uiState.value.crossVaultPending)
        assertEquals(1, vm.uiState.value.scanned)
        // The original vault is still active: a second batch of id 100 is accepted.
        vm.onScanned(
            MigrationFixture.buildUri(
                listOf(entry("AAAA2", "a2")),
                batchIndex = 1,
                batchSize = 2,
                batchId = 100
            )
        )
        assertEquals(2, vm.uiState.value.scanned)
    }

    @Test
    fun `non-migration scan triggers an invalid bump`() = runTest(dispatcher) {
        val vm = newVm()
        val before = vm.uiState.value.invalidScan
        vm.onScanned("otpauth://totp/X?secret=AAAA")
        assertTrue(vm.uiState.value.invalidScan != before)
        assertEquals(0, vm.uiState.value.scanned)
    }

    @Test
    fun `commit imports scanned accounts and deduplicates against existing secrets`() =
        runTest(dispatcher) {
            // Pre-seed an account whose secret matches one we will scan; it should be skipped.
            val sharedSecret = "AAAA"
            runBlocking {
                // The importer base32-encodes raw secret bytes, so seed the repo with that form.
                repo.addAccount(existing(base32Of(sharedSecret)))
            }
            val vm = newVm()
            vm.onScanned(
                MigrationFixture.buildUri(
                    listOf(entry(sharedSecret, "dup"), entry("BBBB", "fresh")),
                    batchIndex = 0,
                    batchSize = 1,
                    batchId = 1,
                ),
            )
            vm.commit()
            advanceUntilIdle()

            val result = vm.uiState.first { it.result != null }.result!!
            assertEquals(2, result.found)
            assertEquals(1, result.imported)  // the duplicate is skipped
            // Repo now holds the pre-seeded one plus the single fresh import.
            assertEquals(2, repo.snapshot.size)
        }

    @Test
    fun `commit on an empty session is a no-op`() = runTest(dispatcher) {
        val vm = newVm()
        vm.commit()
        advanceUntilIdle()
        assertNull(vm.uiState.value.result)
        assertTrue(repo.snapshot.isEmpty())
    }

    private fun existing(secret: String) = OtpAccount(
        issuer = "Existing",
        accountName = "u",
        secret = secret,
    )

    // The importer encodes the raw secret bytes with Base32; reproduce that so the
    // dedup comparison (which normalizes case/spacing) lines up.
    private fun base32Of(raw: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val bytes = raw.toByteArray()
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1f
                bitsLeft -= 5
                sb.append(alphabet[index])
            }
        }
        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1f
            sb.append(alphabet[index])
        }
        return sb.toString()
    }
}
