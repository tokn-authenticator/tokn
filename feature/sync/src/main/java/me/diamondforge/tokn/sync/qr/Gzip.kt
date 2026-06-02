package me.diamondforge.tokn.sync.qr

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal object Gzip {
    private const val MAGIC_0: Byte = 0x1F
    private const val MAGIC_1: Byte = 0x8B.toByte()

    fun compress(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size)
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    fun decompress(data: ByteArray): ByteArray {
        GZIPInputStream(ByteArrayInputStream(data)).use { return it.readBytes() }
    }

    fun looksGzipped(data: ByteArray): Boolean =
        data.size >= 2 && data[0] == MAGIC_0 && data[1] == MAGIC_1
}
