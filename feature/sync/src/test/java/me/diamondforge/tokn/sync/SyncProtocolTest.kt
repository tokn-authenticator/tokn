package me.diamondforge.tokn.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProtocolTest {

    @Test
    fun `hello roundtrip preserves fields`() {
        val bytes = SyncProtocol.makeHello("1.2.2", 5L)
        val parsed = SyncProtocol.parseHello(bytes)
        assertEquals(SyncProtocol.VERSION, parsed.protocol)
        assertEquals("1.2.2", parsed.app)
        assertEquals(5L, parsed.build)
    }

    @Test
    fun `same protocol version is compatible`() {
        val hello = SyncProtocol.Hello(SyncProtocol.VERSION, "x", 1L)
        assertTrue(SyncProtocol.isCompatible(hello))
    }

    @Test
    fun `older protocol is incompatible`() {
        val hello = SyncProtocol.Hello(SyncProtocol.VERSION - 1, "x", 1L)
        assertFalse(SyncProtocol.isCompatible(hello))
    }

    @Test
    fun `newer protocol is incompatible`() {
        val hello = SyncProtocol.Hello(SyncProtocol.VERSION + 1, "x", 1L)
        assertFalse(SyncProtocol.isCompatible(hello))
    }

    @Test
    fun `missing optional fields parse with safe defaults`() {
        val raw = """{"protocol": ${SyncProtocol.VERSION}}""".toByteArray(Charsets.UTF_8)
        val parsed = SyncProtocol.parseHello(raw)
        assertEquals(SyncProtocol.VERSION, parsed.protocol)
        assertEquals("?", parsed.app)
        assertEquals(0L, parsed.build)
    }
}
