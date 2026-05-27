package me.diamondforge.tokn.importer.crypto

import org.bouncycastle.crypto.generators.SCrypt
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object ScryptAesGcm {
    private const val KEY_LEN = 32
    private const val GCM_TAG_BITS = 128

    fun deriveKey(password: ByteArray, salt: ByteArray, n: Int, r: Int, p: Int): ByteArray =
        SCrypt.generate(password, salt, n, r, p, KEY_LEN)

    /**
     * Decrypts ciphertext that was produced with AES-256-GCM where the auth tag is stored
     * separately (Aegis-style). Throws [javax.crypto.AEADBadTagException] on wrong key/data.
     */
    fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        tag: ByteArray,
        ciphertext: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        return cipher.doFinal(ciphertext + tag)
    }

    /**
     * Encrypts with AES-256-GCM and splits the result into (ciphertext, tag). Used by tests.
     */
    fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray
    ): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        val out = cipher.doFinal(plaintext)
        val tagLen = GCM_TAG_BITS / 8
        val ciphertext = out.copyOfRange(0, out.size - tagLen)
        val tag = out.copyOfRange(out.size - tagLen, out.size)
        return ciphertext to tag
    }
}
