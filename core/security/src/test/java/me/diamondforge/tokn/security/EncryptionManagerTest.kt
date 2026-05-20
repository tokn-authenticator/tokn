package me.diamondforge.tokn.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.AEADBadTagException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EncryptionManagerTest {

    private val manager = EncryptionManager()

    @Test
    fun `encrypt then decrypt yields original plaintext`() {
        val plaintext = "secret payload 🔒".toByteArray(Charsets.UTF_8)
        val payload = manager.encrypt(plaintext, password = "correct horse battery staple")
        val decrypted = manager.decrypt(payload, password = "correct horse battery staple")
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `wrong password throws AEADBadTagException`() {
        val payload = manager.encrypt("hello".toByteArray(), password = "right")
        assertThrows(AEADBadTagException::class.java) {
            manager.decrypt(payload, password = "wrong")
        }
    }

    @Test
    fun `tampered ciphertext fails AEAD tag check`() {
        val payload = manager.encrypt("hello".toByteArray(), password = "pw")
        // Flip a byte in the base64-decoded ciphertext via direct mutation of the string.
        val mutated = payload.copy(
            ciphertext = payload.ciphertext.flipFirstAlphaChar(),
        )
        assertThrows(AEADBadTagException::class.java) {
            manager.decrypt(mutated, password = "pw")
        }
    }

    @Test
    fun `salt and iv are unique per encryption`() {
        val a = manager.encrypt("same".toByteArray(), password = "pw")
        val b = manager.encrypt("same".toByteArray(), password = "pw")
        assertNotEquals(a.salt, b.salt)
        assertNotEquals(a.iv, b.iv)
        // ciphertext differs too, since IV is part of the GCM input
        assertNotEquals(a.ciphertext, b.ciphertext)
    }

    @Test
    fun `empty plaintext roundtrips`() {
        val payload = manager.encrypt(ByteArray(0), password = "pw")
        assertArrayEquals(ByteArray(0), manager.decrypt(payload, password = "pw"))
    }

    @Test
    fun `large plaintext roundtrips`() {
        val plaintext = ByteArray(256 * 1024) { (it % 251).toByte() }
        val payload = manager.encrypt(plaintext, password = "pw")
        assertArrayEquals(plaintext, manager.decrypt(payload, password = "pw"))
    }

    @Test
    fun `unicode password roundtrips`() {
        val pw = "pässwörd-中文-🔑"
        val payload = manager.encrypt("data".toByteArray(), password = pw)
        assertEquals("data", manager.decrypt(payload, password = pw).toString(Charsets.UTF_8))
    }

    /**
     * Flip the first alphabetic char (case-toggle) so we mutate a real Base64
     * symbol without risking padding chars. Length stays the same so the
     * decoded bytes have one different byte.
     */
    private fun String.flipFirstAlphaChar(): String {
        val idx = indexOfFirst { it.isLetter() }
        require(idx >= 0)
        val sb = StringBuilder(this)
        sb[idx] = if (this[idx].isUpperCase()) this[idx].lowercaseChar() else this[idx].uppercaseChar()
        return sb.toString()
    }
}
