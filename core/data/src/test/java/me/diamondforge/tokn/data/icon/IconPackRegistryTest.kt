package me.diamondforge.tokn.data.icon

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.diamondforge.tokn.data.preferences.AppPreferencesRepository
import me.diamondforge.tokn.data.preferences.FakePreferencesDataStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IconPackRegistryTest {

    private lateinit var context: Context
    private lateinit var manager: IconPackManager
    private lateinit var prefs: AppPreferencesRepository
    private lateinit var registry: IconPackRegistry
    private lateinit var workDir: File

    private val alphaUuid = "11111111-1111-1111-1111-111111111111"
    private val zetaUuid = "22222222-2222-2222-2222-222222222222"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "icon-packs").deleteRecursively()
        manager = IconPackManager(context)
        prefs = AppPreferencesRepository(FakePreferencesDataStore())
        registry = IconPackRegistry(manager, prefs)
        workDir = File(context.cacheDir, "registry-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun `with no stored order packs stay alphabetical`() = runBlocking {
        install(zetaUuid, "Zeta")
        install(alphaUuid, "Alpha")

        assertEquals(
            listOf("Alpha", "Zeta"),
            registry.activePacks.first().map { it.pack.name },
        )
    }

    @Test
    fun `a stored order wins over the alphabetical fallback`() = runBlocking {
        install(alphaUuid, "Alpha")
        install(zetaUuid, "Zeta")

        registry.setOrder(listOf(zetaUuid, alphaUuid))

        assertEquals(
            listOf("Zeta", "Alpha"),
            registry.activePacks.first().map { it.pack.name },
        )
    }

    @Test
    fun `packs missing from the stored order are appended alphabetically`() = runBlocking {
        install(alphaUuid, "Alpha")
        install(zetaUuid, "Zeta")
        val midUuid = "33333333-3333-3333-3333-333333333333"
        install(midUuid, "Mike")

        registry.setOrder(listOf(zetaUuid))

        assertEquals(
            listOf("Zeta", "Alpha", "Mike"),
            registry.activePacks.first().map { it.pack.name },
        )
    }

    @Test
    fun `a stale uuid in the stored order is ignored`() = runBlocking {
        install(alphaUuid, "Alpha")

        registry.setOrder(listOf("99999999-9999-9999-9999-999999999999", alphaUuid))

        assertEquals(listOf("Alpha"), registry.activePacks.first().map { it.pack.name })
    }

    @Test
    fun `a disabled pack drops out of activePacks but stays in orderedPacks`() = runBlocking {
        install(alphaUuid, "Alpha")
        install(zetaUuid, "Zeta")

        registry.setEnabled(alphaUuid, enabled = false)

        assertEquals(listOf("Zeta"), registry.activePacks.first().map { it.pack.name })
        val ordered = registry.orderedPacks.first()
        assertEquals(2, ordered.size)
        assertFalse(ordered.single { it.pack.pack.uuid == alphaUuid }.enabled)
    }

    @Test
    fun `a newly installed pack is enabled by default`() = runBlocking {
        install(alphaUuid, "Alpha")
        assertTrue(registry.orderedPacks.first().single().enabled)
    }

    @Test
    fun `a disabled pack still resolves an already assigned icon`() = runBlocking {
        install(alphaUuid, "Alpha")
        registry.setEnabled(alphaUuid, enabled = false)

        assertTrue(registry.activePacks.first().isEmpty())
        assertNotNull(manager.iconFile(alphaUuid, "icon.svg"))
    }

    @Test
    fun `forget clears both the disabled flag and the order slot`() = runBlocking {
        install(alphaUuid, "Alpha")
        registry.setEnabled(alphaUuid, enabled = false)
        registry.setOrder(listOf(alphaUuid))

        registry.forget(alphaUuid)

        assertFalse(alphaUuid in prefs.disabledIconPacks.first())
        assertFalse(alphaUuid in prefs.iconPackOrder.first())
    }

    private suspend fun install(uuid: String, name: String) {
        val packJson =
            """{"uuid":"$uuid","name":"$name","icons":[{"filename":"icon.svg","name":"icon","issuer":["$name"]}]}"""
        val zip = File(workDir, "$uuid.zip")
        ZipOutputStream(zip.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("pack.json"))
            zos.write(packJson.toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("icon.svg"))
            zos.write("<svg/>".toByteArray())
            zos.closeEntry()
        }
        val result = manager.install(Uri.fromFile(zip))
        check(result is InstallResult.Success) { "install failed: $result" }
    }
}
