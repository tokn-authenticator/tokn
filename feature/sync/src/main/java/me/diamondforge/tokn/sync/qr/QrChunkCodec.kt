package me.diamondforge.tokn.sync.qr

import android.util.Base64

/**
 * Splits an opaque byte payload across multiple QR frames so a sender can
 * cycle through them as an animated QR. Each frame text looks like
 * `T1|<seq>|<total>|<base64-chunk>`.
 */
object QrChunkCodec {
    private const val MAGIC = "T1"
    private const val CHUNK_BYTES = 512

    data class Chunk(val seq: Int, val total: Int, val data: ByteArray) {
        override fun equals(other: Any?) =
            other is Chunk && seq == other.seq && total == other.total && data.contentEquals(other.data)

        override fun hashCode(): Int = seq * 31 + total
    }

    fun encode(payload: ByteArray): List<String> {
        val total = (payload.size + CHUNK_BYTES - 1) / CHUNK_BYTES
        if (total == 0) return listOf("$MAGIC|0|1|")
        return (0 until total).map { i ->
            val start = i * CHUNK_BYTES
            val end = minOf(start + CHUNK_BYTES, payload.size)
            val chunk = payload.copyOfRange(start, end)
            val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP or Base64.URL_SAFE)
            "$MAGIC|$i|$total|$b64"
        }
    }

    fun parseFrame(raw: String): Chunk? {
        val parts = raw.split('|', limit = 4)
        if (parts.size != 4 || parts[0] != MAGIC) return null
        val seq = parts[1].toIntOrNull() ?: return null
        val total = parts[2].toIntOrNull() ?: return null
        if (seq < 0 || total <= 0 || seq >= total) return null
        val data = runCatching { Base64.decode(parts[3], Base64.NO_WRAP or Base64.URL_SAFE) }
            .getOrNull() ?: return null
        return Chunk(seq, total, data)
    }

    fun assemble(chunks: Map<Int, ByteArray>, total: Int): ByteArray? {
        if (chunks.size != total) return null
        var size = 0
        for (i in 0 until total) size += (chunks[i] ?: return null).size
        val out = ByteArray(size)
        var pos = 0
        for (i in 0 until total) {
            val c = chunks.getValue(i)
            System.arraycopy(c, 0, out, pos, c.size)
            pos += c.size
        }
        return out
    }
}
