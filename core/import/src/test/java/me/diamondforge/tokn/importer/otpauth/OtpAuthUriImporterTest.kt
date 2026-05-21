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
class OtpAuthUriImporterTest {
    private val importer = OtpAuthUriImporter()

    @Test
    fun `canHandle accepts a single line URI`() {
        assertTrue(importer.canHandle("otpauth://totp/ACME:alice?secret=JBSWY3DPEHPK3PXP".toByteArray()))
    }

    @Test
    fun `canHandle rejects migration URI`() {
        assertFalse(importer.canHandle("otpauth-migration://offline?data=AAAA".toByteArray()))
    }

    @Test
    fun `parse extracts all uri lines`() {
        val raw = """
            # tokn export
            otpauth://totp/ACME:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=ACME
            otpauth://hotp/Globex:bob?secret=5OM4WOOGPLQEF6UGN3CPEOOLWU&algorithm=SHA256&digits=8&counter=5

            otpauth://totp/?secret=JBSWY3DPEHPK3PXP
        """.trimIndent().toByteArray()

        val accounts = (importer.parse(raw, null) as ImportOutcome.Success).accounts
        assertEquals(3, accounts.size)

        val totp = accounts[0]
        assertEquals("ACME", totp.issuer)
        assertEquals("alice@example.com", totp.accountName)
        assertEquals(OtpType.TOTP, totp.type)

        val hotp = accounts[1]
        assertEquals(OtpType.HOTP, hotp.type)
        assertEquals(OtpAlgorithm.SHA256, hotp.algorithm)
        assertEquals(8, hotp.digits)
        assertEquals(5L, hotp.counter)

        val noLabel = accounts[2]
        assertEquals("", noLabel.issuer)
        assertEquals("", noLabel.accountName)
    }

    @Test
    fun `parse skips invalid lines`() {
        val raw = """
            otpauth://totp/ACME:alice?secret=JBSWY3DPEHPK3PXP
            not a uri
            otpauth://garbage
            otpauth://totp/Globex:bob?secret=
        """.trimIndent().toByteArray()
        val accounts = (importer.parse(raw, null) as ImportOutcome.Success).accounts
        assertEquals(1, accounts.size)
    }
}
