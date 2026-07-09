package me.diamondforge.tokn.importer.otpauth

import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OtpAuthUriBuilderTest {

    private fun assertRoundTrips(account: OtpAccount) {
        val parsed = OtpAuthParser.parse(account.toOtpAuthUri())
            ?: error("built URI failed to parse: ${account.toOtpAuthUri()}")
        assertEquals(account.issuer, parsed.issuer)
        assertEquals(account.accountName, parsed.accountName)
        assertEquals(account.secret, parsed.secret)
        assertEquals(account.algorithm, parsed.algorithm)
        assertEquals(account.digits, parsed.digits)
        assertEquals(account.type, parsed.type)
        if (account.type == OtpType.TOTP) {
            assertEquals(account.period, parsed.period)
        } else {
            assertEquals(account.counter, parsed.counter)
        }
    }

    @Test
    fun `plain TOTP round-trips`() {
        assertRoundTrips(
            OtpAccount(issuer = "ACME", accountName = "alice@example.com", secret = "JBSWY3DPEHPK3PXP"),
        )
    }

    @Test
    fun `non-default TOTP round-trips`() {
        assertRoundTrips(
            OtpAccount(
                issuer = "toknauth",
                accountName = "bob",
                secret = "5OM4WOOGPLQEF6UGN3CPEOOLWU",
                algorithm = OtpAlgorithm.SHA512,
                digits = 8,
                period = 60,
            ),
        )
    }

    @Test
    fun `HOTP round-trips with counter`() {
        assertRoundTrips(
            OtpAccount(
                issuer = "toknuser",
                accountName = "bob",
                secret = "JBSWY3DPEHPK3PXP",
                type = OtpType.HOTP,
                counter = 5L,
            ),
        )
    }

    @Test
    fun `label with reserved characters round-trips`() {
        assertRoundTrips(
            OtpAccount(
                issuer = "Acme / Corp & Co?",
                accountName = "user name#with=chars",
                secret = "JBSWY3DPEHPK3PXP",
            ),
        )
    }

    @Test
    fun `defaults are omitted from the query`() {
        val uri = OtpAccount(
            issuer = "ACME",
            accountName = "alice",
            secret = "JBSWY3DPEHPK3PXP",
        ).toOtpAuthUri()
        assertTrue(uri.startsWith("otpauth://totp/"))
        assertTrue("algorithm" !in uri)
        assertTrue("digits" !in uri)
        assertTrue("period" !in uri)
    }
}
