package me.diamondforge.tokn.sync.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

/**
 * SecureChannel is the layer that protects every byte after pairing.
 * These tests check that the wire format roundtrips, that frames outside
 * the legal size are refused, and that any tamper attempt fails GCM.
 */
class SecureChannelTest {

    private fun newKeys(): Pair<PairingKeys, PairingKeys> {
        val a = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val b = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return PairingKeys(sendKey = a, recvKey = b) to PairingKeys(sendKey = b, recvKey = a)
    }

    private fun newPipe(): Triple<DataOutputStream, () -> DataInputStream, ByteArrayOutputStream> {
        val out = ByteArrayOutputStream()
        val dataOut = DataOutputStream(out)
        val readBack = { DataInputStream(ByteArrayInputStream(out.toByteArray())) }
        return Triple(dataOut, readBack, out)
    }

    @Test
    fun `send then receive yields original plaintext`() {
        val (initKeys, respKeys) = newKeys()
        val (initOut, readInit, _) = newPipe()

        val initChan = SecureChannel(
            input = DataInputStream(ByteArrayInputStream(ByteArray(0))),
            output = initOut,
            keys = initKeys,
        )
        initChan.send("hello world".toByteArray())

        val respChan = SecureChannel(
            input = readInit(),
            output = DataOutputStream(ByteArrayOutputStream()),
            keys = respKeys,
        )
        assertEquals("hello world", respChan.receive().toString(Charsets.UTF_8))
    }

    @Test
    fun `bidirectional exchange works in both directions`() {
        val (initKeys, respKeys) = newKeys()
        val a2b = ByteArrayOutputStream()
        val b2a = ByteArrayOutputStream()

        // initiator sends to responder
        SecureChannel(
            DataInputStream(ByteArrayInputStream(ByteArray(0))),
            DataOutputStream(a2b),
            initKeys,
        ).send("ping".toByteArray())

        // responder receives, then replies
        val respChan = SecureChannel(
            DataInputStream(ByteArrayInputStream(a2b.toByteArray())),
            DataOutputStream(b2a),
            respKeys,
        )
        assertEquals("ping", respChan.receive().toString(Charsets.UTF_8))
        respChan.send("pong".toByteArray())

        // initiator receives the reply
        val initRecv = SecureChannel(
            DataInputStream(ByteArrayInputStream(b2a.toByteArray())),
            DataOutputStream(ByteArrayOutputStream()),
            initKeys,
        )
        assertEquals("pong", initRecv.receive().toString(Charsets.UTF_8))
    }

    @Test
    fun `consecutive sends use fresh nonces`() {
        val (initKeys, _) = newKeys()
        val (initOut, _, raw) = newPipe()
        val chan = SecureChannel(
            DataInputStream(ByteArrayInputStream(ByteArray(0))),
            initOut,
            initKeys,
        )
        chan.send("same".toByteArray())
        chan.send("same".toByteArray())
        val bytes = raw.toByteArray()

        // Each frame: int32 length || 12-byte nonce || ciphertext+tag.
        // We pull out the first 12 bytes after each length prefix.
        val len1 = readInt(bytes, 0)
        val nonce1 = bytes.copyOfRange(4, 4 + 12)
        val nonce2Start = 4 + len1 + 4
        val nonce2 = bytes.copyOfRange(nonce2Start, nonce2Start + 12)
        assertNotEquals(nonce1.toList(), nonce2.toList())
    }

    @Test
    fun `tampered ciphertext fails AEAD tag`() {
        val (initKeys, respKeys) = newKeys()
        val (initOut, _, raw) = newPipe()
        SecureChannel(
            DataInputStream(ByteArrayInputStream(ByteArray(0))),
            initOut,
            initKeys,
        ).send("payload".toByteArray())

        // Flip a byte in the ciphertext portion (past the 4-byte length and 12-byte nonce).
        val bytes = raw.toByteArray()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()

        val respChan = SecureChannel(
            DataInputStream(ByteArrayInputStream(bytes)),
            DataOutputStream(ByteArrayOutputStream()),
            respKeys,
        )
        assertThrows(AEADBadTagException::class.java) { respChan.receive() }
    }

    @Test
    fun `frame larger than MAX_FRAME_SIZE is rejected`() {
        val (_, respKeys) = newKeys()
        val raw = ByteArrayOutputStream()
        DataOutputStream(raw).writeInt(SecureChannel.MAX_FRAME_SIZE + 1)
        val respChan = SecureChannel(
            DataInputStream(ByteArrayInputStream(raw.toByteArray())),
            DataOutputStream(ByteArrayOutputStream()),
            respKeys,
        )
        val ex = assertThrows(IllegalArgumentException::class.java) { respChan.receive() }
        assertTrue(
            "expected bad frame error, got ${ex.message}",
            ex.message?.contains("bad frame size") == true
        )
    }

    @Test
    fun `frame smaller than nonce is rejected`() {
        val (_, respKeys) = newKeys()
        val raw = ByteArrayOutputStream()
        DataOutputStream(raw).writeInt(12) // exactly nonce length, no room for ciphertext+tag
        val respChan = SecureChannel(
            DataInputStream(ByteArrayInputStream(raw.toByteArray())),
            DataOutputStream(ByteArrayOutputStream()),
            respKeys,
        )
        assertThrows(IllegalArgumentException::class.java) { respChan.receive() }
    }

    @Test
    fun `mismatched keys cannot decrypt`() {
        val (initKeys, _) = newKeys()
        val (_, otherResp) = newKeys() // unrelated peer
        val raw = ByteArrayOutputStream()
        SecureChannel(
            DataInputStream(ByteArrayInputStream(ByteArray(0))),
            DataOutputStream(raw),
            initKeys,
        ).send("hi".toByteArray())

        val respChan = SecureChannel(
            DataInputStream(ByteArrayInputStream(raw.toByteArray())),
            DataOutputStream(ByteArrayOutputStream()),
            otherResp,
        )
        assertThrows(AEADBadTagException::class.java) { respChan.receive() }
    }

    @Test
    fun `truncated frame throws IOException`() {
        val (initKeys, respKeys) = newKeys()
        val raw = ByteArrayOutputStream()
        SecureChannel(
            DataInputStream(ByteArrayInputStream(ByteArray(0))),
            DataOutputStream(raw),
            initKeys,
        ).send("hello".toByteArray())

        val truncated = raw.toByteArray().copyOf(8) // cut mid-frame
        val respChan = SecureChannel(
            DataInputStream(ByteArrayInputStream(truncated)),
            DataOutputStream(ByteArrayOutputStream()),
            respKeys,
        )
        assertThrows(IOException::class.java) { respChan.receive() }
    }

    @Test
    fun `large payload roundtrips`() {
        val (initKeys, respKeys) = newKeys()
        val payload = ByteArray(200_000) { (it % 251).toByte() }
        val raw = ByteArrayOutputStream()
        SecureChannel(
            DataInputStream(ByteArrayInputStream(ByteArray(0))),
            DataOutputStream(raw),
            initKeys,
        ).send(payload)

        val respChan = SecureChannel(
            DataInputStream(ByteArrayInputStream(raw.toByteArray())),
            DataOutputStream(ByteArrayOutputStream()),
            respKeys,
        )
        assertArrayEquals(payload, respChan.receive())
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
                ((bytes[offset + 1].toInt() and 0xff) shl 16) or
                ((bytes[offset + 2].toInt() and 0xff) shl 8) or
                (bytes[offset + 3].toInt() and 0xff)
}
