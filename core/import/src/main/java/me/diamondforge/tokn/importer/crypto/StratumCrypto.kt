package me.diamondforge.tokn.importer.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object StratumCrypto {

    // Strong format layout: [16 magic][16 salt][12 iv][ciphertext+gcm-tag]
    fun decryptStrong(raw: ByteArray, password: String): ByteArray {
        val salt = raw.copyOfRange(16, 32)
        val iv = raw.copyOfRange(32, 44)
        val ciphertextWithTag = raw.copyOfRange(44, raw.size)
        val key = deriveArgon2id(password.toByteArray(Charsets.UTF_8), salt)
        return Pbkdf2AesGcm.decrypt(key, iv, ciphertextWithTag)
    }

    // Legacy format layout: [16 magic][20 salt][16 iv][ciphertext]
    fun decryptLegacy(raw: ByteArray, password: String): ByteArray {
        val salt = raw.copyOfRange(16, 36)
        val iv = raw.copyOfRange(36, 52)
        val ciphertext = raw.copyOfRange(52, raw.size)
        val key = derivePbkdf2Sha1(password.toCharArray(), salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(ciphertext)
    }

    private fun deriveArgon2id(password: ByteArray, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withParallelism(4)
            .withMemoryAsKB(65536)
            .withIterations(3)
            .build()
        val gen = Argon2BytesGenerator()
        gen.init(params)
        val key = ByteArray(32)
        gen.generateBytes(password, key)
        return key
    }

    private fun derivePbkdf2Sha1(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, 64_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(spec)
            .encoded
    }
}
