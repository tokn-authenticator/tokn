package me.diamondforge.tokn.importer

import me.diamondforge.tokn.importer.aegis.AegisFixtureWriter
import me.diamondforge.tokn.importer.aegis.AegisImporter
import me.diamondforge.tokn.importer.otpauth.OtpAuthUriImporter
import me.diamondforge.tokn.importer.twofas.TwoFasFixtureWriter
import me.diamondforge.tokn.importer.twofas.TwoFasImporter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImporterRegistryTest {
    private val registry = ImporterRegistry(
        setOf(
            AegisImporter(),
            TwoFasImporter(),
            OtpAuthUriImporter(),
        ),
    )

    @Test
    fun `detect routes Aegis bytes to AegisImporter`() {
        val raw = AegisFixtureWriter.plain(emptyDb()).toByteArray()
        assertEquals("aegis", registry.detect(raw)?.id)
    }

    @Test
    fun `detect routes 2FAS bytes to TwoFasImporter`() {
        val raw = TwoFasFixtureWriter.plain(JSONArray()).toByteArray()
        assertEquals("twofas", registry.detect(raw)?.id)
    }

    @Test
    fun `detect routes plain otpauth uri to OtpAuthUriImporter`() {
        val raw = "otpauth://totp/ACME:alice?secret=JBSWY3DPEHPK3PXP".toByteArray()
        assertEquals("otpauth_uri", registry.detect(raw)?.id)
    }

    @Test
    fun `detect returns null for unknown content`() {
        assertNull(registry.detect("foo bar".toByteArray()))
        assertNull(registry.detect("""{"random":"json"}""".toByteArray()))
    }

    @Test
    fun `all returns importers sorted by display name`() {
        val names = registry.all().map { it.displayName }
        assertEquals(names.sortedBy { it.lowercase() }, names)
    }

    @Test
    fun `byId returns the matching importer`() {
        assertEquals("aegis", registry.byId("aegis")?.id)
        assertNull(registry.byId("nope"))
    }

    private fun emptyDb(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("entries", JSONArray())
    }
}
