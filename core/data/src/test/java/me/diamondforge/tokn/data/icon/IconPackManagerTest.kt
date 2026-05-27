package me.diamondforge.tokn.data.icon

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IconPackManagerTest {

    private lateinit var context: Context
    private lateinit var manager: IconPackManager
    private lateinit var stagingDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clean any prior pack-dir from earlier tests.
        File(context.filesDir, "icon-packs").deleteRecursively()
        manager = IconPackManager(context)
        stagingDir = File(context.cacheDir, "test-staging").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        stagingDir.deleteRecursively()
    }

    @Test
    fun `install succeeds for a well-formed zip and surfaces the pack on the installed flow`() =
        runBlocking {
            val uuid = "11111111-2222-3333-4444-555555555555"
            val zip = makeZip(
                "test.zip",
                "pack.json" to packJson(
                    uuid,
                    name = "Cool Pack",
                    iconFilenames = listOf("a.svg", "b.svg")
                ),
                "a.svg" to "<svg/>".toByteArray(),
                "b.svg" to "<svg/>".toByteArray(),
            )

            val result = manager.install(Uri.fromFile(zip))

            assertTrue("expected Success, got $result", result is InstallResult.Success)
            val installed = (result as InstallResult.Success).pack
            assertEquals("Cool Pack", installed.pack.name)
            assertEquals(uuid, installed.pack.uuid)
            assertEquals(2, installed.iconCount)

            // installed flow reflects it
            val all = manager.installed.value
            assertEquals(1, all.size)
            assertEquals(uuid, all.single().pack.uuid)
        }

    @Test
    fun `missing pack json yields MissingPackJson`() = runBlocking {
        val zip = makeZip(
            "no-pack.zip",
            "icon.svg" to "<svg/>".toByteArray(),
        )
        assertTrue(manager.install(Uri.fromFile(zip)) is InstallResult.MissingPackJson)
        assertTrue(manager.installed.value.isEmpty())
    }

    @Test
    fun `invalid pack json reports InvalidPackJson with a reason`() = runBlocking {
        val zip = makeZip(
            "bad.zip",
            "pack.json" to "not json".toByteArray(),
        )
        val result = manager.install(Uri.fromFile(zip))
        assertTrue("expected InvalidPackJson, got $result", result is InstallResult.InvalidPackJson)
        val reason = (result as InstallResult.InvalidPackJson).reason
        assertTrue(reason.contains("JSON") || reason.contains("UUID") || reason.contains("name"))
    }

    @Test
    fun `zip slip is rejected as Failed`() = runBlocking {
        val zip = makeZip(
            "evil.zip",
            "../../../escaped.txt" to "pwn".toByteArray(),
            "pack.json" to packJson(
                "22222222-2222-2222-2222-222222222222",
                "Evil",
                listOf(),
            ),
        )
        val result = manager.install(Uri.fromFile(zip))
        assertTrue("expected Failed, got $result", result is InstallResult.Failed)
        val reason = (result as InstallResult.Failed).reason
        assertTrue(
            "reason should mention escape: $reason",
            reason.contains("escapes target", ignoreCase = true)
        )
    }

    @Test
    fun `iconFile returns existing file path and null for missing icon`() = runBlocking {
        val uuid = "33333333-3333-3333-3333-333333333333"
        val zip = makeZip(
            "icons.zip",
            "pack.json" to packJson(uuid, "Icons", listOf("present.svg")),
            "present.svg" to "<svg/>".toByteArray(),
        )
        manager.install(Uri.fromFile(zip))

        val found = manager.iconFile(uuid, "present.svg")
        assertNotNull(found)
        assertTrue(found!!.exists())

        assertNull(manager.iconFile(uuid, "absent.svg"))
        assertNull(manager.iconFile("no-such-pack", "present.svg"))
    }

    @Test
    fun `uninstall removes the directory and clears the installed flow`() = runBlocking {
        val uuid = "44444444-4444-4444-4444-444444444444"
        manager.install(
            Uri.fromFile(
                makeZip(
                    "p.zip",
                    "pack.json" to packJson(uuid, "P", listOf("a.svg")),
                    "a.svg" to ByteArray(0),
                ),
            ),
        )
        assertEquals(1, manager.installed.value.size)

        val removed = manager.uninstall(uuid)
        assertTrue(removed)
        assertTrue(manager.installed.value.isEmpty())
    }

    @Test
    fun `re-installing the same uuid overwrites prior pack`() = runBlocking {
        val uuid = "55555555-5555-5555-5555-555555555555"
        val first = makeZip(
            "v1.zip",
            "pack.json" to packJson(uuid, "v1", listOf("a.svg")),
            "a.svg" to ByteArray(0),
        )
        val second = makeZip(
            "v2.zip",
            "pack.json" to packJson(uuid, "v2", listOf("b.svg")),
            "b.svg" to ByteArray(0),
        )
        manager.install(Uri.fromFile(first))
        manager.install(Uri.fromFile(second))

        assertEquals(1, manager.installed.value.size)
        assertEquals("v2", manager.installed.value.single().pack.name)
        // Old icon should no longer exist.
        assertNull(manager.iconFile(uuid, "a.svg"))
        assertNotNull(manager.iconFile(uuid, "b.svg"))
    }

    @Test
    fun `installed flow is sorted by lowercase name`() = runBlocking {
        listOf(
            "66666666-6666-6666-6666-666666666661" to "Zeta",
            "66666666-6666-6666-6666-666666666662" to "alpha",
            "66666666-6666-6666-6666-666666666663" to "Mike",
        ).forEach { (uuid, name) ->
            manager.install(
                Uri.fromFile(
                    makeZip(
                        "$uuid.zip",
                        "pack.json" to packJson(uuid, name, listOf()),
                    ),
                ),
            )
        }
        val names = manager.installed.value.map { it.pack.name }
        assertEquals(listOf("alpha", "Mike", "Zeta"), names)
    }

    private fun packJson(uuid: String, name: String, iconFilenames: List<String>): ByteArray {
        val iconsJson = iconFilenames.joinToString(",") {
            """{"filename":"$it","name":"${it.substringBeforeLast('.')}"}"""
        }
        return """{"uuid":"$uuid","name":"$name","icons":[$iconsJson]}""".toByteArray()
    }

    private fun makeZip(name: String, vararg entries: Pair<String, ByteArray>): File {
        val out = File(stagingDir, name)
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
