package me.diamondforge.tokn.importer.aegis

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
class AegisImporterTest {
    private val importer = AegisImporter()

    @Test
    fun `canHandle accepts a plain Aegis vault`() {
        val raw = AegisFixtureWriter.plain(sampleDb()).toByteArray()
        assertTrue(importer.canHandle(raw))
    }

    @Test
    fun `canHandle accepts an encrypted Aegis vault`() {
        val raw = AegisFixtureWriter.encrypted(sampleDb(), password = "test").toByteArray()
        assertTrue(importer.canHandle(raw))
    }

    @Test
    fun `canHandle rejects unrelated JSON`() {
        assertFalse(importer.canHandle("""{"foo":1}""".toByteArray()))
        assertFalse(importer.canHandle("plain text".toByteArray()))
    }

    @Test
    fun `parse plain returns all entries`() {
        val raw = AegisFixtureWriter.plain(sampleDb()).toByteArray()
        val outcome = importer.parse(raw, password = null)
        val accounts = (outcome as ImportOutcome.Success).accounts
        assertEquals(3, accounts.size)
        val rfc = accounts[0]
        assertEquals("ACME", rfc.issuer)
        assertEquals("alice@example.com", rfc.accountName)
        assertEquals("JBSWY3DPEHPK3PXP", rfc.secret)
        assertEquals(OtpAlgorithm.SHA1, rfc.algorithm)
        assertEquals(OtpType.TOTP, rfc.type)
        assertEquals(6, rfc.digits)
        assertEquals(30, rfc.period)

        val hotp = accounts.first { it.type == OtpType.HOTP }
        assertEquals(7L, hotp.counter)
        val sha512 = accounts.first { it.algorithm == OtpAlgorithm.SHA512 }
        assertEquals(8, sha512.digits)
    }

    @Test
    fun `parse encrypted without password returns NeedsPassword`() {
        val raw = AegisFixtureWriter.encrypted(sampleDb(), password = "test").toByteArray()
        assertEquals(ImportOutcome.NeedsPassword, importer.parse(raw, password = null))
        assertEquals(ImportOutcome.NeedsPassword, importer.parse(raw, password = ""))
    }

    @Test
    fun `parse encrypted with correct password returns Success`() {
        val raw = AegisFixtureWriter.encrypted(sampleDb(), password = "correct horse").toByteArray()
        val outcome = importer.parse(raw, password = "correct horse")
        val accounts = (outcome as ImportOutcome.Success).accounts
        assertEquals(3, accounts.size)
    }

    @Test
    fun `parse encrypted with wrong password returns WrongPassword`() {
        val raw = AegisFixtureWriter.encrypted(sampleDb(), password = "right").toByteArray()
        val outcome = importer.parse(raw, password = "wrong")
        assertTrue(
            "expected WrongPassword but was $outcome",
            outcome is ImportOutcome.WrongPassword
        )
    }

    @Test
    fun `parse encrypted with no password slot returns Unsupported`() {
        val raw = AegisFixtureWriter.encryptedHardwareOnly(sampleDb()).toByteArray()
        val outcome = importer.parse(raw, password = "anything")
        assertEquals(ImportOutcome.Unsupported, outcome)
    }

    @Test
    fun `parse malformed JSON returns Malformed`() {
        val outcome = importer.parse("{not json".toByteArray(), password = null)
        assertTrue(outcome is ImportOutcome.Malformed)
    }

    @Test
    fun `entries with unknown type are skipped`() {
        val db = JSONObject().apply {
            put("version", 1)
            put("entries", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "yubikey")
                    put("name", "skip me")
                    put("issuer", "")
                    put("info", JSONObject().apply { put("secret", "JBSWY3DPEHPK3PXP") })
                })
                put(JSONObject().apply {
                    put("type", "totp")
                    put("name", "keep me")
                    put("issuer", "ACME")
                    put("info", JSONObject().apply {
                        put("secret", "JBSWY3DPEHPK3PXP")
                        put("algo", "SHA1")
                        put("digits", 6)
                        put("period", 30)
                    })
                })
            })
        }
        val raw = AegisFixtureWriter.plain(db).toByteArray()
        val accounts = (importer.parse(raw, null) as ImportOutcome.Success).accounts
        assertEquals(1, accounts.size)
        assertEquals("keep me", accounts.first().accountName)
        assertTrue(accounts.first().groups.isEmpty())
    }

    private fun sampleDb(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("entries", JSONArray().apply {
            put(JSONObject().apply {
                put("type", "totp")
                put("uuid", "11111111-1111-1111-1111-111111111111")
                put("name", "alice@example.com")
                put("issuer", "ACME")
                put("info", JSONObject().apply {
                    put("secret", "JBSWY3DPEHPK3PXP")
                    put("algo", "SHA1")
                    put("digits", 6)
                    put("period", 30)
                })
            })
            put(JSONObject().apply {
                put("type", "totp")
                put("uuid", "22222222-2222-2222-2222-222222222222")
                put("name", "carol@example.com")
                put("issuer", "Globex")
                put("info", JSONObject().apply {
                    put("secret", "5OM4WOOGPLQEF6UGN3CPEOOLWU")
                    put("algo", "SHA512")
                    put("digits", 8)
                    put("period", 30)
                })
            })
            put(JSONObject().apply {
                put("type", "hotp")
                put("uuid", "33333333-3333-3333-3333-333333333333")
                put("name", "bob")
                put("issuer", "")
                put("info", JSONObject().apply {
                    put("secret", "JBSWY3DPEHPK3PXP")
                    put("algo", "SHA256")
                    put("digits", 6)
                    put("counter", 7)
                })
            })
        })
    }
}
