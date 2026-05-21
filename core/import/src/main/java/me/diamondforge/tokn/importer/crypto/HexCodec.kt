package me.diamondforge.tokn.importer.crypto

internal object HexCodec {
    fun decode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "hex must be even length" }
        return ByteArray(hex.length / 2) { i ->
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "invalid hex char" }
            ((hi shl 4) or lo).toByte()
        }
    }
}
