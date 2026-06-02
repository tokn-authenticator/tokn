package me.diamondforge.tokn.sync.qr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QrChunkCodecTest {

    @Test
    fun `single-chunk payload roundtrips`() {
        val payload = "hello".toByteArray()
        val frames = QrChunkCodec.encode(payload)
        assertEquals(1, frames.size)
        val parsed = QrChunkCodec.parseFrame(frames[0])!!
        assertEquals(0, parsed.seq)
        assertEquals(1, parsed.total)
        val assembled = QrChunkCodec.assemble(mapOf(0 to parsed.data), total = 1)
        assertArrayEquals(payload, assembled)
    }

    @Test
    fun `multi-chunk payload is split and reassembled`() {
        val payload = ByteArray(1300) { (it % 251).toByte() }
        val frames = QrChunkCodec.encode(payload)
        assertEquals(3, frames.size)

        val chunks = frames.mapNotNull(QrChunkCodec::parseFrame)
            .associate { it.seq to it.data }
        val assembled = QrChunkCodec.assemble(chunks, total = 3)
        assertArrayEquals(payload, assembled)
    }

    @Test
    fun `assemble works with frames out of order`() {
        val payload = ByteArray(1300) { (it % 251).toByte() }
        val frames = QrChunkCodec.encode(payload).shuffled().reversed()
        val chunks = frames.mapNotNull(QrChunkCodec::parseFrame)
            .associate { it.seq to it.data }
        assertArrayEquals(payload, QrChunkCodec.assemble(chunks, total = frames.size))
    }

    @Test
    fun `empty payload encodes as one empty frame`() {
        val frames = QrChunkCodec.encode(ByteArray(0))
        assertEquals(1, frames.size)
        assertEquals("T1|0|1|", frames[0])
    }

    @Test
    fun `frame with wrong magic is rejected`() {
        assertNull(QrChunkCodec.parseFrame("X1|0|1|aGVsbG8="))
    }

    @Test
    fun `frame with malformed numbers is rejected`() {
        assertNull(QrChunkCodec.parseFrame("T1|abc|1|aGVsbG8="))
        assertNull(QrChunkCodec.parseFrame("T1|0|xyz|aGVsbG8="))
    }

    @Test
    fun `frame with seq out of range is rejected`() {
        assertNull(QrChunkCodec.parseFrame("T1|5|3|aGVsbG8="))
        assertNull(QrChunkCodec.parseFrame("T1|-1|3|aGVsbG8="))
        assertNull(QrChunkCodec.parseFrame("T1|0|0|"))
    }

    @Test
    fun `frame with corrupt base64 is rejected`() {
        assertNull(QrChunkCodec.parseFrame("T1|0|1|@@@notbase64@@@"))
    }

    @Test
    fun `frame with too few separators is rejected`() {
        assertNull(QrChunkCodec.parseFrame("T1|0|1"))
        assertNull(QrChunkCodec.parseFrame("plaintext"))
    }

    @Test
    fun `assemble returns null when a chunk is missing`() {
        val payload = ByteArray(1300) { it.toByte() }
        val frames = QrChunkCodec.encode(payload)
        val chunks = frames.mapNotNull(QrChunkCodec::parseFrame)
            .associate { it.seq to it.data }
            .filterKeys { it != 1 } // drop middle chunk
        assertNull(QrChunkCodec.assemble(chunks, total = 3))
    }

    @Test
    fun `assemble returns null when size mismatches total`() {
        val payload = "abc".toByteArray()
        val frames = QrChunkCodec.encode(payload)
        val one = QrChunkCodec.parseFrame(frames[0])!!
        // Claim there should be 2 chunks but only supply 1.
        assertNull(QrChunkCodec.assemble(mapOf(0 to one.data), total = 2))
    }

    @Test
    fun `parsing round-trips through encode-parseFrame for sizes around chunk boundary`() {
        for (size in listOf(1, 511, 512, 513, 1023, 1024, 1025)) {
            val payload = ByteArray(size) { (it % 251).toByte() }
            val frames = QrChunkCodec.encode(payload)
            val chunks = frames.mapNotNull(QrChunkCodec::parseFrame)
                .associate { it.seq to it.data }
            val assembled = QrChunkCodec.assemble(chunks, total = frames.size)
            assertNotNull("size=$size produced null", assembled)
            assertArrayEquals("size=$size", payload, assembled)
        }
    }
}
