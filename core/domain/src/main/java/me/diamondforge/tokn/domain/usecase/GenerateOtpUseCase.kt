package me.diamondforge.tokn.domain.usecase

import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
class GenerateOtpUseCase {

    operator fun invoke(account: OtpAccount, timeMillis: Long = System.currentTimeMillis()): OtpResult {
        val counter = when (account.type) {
            OtpType.TOTP -> timeMillis / 1000 / account.period
            OtpType.HOTP -> account.counter
        }
        // RFC 4226 caps digits at 8; anything larger overflows intPow10 (Int).
        val digits = account.digits.coerceIn(6, 8)
        val code = generateHotp(account.secret, counter, digits, account.algorithm)
        val remainingMillis = when (account.type) {
            OtpType.TOTP -> {
                val periodMillis = account.period * 1000L
                periodMillis - (timeMillis % periodMillis)
            }
            OtpType.HOTP -> -1L
        }
        return OtpResult(code, remainingMillis, account.period * 1000L)
    }

    private fun generateHotp(
        secret: String,
        counter: Long,
        digits: Int,
        algorithm: OtpAlgorithm,
    ): String {
        val key = base32Decode(secret.uppercase().trimEnd('='))
        val counterBytes = ByteArray(8)
        var c = counter
        for (i in 7 downTo 0) {
            counterBytes[i] = (c and 0xff).toByte()
            c = c shr 8
        }

        val hmacAlgorithm = when (algorithm) {
            OtpAlgorithm.SHA1 -> "HmacSHA1"
            OtpAlgorithm.SHA256 -> "HmacSHA256"
            OtpAlgorithm.SHA512 -> "HmacSHA512"
        }

        val mac = Mac.getInstance(hmacAlgorithm)
        mac.init(SecretKeySpec(key, hmacAlgorithm))
        val hmac = mac.doFinal(counterBytes)

        val offset = hmac.last().toInt() and 0x0f
        val binary = ((hmac[offset].toInt() and 0x7f) shl 24) or
            ((hmac[offset + 1].toInt() and 0xff) shl 16) or
            ((hmac[offset + 2].toInt() and 0xff) shl 8) or
            (hmac[offset + 3].toInt() and 0xff)

        val modulus = intPow10(digits)
        val otp = binary % modulus
        return otp.toString().padStart(digits, '0')
    }

    private fun intPow10(n: Int): Int {
        var result = 1
        repeat(n) { result *= 10 }
        return result
    }

    private fun base32Decode(encoded: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val clean = encoded.uppercase().replace(" ", "").replace("-", "").trimEnd('=')
        var bits = 0
        var bitsCount = 0
        val result = mutableListOf<Byte>()
        for (ch in clean) {
            val idx = alphabet.indexOf(ch)
            require(idx >= 0) { "Invalid Base32 character: '$ch'" }
            bits = (bits shl 5) or idx
            bitsCount += 5
            if (bitsCount >= 8) {
                bitsCount -= 8
                result.add((bits shr bitsCount and 0xff).toByte())
            }
        }
        return result.toByteArray()
    }
}

data class OtpResult(
    val code: String,
    val remainingMillis: Long,
    val periodMillis: Long,
)
