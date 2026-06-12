package me.diamondforge.tokn.importer.otpauth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

class Base32Test {

    @Test
    fun `rfc 4648 vectors encode without padding`() {
        assertEquals("", Base32.encode(ByteArray(0)))
        assertEquals("MY", Base32.encode("f".toByteArray()))
        assertEquals("MZXQ", Base32.encode("fo".toByteArray()))
        assertEquals("MZXW6", Base32.encode("foo".toByteArray()))
        assertEquals("MZXW6YQ", Base32.encode("foob".toByteArray()))
        assertEquals("MZXW6YTB", Base32.encode("fooba".toByteArray()))
        assertEquals("MZXW6YTBOI", Base32.encode("foobar".toByteArray()))
    }

    @Test
    fun `decode is the inverse of encode for rfc vectors`() {
        listOf("f", "fo", "foo", "foob", "fooba", "foobar").forEach {
            assertArrayEquals(it.toByteArray(), Base32.decode(Base32.encode(it.toByteArray())))
        }
    }

    @Test
    fun `decode tolerates lowercase, spaces and padding`() {
        val expected = "foobar".toByteArray()
        assertArrayEquals(expected, Base32.decode("mzxw6ytboi"))
        assertArrayEquals(expected, Base32.decode("MZXW 6YTB OI"))
        assertArrayEquals(expected, Base32.decode("MZXW6YTBOI======"))
    }

    @Test
    fun `empty input round-trips`() {
        assertEquals("", Base32.encode(ByteArray(0)))
        assertArrayEquals(ByteArray(0), Base32.decode(""))
        assertArrayEquals(ByteArray(0), Base32.decode("===="))
    }

    @Test
    fun `decode rejects characters outside the alphabet`() {
        assertThrows(IllegalArgumentException::class.java) { Base32.decode("01") }
        assertThrows(IllegalArgumentException::class.java) { Base32.decode("AAAA!") }
    }

    @Test
    fun `random byte arrays survive an encode-decode round trip`() {
        val rng = Random(42)
        repeat(200) {
            val bytes = ByteArray(rng.nextInt(0, 64)).also { rng.nextBytes(it) }
            assertArrayEquals(bytes, Base32.decode(Base32.encode(bytes)))
        }
    }
}
