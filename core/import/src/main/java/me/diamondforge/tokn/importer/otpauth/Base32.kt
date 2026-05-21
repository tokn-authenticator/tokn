package me.diamondforge.tokn.importer.otpauth

/**
 * RFC 4648 Base32 encoder/decoder. The OTP secret format uses base32 so we need to
 * round-trip raw secret bytes (e.g. from the Google Authenticator migration protobuf) to
 * the canonical string representation that the rest of the app stores.
 */
internal object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val DECODE = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { idx, c -> table[c.code] = idx }
    }

    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val idx = (buffer shr (bitsLeft - 5)) and 0x1F
                sb.append(ALPHABET[idx])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            val idx = (buffer shl (5 - bitsLeft)) and 0x1F
            sb.append(ALPHABET[idx])
        }
        return sb.toString()
    }

    fun decode(text: String): ByteArray {
        val clean = text.uppercase().trimEnd('=').replace(" ", "")
        if (clean.isEmpty()) return ByteArray(0)
        val out = ByteArray(clean.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        var pos = 0
        for (c in clean) {
            val v = DECODE.getOrElse(c.code) { -1 }
            require(v >= 0) { "invalid base32 char $c" }
            buffer = (buffer shl 5) or v
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out[pos++] = (buffer shr (bitsLeft - 8) and 0xFF).toByte()
                bitsLeft -= 8
            }
        }
        return out.copyOf(pos)
    }
}
