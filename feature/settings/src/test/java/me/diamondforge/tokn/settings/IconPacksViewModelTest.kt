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
import me.diamondforge.tokn.data.icon.IconPackRegistry
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.UpdateAccountUseCase
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
    private lateinit var prefs: FakeAppPreferences
    private lateinit var workDir: File

    private val packUuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    private val otherUuid = "bbbbbbbb-cccc-dddd-eeee-ffffffffffff"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "icon-packs").deleteRecursively()
        repo = FakeAccountRepository()
        manager = IconPackManager(context)
        prefs = FakeAppPreferences(context)
        workDir = File(context.cacheDir, "settings-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        workDir.deleteRecursively()
    }

    private fun registry() = IconPackRegistry(manager, prefs)

    private fun newVm() = IconPacksViewModel(
        iconPackManager = manager,
        iconPackRegistry = registry(),
        getAccountsUseCase = GetAccountsUseCase(repo),
        updateAccountUseCase = UpdateAccountUseCase(repo),
    )

    @Test
    fun `import proposes auto-match only for accounts without a custom icon and a matching issuer`() =
        runTest(dispatcher) {
            runBlocking {
                repo.addAccount(account(issuer = "GitHub"))                       // should match
                repo.addAccount(account(issuer = "GitHub", custom = byteArrayOf(1))) // has custom icon
                repo.addAccount(account(issuer = "Unmatched Co"))                 // no suggestion
            }
            val vm = newVm()

            vm.importPacks(listOf(packZip(packUuid, "Brands", "github.svg" to listOf("GitHub"))))
            advanceUntilIdle()

            val proposal = vm.uiState.first { it.autoMatch != null }.autoMatch!!
            assertEquals(listOf("Brands"), proposal.packNames)
            assertEquals(1, proposal.fresh.size)
            assertTrue(proposal.replacements.isEmpty())
            assertEquals("GitHub", proposal.fresh.single().account.issuer)
            assertEquals(packUuid, proposal.fresh.single().packUuid)
            assertEquals("github.svg", proposal.fresh.single().filename)
        }

    @Test
    fun `auto-match never proposes an inverse match`() = runTest(dispatcher) {
        runBlocking { repo.addAccount(account(issuer = "GitHub Enterprise")) }
        val vm = newVm()

        vm.importPacks(listOf(packZip(packUuid, "Brands", "git.svg" to listOf("Git"))))
        advanceUntilIdle()
        vm.uiState.first { it.importSummary != null }

        assertNull(vm.uiState.value.autoMatch)
    }

    @Test
    fun `an account already using another pack lands in replacements, not fresh`() =
        runTest(dispatcher) {
            installPack(otherUuid, "Old", "old.svg" to listOf("GitHub"))
            runBlocking {
                repo.addAccount(account(issuer = "GitHub"))
                repo.addAccount(
                    account(issuer = "GitHub").copy(iconPackId = otherUuid, iconPackFile = "old.svg")
                )
            }
            val vm = newVm()

            vm.importPacks(listOf(packZip(packUuid, "New", "new.svg" to listOf("GitHub"))))
            advanceUntilIdle()

            val proposal = vm.uiState.first { it.autoMatch != null }.autoMatch!!
            assertEquals(1, proposal.fresh.size)
            assertEquals(1, proposal.replacements.size)
            assertEquals(otherUuid, proposal.replacements.single().account.iconPackId)
        }

    @Test
    fun `an account whose pack is gone counts as fresh`() = runTest(dispatcher) {
        runBlocking {
            repo.addAccount(
                account(issuer = "GitHub").copy(iconPackId = otherUuid, iconPackFile = "old.svg")
            )
        }
        val vm = newVm()

        vm.importPacks(listOf(packZip(packUuid, "New", "new.svg" to listOf("GitHub"))))
        advanceUntilIdle()

        val proposal = vm.uiState.first { it.autoMatch != null }.autoMatch!!
        assertEquals(1, proposal.fresh.size)
        assertTrue(proposal.replacements.isEmpty())
    }

    @Test
    fun `applyAutoMatch without replacements leaves an existing assignment untouched`() =
        runTest(dispatcher) {
            installPack(otherUuid, "Old", "old.svg" to listOf("GitHub"))
            val freshId = runBlocking { repo.addAccount(account(issuer = "GitHub")) }
            val keptId = runBlocking {
                repo.addAccount(
                    account(issuer = "GitHub").copy(iconPackId = otherUuid, iconPackFile = "old.svg")
                )
            }
            val vm = newVm()
            vm.importPacks(listOf(packZip(packUuid, "New", "new.svg" to listOf("GitHub"))))
            advanceUntilIdle()
            vm.uiState.first { it.autoMatch != null }

            vm.applyAutoMatch(includeReplacements = false)

            val stored = repo.getAccounts()
                .first { list -> list.any { it.id == freshId && it.iconPackId == packUuid } }
            assertEquals("new.svg", stored.single { it.id == freshId }.iconPackFile)
            assertEquals(otherUuid, stored.single { it.id == keptId }.iconPackId)
            assertEquals("old.svg", stored.single { it.id == keptId }.iconPackFile)
        }

    @Test
    fun `applyAutoMatch with replacements rewrites the existing assignment`() =
        runTest(dispatcher) {
            installPack(otherUuid, "Old", "old.svg" to listOf("GitHub"))
            val keptId = runBlocking {
                repo.addAccount(
                    account(issuer = "GitHub").copy(iconPackId = otherUuid, iconPackFile = "old.svg")
                )
            }
            val vm = newVm()
            vm.importPacks(listOf(packZip(packUuid, "New", "new.svg" to listOf("GitHub"))))
            advanceUntilIdle()
            vm.uiState.first { it.autoMatch != null }

            vm.applyAutoMatch(includeReplacements = true)

            val stored = repo.getAccounts()
                .first { list -> list.any { it.id == keptId && it.iconPackId == packUuid } }
            assertEquals("new.svg", stored.single { it.id == keptId }.iconPackFile)
        }

    @Test
    fun `applyAutoMatch never touches a gallery icon`() = runTest(dispatcher) {
        val customId = runBlocking {
            repo.addAccount(account(issuer = "GitHub", custom = byteArrayOf(7)))
        }
        runBlocking { repo.addAccount(account(issuer = "GitHub")) }
        val vm = newVm()
        vm.importPacks(listOf(packZip(packUuid, "Brands", "github.svg" to listOf("GitHub"))))
        advanceUntilIdle()
        vm.uiState.first { it.autoMatch != null }

        vm.applyAutoMatch(includeReplacements = true)

        val stored = repo.getAccounts().first { list -> list.any { it.iconPackId == packUuid } }
        val custom = stored.single { it.id == customId }
        assertNotNull(custom.customIconBytes)
        assertNull(custom.iconPackId)
    }

    @Test
    fun `dismissAutoMatch drops the pending proposal without applying`() = runTest(dispatcher) {
        val id = runBlocking { repo.addAccount(account(issuer = "GitHub")) }
        val vm = newVm()
        vm.importPacks(listOf(packZip(packUuid, "Brands", "github.svg" to listOf("GitHub"))))
        advanceUntilIdle()
        vm.uiState.first { it.autoMatch != null }

        vm.dismissAutoMatch()
        assertNull(vm.uiState.first { it.autoMatch == null }.autoMatch)
        assertNull(repo.snapshot.single { it.id == id }.iconPackId)
    }

    @Test
    fun `importPacks installs every uri and proposes across all of them`() = runTest(dispatcher) {
        runBlocking {
            repo.addAccount(account(issuer = "GitHub"))
            repo.addAccount(account(issuer = "Proton"))
        }
        val vm = newVm()

        vm.importPacks(
            listOf(
                packZip(packUuid, "Brands", "github.svg" to listOf("GitHub")),
                packZip(otherUuid, "More", "proton.svg" to listOf("Proton")),
            )
        )
        advanceUntilIdle()

        val state = vm.uiState.first { it.importSummary != null }
        assertEquals(2, state.importSummary!!.imported)
        assertTrue(state.importSummary!!.failures.isEmpty())
        assertEquals(2, state.rows.size)

        val proposal = state.autoMatch!!
        assertEquals(2, proposal.fresh.size)
        assertEquals(
            setOf(packUuid, otherUuid),
            proposal.fresh.mapTo(mutableSetOf()) { it.packUuid },
        )
    }

    @Test
    fun `importPacks reports per-file failures without aborting the batch`() = runTest(dispatcher) {
        val vm = newVm()

        vm.importPacks(
            listOf(
                packZip(packUuid, "Brands", "github.svg" to listOf("GitHub")),
                Uri.fromFile(makeZip("bad.zip", "pack.json" to "garbage".toByteArray())),
                packZip(otherUuid, "More", "proton.svg" to listOf("Proton")),
            )
        )
        advanceUntilIdle()

        val state = vm.uiState.first { it.importSummary != null }
        assertEquals(2, state.importSummary!!.imported)
        assertEquals(1, state.importSummary!!.failures.size)
        assertEquals(2, state.rows.size)
        assertFalse(state.isImporting)
    }

    @Test
    fun `missing pack json is reported as a failure`() = runTest(dispatcher) {
        val vm = newVm()
        vm.importPacks(listOf(Uri.fromFile(makeZip("nopack.zip", "icon.svg" to ByteArray(0)))))
        advanceUntilIdle()
        val summary = vm.uiState.first { it.importSummary != null }.importSummary!!
        assertEquals(0, summary.imported)
        assertEquals("pack.json missing", summary.failures.single())
    }

    @Test
    fun `clearImportSummary resets the summary`() = runTest(dispatcher) {
        val vm = newVm()
        vm.importPacks(listOf(Uri.fromFile(makeZip("bad.zip", "pack.json" to "x".toByteArray()))))
        advanceUntilIdle()
        vm.uiState.first { it.importSummary != null }
        vm.clearImportSummary()
        assertNull(vm.uiState.first { it.importSummary == null }.importSummary)
    }

    @Test
    fun `a disabled pack stays listed but is left out of auto-match`() = runTest(dispatcher) {
        runBlocking { repo.addAccount(account(issuer = "GitHub")) }
        val vm = newVm()
        vm.importPacks(listOf(packZip(packUuid, "Brands", "github.svg" to listOf("GitHub"))))
        advanceUntilIdle()
        vm.dismissAutoMatch()

        vm.setPackEnabled(packUuid, enabled = false)
        advanceUntilIdle()
        val disabled = vm.uiState.first { it.rows.any { row -> !row.enabled } }
        assertEquals(1, disabled.rows.size)

        vm.importPacks(listOf(packZip(packUuid, "Brands", "github.svg" to listOf("GitHub"))))
        advanceUntilIdle()
        assertTrue(vm.uiState.first { it.rows.all { row -> row.enabled } }.rows.single().enabled)
    }

    @Test
    fun `uninstall forgets the stored preferences for that pack`() = runTest(dispatcher) {
        val vm = newVm()
        vm.importPacks(listOf(packZip(packUuid, "Brands", "github.svg" to listOf("GitHub"))))
        advanceUntilIdle()
        vm.setPackEnabled(packUuid, enabled = false)
        advanceUntilIdle()
        assertTrue(packUuid in prefs.disabledIconPacks.first())

        vm.uninstall(packUuid)
        assertTrue(prefs.disabledIconPacks.first { packUuid !in it }.isEmpty())
        assertTrue(vm.uiState.first { it.rows.isEmpty() }.rows.isEmpty())
    }

    @Test
    fun `pack priority decides which of two exact matches wins`() = runTest(dispatcher) {
        runBlocking { repo.addAccount(account(issuer = "GitHub")) }
        val vm = newVm()
        vm.importPacks(
            listOf(
                packZip(packUuid, "Alpha", "a.svg" to listOf("GitHub")),
                packZip(otherUuid, "Zeta", "z.svg" to listOf("GitHub")),
            )
        )
        advanceUntilIdle()
        assertEquals(packUuid, vm.uiState.first { it.autoMatch != null }.autoMatch!!.fresh.single().packUuid)
        vm.dismissAutoMatch()

        vm.reorder(listOf(otherUuid, packUuid))
        advanceUntilIdle()
        vm.importPacks(listOf(packZip(otherUuid, "Zeta", "z.svg" to listOf("GitHub"))))
        advanceUntilIdle()
        assertEquals(otherUuid, vm.uiState.first { it.autoMatch != null }.autoMatch!!.fresh.single().packUuid)
    }

    private fun account(issuer: String, custom: ByteArray? = null) = OtpAccount(
        issuer = issuer,
        accountName = "user@$issuer",
        secret = "JBSWY3DPEHPK3PXP",
        customIconBytes = custom,
    )

    private fun installPack(uuid: String, name: String, vararg icons: Pair<String, List<String>>) {
        runBlocking { manager.install(packZip(uuid, name, *icons)) }
    }

    private fun packZip(
        uuid: String,
        name: String,
        vararg icons: Pair<String, List<String>>,
    ): Uri {
        val iconsJson = icons.joinToString(",") { (filename, issuers) ->
            val issuerArr = issuers.joinToString(",") { "\"$it\"" }
            """{"filename":"$filename","name":"${filename.substringBeforeLast('.')}","issuer":[$issuerArr]}"""
        }
        val packJson = """{"uuid":"$uuid","name":"$name","icons":[$iconsJson]}"""
        val entries = mutableListOf<Pair<String, ByteArray>>("pack.json" to packJson.toByteArray())
        icons.forEach { (filename, _) -> entries += filename to "<svg/>".toByteArray() }
        return Uri.fromFile(makeZip("$uuid.zip", *entries.toTypedArray()))
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
