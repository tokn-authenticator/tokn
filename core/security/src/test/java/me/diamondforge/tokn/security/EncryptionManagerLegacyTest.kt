package me.diamondforge.tokn.security

import android.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EncryptionManagerLegacyTest {

    private val manager = EncryptionManager()

    @Test
    fun `decrypts a legacy PBKDF2 payload`() {
        val password = "correct horse battery staple"
        val plaintext = "legacy vault contents 🔐".toByteArray()
        val payload = makeLegacyPayload(plaintext, password)

        assertArrayEquals(plaintext, manager.decrypt(payload, password))
    }

    @Test
    fun `old wrapper json without kdf is read as PBKDF2 and decrypts`() {
        val password = "pw"
        val plaintext = "data".toByteArray()
        val legacy = makeLegacyPayload(plaintext, password)

        // Emulate an on-disk backup wrapper from the previous version: only the
        // three original fields, no kdf metadata.
        val wrapper = JSONObject().apply {
            put("ciphertext", legacy.ciphertext)
            put("iv", legacy.iv)
            put("salt", legacy.salt)
        }

        val parsed = EncryptedPayload.fromJson(wrapper)
        assertEquals(KDF_PBKDF2, parsed.kdf)
        assertEquals("data", manager.decrypt(parsed, password).toString(Charsets.UTF_8))
    }

    @Test
    fun `new argon2 payload survives a writeTo and fromJson round trip`() {
        val payload = manager.encrypt("hello".toByteArray(), "pw")
        val obj = JSONObject().apply { payload.writeTo(this) }

        val parsed = EncryptedPayload.fromJson(obj)
        assertEquals(KDF_ARGON2ID, parsed.kdf)
        assertEquals("hello", manager.decrypt(parsed, "pw").toString(Charsets.UTF_8))
    }

    private fun makeLegacyPayload(plaintext: ByteArray, password: String): EncryptedPayload {
        val salt = ByteArray(16) { (it + 3).toByte() }
        val spec = PBEKeySpec(password.toCharArray(), salt, 310_000, 256)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedPayload(
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            kdf = KDF_PBKDF2,
        )
    }
}
