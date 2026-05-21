package me.diamondforge.tokn.importer.otpauth

import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.importer.ImportOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OtpAuthMigrationImporterTest {
    private val importer = OtpAuthMigrationImporter()

    @Test
    fun `canHandle accepts a migration URI`() {
        val uri = MigrationFixtureWriter.buildUri(listOf(rfcVector()))
        assertTrue(importer.canHandle(uri.toByteArray()))
    }

    @Test
    fun `canHandle rejects a plain otpauth URI`() {
        assertFalse(importer.canHandle("otpauth://totp/ACME:alice?secret=JBSWY3DPEHPK3PXP".toByteArray()))
    }

    @Test
    fun `parse decodes a single entry to base32 secret`() {
        val uri = MigrationFixtureWriter.buildUri(listOf(rfcVector()))
        val accounts = (importer.parse(uri.toByteArray(), null) as ImportOutcome.Success).accounts
        assertEquals(1, accounts.size)
        assertEquals("JBSWY3DPEHPK3PXP", accounts.first().secret)
        assertEquals("ACME", accounts.first().issuer)
        assertEquals("alice@example.com", accounts.first().accountName)
        assertEquals(OtpAlgorithm.SHA1, accounts.first().algorithm)
        assertEquals(OtpType.TOTP, accounts.first().type)
    }

    @Test
    fun `parse handles multiple URIs across lines`() {
        val one = MigrationFixtureWriter.buildUri(listOf(rfcVector()), batchIndex = 0, batchSize = 2)
        val two = MigrationFixtureWriter.buildUri(
            listOf(
                MigrationEntry(
                    secret = Base32.decode("5OM4WOOGPLQEF6UGN3CPEOOLWU"),
                    name = "bob",
                    issuer = "Globex",
                    algorithm = 3,   // SHA512
                    digits = 2,      // EIGHT
                    type = 1,        // HOTP
                    counter = 7,
                ),
            ),
            batchIndex = 1,
            batchSize = 2,
        )
        val accounts = (importer.parse("$one\n$two".toByteArray(), null) as ImportOutcome.Success).accounts
        assertEquals(2, accounts.size)
        val hotp = accounts.first { it.type == OtpType.HOTP }
        assertEquals(OtpAlgorithm.SHA512, hotp.algorithm)
        assertEquals(8, hotp.digits)
        assertEquals(7L, hotp.counter)
        assertEquals("5OM4WOOGPLQEF6UGN3CPEOOLWU", hotp.secret)
    }

    @Test
    fun `parse with no migration URI returns Malformed`() {
        assertTrue(importer.parse("just text".toByteArray(), null) is ImportOutcome.Malformed)
    }

    private fun rfcVector() = MigrationEntry(
        secret = Base32.decode("JBSWY3DPEHPK3PXP"),
        name = "alice@example.com",
        issuer = "ACME",
    )
}
