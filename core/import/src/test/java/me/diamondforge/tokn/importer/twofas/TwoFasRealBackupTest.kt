package me.diamondforge.tokn.importer.twofas

import me.diamondforge.tokn.importer.ImportOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the importer against unmodified backups exported from a real 2FAS Authenticator
 * app (Android v5.5.3, schemaVersion 4). The plain backup contains a single account named
 * "Hello"; the encrypted backup wraps the same kind of data with password `hello`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TwoFasRealBackupTest {
    private val importer = TwoFasImporter()

    @Test
    fun `real plain backup parses single account`() {
        val raw = loadResource("importers/twofas/sample_plain_v4.2fas")
        assertTrue(importer.canHandle(raw))
        val accounts = (importer.parse(raw, null) as ImportOutcome.Success).accounts
        assertEquals(1, accounts.size)
        val account = accounts.first()
        assertEquals("Hello", account.issuer)
        assertEquals("hello", account.secret)
        assertEquals(6, account.digits)
        assertEquals(30, account.period)
    }

    @Test
    fun `real encrypted backup is canHandle`() {
        val raw = loadResource("importers/twofas/sample_encrypted_v4.2fas")
        assertTrue(importer.canHandle(raw))
    }

    @Test
    fun `real encrypted backup without password returns NeedsPassword`() {
        val raw = loadResource("importers/twofas/sample_encrypted_v4.2fas")
        assertEquals(ImportOutcome.NeedsPassword, importer.parse(raw, password = null))
    }

    @Test
    fun `real encrypted backup decrypts with correct password`() {
        val raw = loadResource("importers/twofas/sample_encrypted_v4.2fas")
        val outcome = importer.parse(raw, password = "hello")
        assertTrue("expected Success but was $outcome", outcome is ImportOutcome.Success)
        // The corresponding plain backup has one account; the encrypted one was exported
        // immediately after and should have the same content.
        val accounts = (outcome as ImportOutcome.Success).accounts
        assertEquals(1, accounts.size)
    }

    @Test
    fun `real encrypted backup with wrong password returns WrongPassword`() {
        val raw = loadResource("importers/twofas/sample_encrypted_v4.2fas")
        val outcome = importer.parse(raw, password = "nope")
        assertTrue("expected WrongPassword but was $outcome", outcome is ImportOutcome.WrongPassword)
    }

    private fun loadResource(path: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream(path)!!.use { it.readBytes() }
}
