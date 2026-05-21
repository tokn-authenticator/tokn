package me.diamondforge.tokn.importer.otpauth

/**
 * Minimal protobuf wire-format reader. Supports the four wire types we care about for the
 * Google Authenticator `MigrationPayload` schema: varint (0), 64-bit fixed (1),
 * length-delimited (2), and 32-bit fixed (5). Group wire types (3/4) are deprecated and not
 * emitted by Google Authenticator, but we still skip them gracefully.
 */
internal class ProtoReader(private val buf: ByteArray, private val end: Int = buf.size) {
    private var pos = 0

    fun hasMore(): Boolean = pos < end

    fun readTag(): Int = readVarint().toInt()

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            check(pos < end) { "unexpected EOF in varint" }
            val b = buf[pos++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            check(shift < 64) { "varint overflow" }
        }
    }

    fun readBytes(): ByteArray {
        val len = readVarint().toInt()
        check(len in 0..(end - pos)) { "length-delimited overrun" }
        val out = buf.copyOfRange(pos, pos + len)
        pos += len
        return out
    }

    fun readString(): String = readBytes().toString(Charsets.UTF_8)

    /** Skip the value for a tag whose wire type was read out of [readTag]. */
    fun skipValue(tag: Int) {
        when (tag and 0x7) {
            0 -> readVarint()
            1 -> pos += 8
            2 -> { val len = readVarint().toInt(); pos += len }
            5 -> pos += 4
            else -> error("unsupported wire type ${tag and 0x7}")
        }
    }

    companion object {
        fun fieldNumber(tag: Int): Int = tag ushr 3
        fun wireType(tag: Int): Int = tag and 0x7
    }
}
