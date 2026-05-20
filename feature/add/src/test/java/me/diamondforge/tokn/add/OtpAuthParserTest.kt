package me.diamondforge.tokn.add

import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OtpAuthParserTest {

    @Test
    fun `minimal totp URI parses with defaults`() {
        val result = OtpAuthParser.parse(
            "otpauth://totp/Example:alice@example.com?secret=JBSWY3DPEHPK3PXP",
        )
        assertEquals("Example", result.issuer)
        assertEquals("alice@example.com", result.accountName)
        assertEquals("JBSWY3DPEHPK3PXP", result.secret)
        assertEquals(OtpAlgorithm.SHA1, result.algorithm)
        assertEquals(6, result.digits)
        assertEquals(30, result.period)
        assertEquals(OtpType.TOTP, result.type)
    }

    @Test
    fun `issuer query parameter wins over label issuer`() {
        val result = OtpAuthParser.parse(
            "otpauth://totp/Wrong:alice@example.com?secret=AAAA&issuer=Right",
        )
        assertEquals("Right", result.issuer)
        assertEquals("alice@example.com", result.accountName)
    }

    @Test
    fun `label without colon becomes account name with empty issuer`() {
        val result = OtpAuthParser.parse(
            "otpauth://totp/alice?secret=AAAA",
        )
        assertEquals("", result.issuer)
        assertEquals("alice", result.accountName)
    }

    @Test
    fun `hotp scheme detected`() {
        val result = OtpAuthParser.parse(
            "otpauth://hotp/X:y?secret=AAAA&counter=5",
        )
        assertEquals(OtpType.HOTP, result.type)
    }

    @Test
    fun `unknown otp host falls back to totp`() {
        val result = OtpAuthParser.parse(
            "otpauth://wonky/X:y?secret=AAAA",
        )
        assertEquals(OtpType.TOTP, result.type)
    }

    @Test
    fun `algorithm parameter is case insensitive`() {
        val sha256 = OtpAuthParser.parse("otpauth://totp/x?secret=A&algorithm=sha256")
        val sha512 = OtpAuthParser.parse("otpauth://totp/x?secret=A&algorithm=SHA512")
        val unknown = OtpAuthParser.parse("otpauth://totp/x?secret=A&algorithm=MD5")
        assertEquals(OtpAlgorithm.SHA256, sha256.algorithm)
        assertEquals(OtpAlgorithm.SHA512, sha512.algorithm)
        assertEquals(OtpAlgorithm.SHA1, unknown.algorithm)
    }

    @Test
    fun `digits and period override defaults`() {
        val result = OtpAuthParser.parse(
            "otpauth://totp/x?secret=A&digits=8&period=60",
        )
        assertEquals(8, result.digits)
        assertEquals(60, result.period)
    }

    @Test
    fun `non-integer digits and period fall back to defaults`() {
        val result = OtpAuthParser.parse(
            "otpauth://totp/x?secret=A&digits=abc&period=def",
        )
        assertEquals(6, result.digits)
        assertEquals(30, result.period)
    }

    @Test
    fun `URL-encoded colon in label is decoded for issuer split`() {
        val result = OtpAuthParser.parse(
            "otpauth://totp/Example%3Aalice%40example.com?secret=AAAA",
        )
        assertEquals("Example", result.issuer)
        assertEquals("alice@example.com", result.accountName)
    }

    @Test
    fun `wrong scheme throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            OtpAuthParser.parse("https://example.com/totp/x?secret=A")
        }
    }

    @Test
    fun `missing secret throws`() {
        assertThrows(IllegalStateException::class.java) {
            OtpAuthParser.parse("otpauth://totp/x?digits=6")
        }
    }

    @Test
    fun `spaces in label are preserved`() {
        val result = OtpAuthParser.parse(
            "otpauth://totp/My%20Issuer:my%20account?secret=AAAA",
        )
        assertEquals("My Issuer", result.issuer)
        assertEquals("my account", result.accountName)
    }
}
