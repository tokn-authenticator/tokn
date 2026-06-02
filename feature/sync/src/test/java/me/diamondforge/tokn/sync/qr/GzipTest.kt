package me.diamondforge.tokn.sync.qr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GzipTest {

    @Test
    fun `compress then decompress roundtrips arbitrary bytes`() {
        val original = ByteArray(4096) { (it % 251).toByte() }
        val out = Gzip.decompress(Gzip.compress(original))
        assertArrayEquals(original, out)
    }

    @Test
    fun `compressed output is detected by looksGzipped`() {
        val compressed = Gzip.compress("payload".toByteArray())
        assertTrue(Gzip.looksGzipped(compressed))
    }

    @Test
    fun `plain JSON is not detected as gzipped`() {
        val plain = """{"accounts":[],"version":1}""".toByteArray(Charsets.UTF_8)
        assertFalse(Gzip.looksGzipped(plain))
    }

    @Test
    fun `looksGzipped is safe on short buffers`() {
        assertFalse(Gzip.looksGzipped(ByteArray(0)))
        assertFalse(Gzip.looksGzipped(byteArrayOf(0x1F)))
    }

    @Test
    fun `repetitive JSON-shaped data compresses significantly`() {
        // A vault export is JSON with repeating keys; this is the realistic
        // case the gzip step is trying to help, so we encode it as a test.
        val json = buildString {
            append("""{"accounts":[""")
            repeat(50) { i ->
                if (i > 0) append(',')
                append("""{"issuer":"Issuer$i","secret":"JBSWY3DPEHPK3PXP","digits":6,"period":30}""")
            }
            append("""],"version":1}""")
        }.toByteArray(Charsets.UTF_8)
        val compressed = Gzip.compress(json)
        assertTrue(
            "expected meaningful compression, got ${compressed.size}/${json.size}",
            compressed.size < json.size / 2,
        )
    }

    @Test
    fun `gzip magic bytes match RFC 1952`() {
        val compressed = Gzip.compress("x".toByteArray())
        assertEquals(0x1F.toByte(), compressed[0])
        assertEquals(0x8B.toByte(), compressed[1])
    }
}
