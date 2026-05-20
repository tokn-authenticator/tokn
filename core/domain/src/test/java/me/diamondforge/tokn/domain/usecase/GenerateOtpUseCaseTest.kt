package me.diamondforge.tokn.domain.usecase

import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * RFC test vectors are the canonical safety net for any OTP library. If these
 * stop passing, every existing user is locked out of every account — there is
 * no "small bug" in this file. Vectors taken from:
 *  - RFC 6238 Appendix B (TOTP), using the per-erratum 20/32/64-byte keys.
 *  - RFC 4226 Appendix D (HOTP), 20-byte key, 6 digits.
 */
class GenerateOtpUseCaseTest {

    private val useCase = GenerateOtpUseCase()

    // ASCII "12345678901234567890" (20 bytes) -> Base32.
    private val sha1Secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    // ASCII "12345678901234567890123456789012" (32 bytes) -> Base32.
    private val sha256Secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA"
    // ASCII "1234567890123456789012345678901234567890123456789012345678901234" (64 bytes) -> Base32.
    private val sha512Secret =
        "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" +
            "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA"

    private fun totp(secret: String, algorithm: OtpAlgorithm, digits: Int = 8) = OtpAccount(
        issuer = "RFC", accountName = "test",
        secret = secret, algorithm = algorithm, digits = digits,
        period = 30, type = OtpType.TOTP,
    )

    private fun hotp(counter: Long) = OtpAccount(
        issuer = "RFC", accountName = "test",
        secret = sha1Secret, algorithm = OtpAlgorithm.SHA1, digits = 6,
        counter = counter, type = OtpType.HOTP,
    )

    @Test
    fun `RFC 6238 TOTP SHA1 vectors`() {
        // (unix seconds, expected 8-digit TOTP)
        val vectors = listOf(
            59L to "94287082",
            1111111109L to "07081804",
            1111111111L to "14050471",
            1234567890L to "89005924",
            2000000000L to "69279037",
            20000000000L to "65353130",
        )
        for ((time, expected) in vectors) {
            val result = useCase(totp(sha1Secret, OtpAlgorithm.SHA1), time * 1000)
            assertEquals("SHA1 @ t=$time", expected, result.code)
        }
    }

    @Test
    fun `RFC 6238 TOTP SHA256 vectors`() {
        val vectors = listOf(
            59L to "46119246",
            1111111109L to "68084774",
            1111111111L to "67062674",
            1234567890L to "91819424",
            2000000000L to "90698825",
            20000000000L to "77737706",
        )
        for ((time, expected) in vectors) {
            val result = useCase(totp(sha256Secret, OtpAlgorithm.SHA256), time * 1000)
            assertEquals("SHA256 @ t=$time", expected, result.code)
        }
    }

    @Test
    fun `RFC 6238 TOTP SHA512 vectors`() {
        val vectors = listOf(
            59L to "90693936",
            1111111109L to "25091201",
            1111111111L to "99943326",
            1234567890L to "93441116",
            2000000000L to "38618901",
            20000000000L to "47863826",
        )
        for ((time, expected) in vectors) {
            val result = useCase(totp(sha512Secret, OtpAlgorithm.SHA512), time * 1000)
            assertEquals("SHA512 @ t=$time", expected, result.code)
        }
    }

    @Test
    fun `RFC 4226 HOTP vectors`() {
        val expected = listOf(
            "755224", "287082", "359152", "969429", "338314",
            "254676", "287922", "162583", "399871", "520489",
        )
        for ((counter, value) in expected.withIndex()) {
            val result = useCase(hotp(counter.toLong()), timeMillis = 0L)
            assertEquals("counter=$counter", value, result.code)
        }
    }

    @Test
    fun `TOTP remainingMillis counts down within the period`() {
        // At T=0 of a 30s period we should have the full 30s remaining.
        val res0 = useCase(totp(sha1Secret, OtpAlgorithm.SHA1), 0L)
        assertEquals(30_000L, res0.remainingMillis)
        assertEquals(30_000L, res0.periodMillis)

        // 1ms into the period -> 29_999ms left.
        val res1 = useCase(totp(sha1Secret, OtpAlgorithm.SHA1), 1L)
        assertEquals(29_999L, res1.remainingMillis)

        // Just before the rollover.
        val res29 = useCase(totp(sha1Secret, OtpAlgorithm.SHA1), 29_999L)
        assertEquals(1L, res29.remainingMillis)
    }

    @Test
    fun `HOTP remainingMillis is sentinel -1`() {
        val result = useCase(hotp(0L), timeMillis = 123_456L)
        assertEquals(-1L, result.remainingMillis)
    }

    @Test
    fun `HOTP ignores time and uses counter`() {
        val a = useCase(hotp(0L), timeMillis = 0L)
        val b = useCase(hotp(0L), timeMillis = 99_999_999_999L)
        assertEquals(a.code, b.code)
    }

    @Test
    fun `digits coerced into 6 to 8`() {
        val tooFew = useCase(totp(sha1Secret, OtpAlgorithm.SHA1, digits = 4), 59_000L)
        val tooMany = useCase(totp(sha1Secret, OtpAlgorithm.SHA1, digits = 10), 59_000L)
        // Coerced low end behaves like digits=6.
        val six = useCase(totp(sha1Secret, OtpAlgorithm.SHA1, digits = 6), 59_000L)
        assertEquals(six.code, tooFew.code)
        // Coerced high end behaves like digits=8.
        val eight = useCase(totp(sha1Secret, OtpAlgorithm.SHA1, digits = 8), 59_000L)
        assertEquals(eight.code, tooMany.code)
        assertEquals(6, tooFew.code.length)
        assertEquals(8, tooMany.code.length)
    }

    @Test
    fun `code is zero-padded to digit length`() {
        // We can't easily force a specific value, but the assertion holds for
        // every RFC vector above (length == digits). Sanity-check the property.
        repeat(10) { counter ->
            val result = useCase(hotp(counter.toLong()), timeMillis = 0L)
            assertEquals(6, result.code.length)
        }
    }

    @Test
    fun `base32 secret tolerates lowercase, spaces, dashes and padding`() {
        val canonical = useCase(hotp(0L), timeMillis = 0L).code
        val variants = listOf(
            "gezdgnbvgy3tqojqgezdgnbvgy3tqojq",
            "GEZD GNBV GY3T QOJQ GEZD GNBV GY3T QOJQ",
            "GEZD-GNBV-GY3T-QOJQ-GEZD-GNBV-GY3T-QOJQ",
            "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ====",
        )
        for (variant in variants) {
            val result = useCase(hotp(0L).copy(secret = variant), timeMillis = 0L)
            assertEquals(variant, canonical, result.code)
        }
    }

    @Test
    fun `invalid base32 character throws`() {
        val account = hotp(0L).copy(secret = "GEZDGNBV!")
        assertThrows(IllegalArgumentException::class.java) {
            useCase(account, timeMillis = 0L)
        }
    }

    @Test
    fun `same input deterministic, different counters differ`() {
        val a = useCase(hotp(7L), 0L).code
        val b = useCase(hotp(7L), 0L).code
        val c = useCase(hotp(8L), 0L).code
        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
