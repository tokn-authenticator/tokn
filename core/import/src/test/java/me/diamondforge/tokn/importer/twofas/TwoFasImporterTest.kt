package me.diamondforge.tokn.importer.twofas

import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.importer.ImportOutcome
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TwoFasImporterTest {
    private val importer = TwoFasImporter()

    @Test
    fun `canHandle accepts plain 2fas backup`() {
        val raw = TwoFasFixtureWriter.plain(sampleServices()).toByteArray()
        assertTrue(importer.canHandle(raw))
    }

    @Test
    fun `canHandle rejects non-2fas JSON`() {
        assertFalse(importer.canHandle("""{"db":{},"header":{},"version":1}""".toByteArray()))
        assertFalse(importer.canHandle("not json".toByteArray()))
    }

    @Test
    fun `parse plain returns entries`() {
        val raw = TwoFasFixtureWriter.plain(sampleServices()).toByteArray()
        val accounts = (importer.parse(raw, null) as ImportOutcome.Success).accounts
        assertEquals(3, accounts.size)

        val totp = accounts.first()
        assertEquals("ACME", totp.issuer)
        assertEquals("alice@example.com", totp.accountName)
        assertEquals("JBSWY3DPEHPK3PXP", totp.secret)
        assertEquals(OtpType.TOTP, totp.type)

        val hotp = accounts.first { it.type == OtpType.HOTP }
        assertEquals(42L, hotp.counter)

        val sha512 = accounts.first { it.algorithm == OtpAlgorithm.SHA512 }
        assertEquals(8, sha512.digits)
    }

    @Test
    fun `parse encrypted without password returns NeedsPassword`() {
        val raw = TwoFasFixtureWriter.encrypted(sampleServices(), password = "test").toByteArray()
        assertEquals(ImportOutcome.NeedsPassword, importer.parse(raw, password = null))
    }

    @Test
    fun `parse encrypted with correct password returns Success`() {
        val raw =
            TwoFasFixtureWriter.encrypted(sampleServices(), password = "correct").toByteArray()
        val accounts = (importer.parse(raw, password = "correct") as ImportOutcome.Success).accounts
        assertEquals(3, accounts.size)
    }

    @Test
    fun `parse encrypted with wrong password returns WrongPassword`() {
        val raw = TwoFasFixtureWriter.encrypted(sampleServices(), password = "right").toByteArray()
        val outcome = importer.parse(raw, password = "wrong")
        assertTrue(
            "expected WrongPassword but was $outcome",
            outcome is ImportOutcome.WrongPassword
        )
    }

    @Test
    fun `parse missing schemaVersion is malformed via canHandle`() {
        val raw = """{"services":[]}""".toByteArray()
        assertFalse(importer.canHandle(raw))
    }

    @Test
    fun `service without secret is skipped`() {
        val services = JSONArray().apply {
            put(JSONObject().apply { put("name", "no secret"); put("otp", JSONObject()) })
            put(JSONObject().apply {
                put("name", "good")
                put("secret", "JBSWY3DPEHPK3PXP")
                put("otp", JSONObject().apply { put("tokenType", "TOTP") })
            })
        }
        val raw = TwoFasFixtureWriter.plain(services).toByteArray()
        val accounts = (importer.parse(raw, null) as ImportOutcome.Success).accounts
        assertEquals(1, accounts.size)
        assertEquals("good", accounts.first().issuer)
    }

    private fun sampleServices(): JSONArray = JSONArray().apply {
        put(JSONObject().apply {
            put("name", "ACME")
            put("secret", "JBSWY3DPEHPK3PXP")
            put("otp", JSONObject().apply {
                put("account", "alice@example.com")
                put("issuer", "ACME")
                put("digits", 6)
                put("period", 30)
                put("algorithm", "SHA1")
                put("tokenType", "TOTP")
            })
        })
        put(JSONObject().apply {
            put("name", "Globex")
            put("secret", "5OM4WOOGPLQEF6UGN3CPEOOLWU")
            put("otp", JSONObject().apply {
                put("account", "carol@example.com")
                put("digits", 8)
                put("period", 30)
                put("algorithm", "SHA512")
                put("tokenType", "TOTP")
            })
        })
        put(JSONObject().apply {
            put("name", "Initech")
            put("secret", "JBSWY3DPEHPK3PXP")
            put("otp", JSONObject().apply {
                put("account", "bob")
                put("digits", 6)
                put("algorithm", "SHA256")
                put("tokenType", "HOTP")
                put("counter", 42)
            })
        })
    }
}
