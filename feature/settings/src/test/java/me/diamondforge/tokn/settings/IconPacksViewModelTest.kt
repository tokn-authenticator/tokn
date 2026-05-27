package me.diamondforge.tokn.settings

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.diamondforge.tokn.data.icon.IconPackManager
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.UpdateAccountUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IconPacksViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var repo: FakeAccountRepository
    private lateinit var manager: IconPackManager
    private lateinit var workDir: File

    private val packUuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "icon-packs").deleteRecursively()
        repo = FakeAccountRepository()
        manager = IconPackManager(context)
        workDir = File(context.cacheDir, "settings-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        workDir.deleteRecursively()
    }

    private fun newVm() = IconPacksViewModel(
        iconPackManager = manager,
        getAccountsUseCase = GetAccountsUseCase(repo),
        updateAccountUseCase = UpdateAccountUseCase(repo),
    )

    @Test
    fun `import proposes auto-match only for accounts without a custom icon and a matching issuer`() =
        runTest(dispatcher) {
            runBlocking {
                repo.addAccount(account(issuer = "GitHub"))                                  // should match
                repo.addAccount(
                    account(
                        issuer = "GitHub",
                        custom = byteArrayOf(1)
                    )
                )         // excluded: has custom icon
                repo.addAccount(account(issuer = "Unmatched Co"))                            // excluded: no suggestion
            }
            val vm = newVm()

            vm.importPack(packZipUri("github.svg" to listOf("GitHub")))
            advanceUntilIdle()

            val proposal = vm.uiState.first { it.autoMatch != null }.autoMatch
            assertNotNull(proposal)
            assertEquals(packUuid, proposal!!.packUuid)
            assertEquals(1, proposal.assignments.size)
            assertEquals("GitHub", proposal.assignments.single().first.issuer)
            assertEquals("github.svg", proposal.assignments.single().second)
        }

    @Test
    fun `applyAutoMatch writes pack assignment and clears any custom icon`() = runTest(dispatcher) {
        val id = runBlocking { repo.addAccount(account(issuer = "GitHub")) }
        val vm = newVm()
        vm.importPack(packZipUri("github.svg" to listOf("GitHub")))
        advanceUntilIdle()
        vm.uiState.first { it.autoMatch != null }

        vm.applyAutoMatch()
        // The assignment is written on Dispatchers.IO, so await the repository flow rather
        // than reading the snapshot synchronously (advanceUntilIdle only drives the test
        // dispatcher, not the real IO pool).
        val stored = repo.getAccounts().first { list -> list.any { it.iconPackId == packUuid } }
            .single { it.id == id }
        assertEquals(packUuid, stored.iconPackId)
        assertEquals("github.svg", stored.iconPackFile)
        assertNull(stored.customIconBytes)
        // autoMatch is cleared synchronously before the IO write is dispatched.
        assertNull(vm.uiState.first { it.autoMatch == null }.autoMatch)
    }

    @Test
    fun `invalid pack json sets import error`() = runTest(dispatcher) {
        val vm = newVm()
        val badZip = makeZip("bad.zip", "pack.json" to "garbage".toByteArray())
        vm.importPack(Uri.fromFile(badZip))
        advanceUntilIdle()
        val state = vm.uiState.first { it.importError != null }
        assertNotNull(state.importError)
        assertTrue(!state.isImporting)
    }

    @Test
    fun `missing pack json sets import error`() = runTest(dispatcher) {
        val vm = newVm()
        val zip = makeZip("nopack.zip", "icon.svg" to ByteArray(0))
        vm.importPack(Uri.fromFile(zip))
        advanceUntilIdle()
        assertEquals("pack.json missing", vm.uiState.first { it.importError != null }.importError)
    }

    @Test
    fun `clearImportError resets the error`() = runTest(dispatcher) {
        val vm = newVm()
        vm.importPack(Uri.fromFile(makeZip("bad.zip", "pack.json" to "x".toByteArray())))
        advanceUntilIdle()
        vm.uiState.first { it.importError != null }
        vm.clearImportError()
        assertNull(vm.uiState.first { it.importError == null }.importError)
    }

    @Test
    fun `dismissAutoMatch drops the pending proposal without applying`() = runTest(dispatcher) {
        val id = runBlocking { repo.addAccount(account(issuer = "GitHub")) }
        val vm = newVm()
        vm.importPack(packZipUri("github.svg" to listOf("GitHub")))
        advanceUntilIdle()
        vm.uiState.first { it.autoMatch != null }

        vm.dismissAutoMatch()
        assertNull(vm.uiState.first { it.autoMatch == null }.autoMatch)
        // No assignment was written.
        assertNull(repo.snapshot.single { it.id == id }.iconPackId)
    }

    private fun account(issuer: String, custom: ByteArray? = null) = OtpAccount(
        issuer = issuer,
        accountName = "user@$issuer",
        secret = "JBSWY3DPEHPK3PXP",
        customIconBytes = custom,
    )

    private fun packZipUri(vararg icons: Pair<String, List<String>>): Uri {
        val iconsJson = icons.joinToString(",") { (filename, issuers) ->
            val issuerArr = issuers.joinToString(",") { "\"$it\"" }
            """{"filename":"$filename","name":"${filename.substringBeforeLast('.')}","issuer":[$issuerArr]}"""
        }
        val packJson = """{"uuid":"$packUuid","name":"Brands","icons":[$iconsJson]}"""
        val entries = mutableListOf<Pair<String, ByteArray>>("pack.json" to packJson.toByteArray())
        icons.forEach { (filename, _) -> entries += filename to "<svg/>".toByteArray() }
        return Uri.fromFile(makeZip("pack.zip", *entries.toTypedArray()))
    }

    private fun makeZip(name: String, vararg entries: Pair<String, ByteArray>): File {
        val out = File(workDir, name)
        ZipOutputStream(out.outputStream()).use { zos ->
            for ((path, bytes) in entries) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return out
    }
}
