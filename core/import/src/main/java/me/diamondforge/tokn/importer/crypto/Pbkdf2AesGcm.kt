package me.diamondforge.tokn.importer.crypto

import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object Pbkdf2AesGcm {
    private const val GCM_TAG_BITS = 128

    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyBits: Int,
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyBits)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
    }

    /**
     * AES-GCM decrypt. The Java provider expects the 128-bit auth tag appended to the
     * ciphertext, which is how both 2FAS and Aegis-internal blobs are laid out:
     * `cipher.doFinal(plaintext)` is written verbatim, so the tag is already part of the
     * input here.
     */
    fun decrypt(key: ByteArray, iv: ByteArray, ciphertextWithTag: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return cipher.doFinal(ciphertextWithTag)
    }

    fun encrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return cipher.doFinal(plaintext)
    }
}
