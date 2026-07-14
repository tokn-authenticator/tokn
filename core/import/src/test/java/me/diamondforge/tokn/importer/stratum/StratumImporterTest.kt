package me.diamondforge.tokn.importer.stratum

import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.importer.ImportOutcome
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StratumImporterTest {
    private val importer = StratumImporter()

    // region canHandle

    @Test
    fun `canHandle accepts plain Stratum JSON`() {
        val raw =
            StratumFixtureWriter.plain(StratumFixtureWriter.backup(StratumFixtureWriter.totp()))
        assertTrue(importer.canHandle(raw))
    }

    @Test
    fun `canHandle accepts legacy encrypted header`() {
        val raw = StratumFixtureWriter.legacy(
            StratumFixtureWriter.backup(StratumFixtureWriter.totp()), "pw"
        )
        assertTrue(importer.canHandle(raw))
    }

    @Test
    fun `canHandle rejects unrelated JSON`() {
        assertFalse(importer.canHandle("""{"db":{},"header":{},"version":1}""".toByteArray()))
        assertFalse(importer.canHandle("not json".toByteArray()))
    }

    // endregion

    // region plain JSON parsing

    @Test
    fun `parse plain maps all TOTP fields`() {
        val raw = StratumFixtureWriter.plain(
            StratumFixtureWriter.backup(
                StratumFixtureWriter.totp(
                    issuer = "Google",
                    username = "user@gmail.com",
                    secret = "JBSWY3DPEHPK3PXP",
                    algorithm = 1,  // SHA256
                    digits = 8,
                    period = 60,
                )
            )
        )
        val accounts = (importer.parse(raw, null) as ImportOutcome.Success).accounts
        assertEquals(1, accounts.size)
        with(accounts[0]) {
            assertEquals("Google", issuer)
            assertEquals("user@gmail.com", accountName)
            assertEquals("JBSWY3DPEHPK3PXP", secret)
            assertEquals(OtpType.TOTP, type)
            assertEquals(OtpAlgorithm.SHA256, algorithm)
            assertEquals(8, digits)
            assertEquals(60, period)
        }
    }

    @Test
    fun `parse plain maps HOTP counter`() {
        val raw = StratumFixtureWriter.plain(
            StratumFixtureWriter.backup(StratumFixtureWriter.hotp(counter = 42))
        )
        val accounts = (importer.parse(raw, null) as ImportOutcome.Success).accounts
        assertEquals(OtpType.HOTP, accounts[0].type)
        assertEquals(42L, accounts[0].counter)
    }

    @Test
    fun `parse plain maps SHA512 algorithm`() {
        val entry = StratumFixtureWriter.totp(algorithm = 2)
        val raw = StratumFixtureWriter.plain(StratumFixtureWriter.backup(entry))
        val accounts = (importer.parse(raw, null) as ImportOutcome.Success).accounts
        assertEquals(OtpAlgorithm.SHA512, accounts[0].algorithm)
    }

    @Test
    fun `parse plain resolves categories to groups`() {
        val secret = "JBSWY3DPEHPK3PXP"
        val backup = JSONObject().apply {
            put("Authenticators", JSONArray().put(StratumFixtureWriter.totp(secret = secret)))
            put("Categories", JSONArray().put(JSONObject().apply {
                put("Id", "abc12345")
                put("Name", "Work")
                put("Ranking", 0)
            }))
            put("AuthenticatorCategories", JSONArray().put(JSONObject().apply {
                put("AuthenticatorSecret", secret)
                put("CategoryId", "abc12345")
                put("Ranking", 0)
            }))
            put("CustomIcons", JSONArray())
        }
        val accounts = (importer.parse(
            StratumFixtureWriter.plain(backup),
            null
        ) as ImportOutcome.Success).accounts
        assertEquals(listOf("Work"), accounts[0].groups)
    }

    @Test
    fun `parse plain resolves custom icon`() {
        val iconId = "icon0001"
        val iconPng = byteArrayOf(1, 2, 3, 4)
        val iconBase64 = android.util.Base64.encodeToString(iconPng, android.util.Base64.DEFAULT)
        val backup = JSONObject().apply {
            put(
                "Authenticators", JSONArray().put(
                    StratumFixtureWriter.totp().apply { put("Icon", "@$iconId") }
                ))
            put("Categories", JSONArray())
            put("AuthenticatorCategories", JSONArray())
            put("CustomIcons", JSONArray().put(JSONObject().apply {
                put("Id", iconId)
                put("Data", iconBase64)
            }))
        }
        val accounts = (importer.parse(
            StratumFixtureWriter.plain(backup),
            null
        ) as ImportOutcome.Success).accounts
        assertNotNull(accounts[0].customIconBytes)
        assertArrayEquals(iconPng, accounts[0].customIconBytes)
    }

    @Test
    fun `parse plain skips unsupported types and reports count`() {
        val raw = StratumFixtureWriter.plain(
            StratumFixtureWriter.backup(
                StratumFixtureWriter.totp(),
                StratumFixtureWriter.unsupported(3),  // MobileOtp
                StratumFixtureWriter.unsupported(4),  // SteamOtp
                StratumFixtureWriter.unsupported(5),  // YandexOtp
            )
        )
        val outcome = importer.parse(raw, null) as ImportOutcome.Success
        assertEquals(1, outcome.accounts.size)
        assertEquals(3, outcome.unsupportedCount)
    }

    @Test
    fun `parse plain with empty backup returns success with zero accounts`() {
        val raw = StratumFixtureWriter.plain(StratumFixtureWriter.backup())
        val outcome = importer.parse(raw, null) as ImportOutcome.Success
        assertEquals(0, outcome.accounts.size)
        assertEquals(0, outcome.unsupportedCount)
    }

    @Test
    fun `parse non-stratum JSON returns Malformed`() {
        val outcome = importer.parse("""{"foo":"bar"}""".toByteArray(), null)
        assertTrue(outcome is ImportOutcome.Malformed)
    }

    // endregion

    // region legacy encryption

    @Test
    fun `parse legacy encrypted returns NeedsPassword when no password given`() {
        val raw = StratumFixtureWriter.legacy(
            StratumFixtureWriter.backup(StratumFixtureWriter.totp()), "secret"
        )
        assertEquals(ImportOutcome.NeedsPassword, importer.parse(raw, null))
    }

    @Test
    fun `parse legacy encrypted with correct password returns accounts`() {
        val raw = StratumFixtureWriter.legacy(
            StratumFixtureWriter.backup(StratumFixtureWriter.totp(issuer = "GitHub")), "hunter2"
        )
        val accounts = (importer.parse(raw, "hunter2") as ImportOutcome.Success).accounts
        assertEquals(1, accounts.size)
        assertEquals("GitHub", accounts[0].issuer)
    }

    @Test
    fun `parse legacy encrypted with wrong password returns WrongPassword`() {
        val raw = StratumFixtureWriter.legacy(
            StratumFixtureWriter.backup(StratumFixtureWriter.totp()), "correct"
        )
        assertTrue(importer.parse(raw, "wrong") is ImportOutcome.WrongPassword)
    }

    // endregion
}
