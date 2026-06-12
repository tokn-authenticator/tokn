package me.diamondforge.tokn.importer.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HexCodecTest {

    @Test
    fun `decodes lower and upper case hex`() {
        assertArrayEquals(byteArrayOf(0x00, 0xFF.toByte(), 0x10, 0xAB.toByte()), HexCodec.decode("00ff10ab"))
        assertArrayEquals(byteArrayOf(0xAB.toByte(), 0xCD.toByte()), HexCodec.decode("ABCD"))
        assertArrayEquals(byteArrayOf(0xAb.toByte()), HexCodec.decode("aB"))
    }

    @Test
    fun `empty string decodes to empty array`() {
        assertArrayEquals(ByteArray(0), HexCodec.decode(""))
    }

    @Test
    fun `odd length is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { HexCodec.decode("abc") }
    }

    @Test
    fun `non hex characters are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { HexCodec.decode("zz") }
        assertThrows(IllegalArgumentException::class.java) { HexCodec.decode("0g") }
    }
}
