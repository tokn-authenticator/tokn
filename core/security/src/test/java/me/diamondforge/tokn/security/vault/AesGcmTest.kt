package me.diamondforge.tokn.security.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class AesGcmTest {

    private val key = ByteArray(32) { it.toByte() }

    @Test
    fun `round trips arbitrary plaintext`() {
        val plaintext = "the vault master key".toByteArray()
        val blob = AesGcm.encrypt(key, plaintext)
        assertArrayEquals(plaintext, AesGcm.decrypt(key, blob))
    }

    @Test
    fun `each encryption uses a fresh iv`() {
        val plaintext = "same input".toByteArray()
        val a = AesGcm.encrypt(key, plaintext)
        val b = AesGcm.encrypt(key, plaintext)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `a different key fails authentication`() {
        val blob = AesGcm.encrypt(key, "secret".toByteArray())
        val wrong = ByteArray(32) { (it + 1).toByte() }
        assertThrows(AEADBadTagException::class.java) { AesGcm.decrypt(wrong, blob) }
    }

    @Test
    fun `tampering with the ciphertext fails authentication`() {
        val blob = AesGcm.encrypt(key, "secret".toByteArray())
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()
        assertThrows(AEADBadTagException::class.java) { AesGcm.decrypt(key, blob) }
    }

    @Test
    fun `a blob shorter than the iv is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { AesGcm.decrypt(key, ByteArray(8)) }
    }
}
