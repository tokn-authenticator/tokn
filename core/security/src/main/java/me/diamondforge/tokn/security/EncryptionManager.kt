package me.diamondforge.tokn.security

import android.util.Base64
import me.diamondforge.tokn.security.crypto.Argon2KeyDeriver
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

const val KDF_ARGON2ID = "argon2id"
const val KDF_PBKDF2 = "pbkdf2"

@Singleton
class EncryptionManager @Inject constructor() {

    fun encrypt(plaintext: ByteArray, password: String): EncryptedPayload {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val key = deriveArgon2(password, salt)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext)
            return EncryptedPayload(
                ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
                iv = Base64.encodeToString(iv, Base64.NO_WRAP),
                salt = Base64.encodeToString(salt, Base64.NO_WRAP),
                kdf = KDF_ARGON2ID,
                memoryKib = Argon2KeyDeriver.DEFAULT_MEMORY_KIB,
                iterations = Argon2KeyDeriver.DEFAULT_ITERATIONS,
                parallelism = Argon2KeyDeriver.DEFAULT_PARALLELISM,
            )
        } finally {
            Arrays.fill(key, 0)
        }
    }

    fun decrypt(payload: EncryptedPayload, password: String): ByteArray {
        val salt = Base64.decode(payload.salt, Base64.NO_WRAP)
        val iv = Base64.decode(payload.iv, Base64.NO_WRAP)
        val ciphertext = Base64.decode(payload.ciphertext, Base64.NO_WRAP)
        val key = when (payload.kdf) {
            KDF_PBKDF2 -> derivePbkdf2(password, salt)
            else -> deriveArgon2(
                password, salt, payload.memoryKib, payload.iterations, payload.parallelism,
            )
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH * 8, iv))
            return cipher.doFinal(ciphertext)
        } finally {
            Arrays.fill(key, 0)
        }
    }

    private fun deriveArgon2(
        password: String,
        salt: ByteArray,
        memoryKib: Int = Argon2KeyDeriver.DEFAULT_MEMORY_KIB,
        iterations: Int = Argon2KeyDeriver.DEFAULT_ITERATIONS,
        parallelism: Int = Argon2KeyDeriver.DEFAULT_PARALLELISM,
    ): ByteArray = Argon2KeyDeriver.derive(
        password = password.toByteArray(Charsets.UTF_8),
        salt = salt,
        memoryKib = memoryKib,
        iterations = iterations,
        parallelism = parallelism,
    )

    private fun derivePbkdf2(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        try {
            return factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val SALT_LENGTH = 16
        private const val GCM_TAG_LENGTH = 16
        private const val PBKDF2_ITERATIONS = 310_000
        private const val KEY_LENGTH_BITS = 256
    }
}

data class EncryptedPayload(
    val ciphertext: String,
    val iv: String,
    val salt: String,
    val kdf: String = KDF_ARGON2ID,
    val memoryKib: Int = Argon2KeyDeriver.DEFAULT_MEMORY_KIB,
    val iterations: Int = Argon2KeyDeriver.DEFAULT_ITERATIONS,
    val parallelism: Int = Argon2KeyDeriver.DEFAULT_PARALLELISM,
) {
    fun writeTo(obj: JSONObject) {
        obj.put("ciphertext", ciphertext)
        obj.put("iv", iv)
        obj.put("salt", salt)
        obj.put("kdf", kdf)
        if (kdf == KDF_ARGON2ID) {
            obj.put("m", memoryKib)
            obj.put("t", iterations)
            obj.put("p", parallelism)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): EncryptedPayload = EncryptedPayload(
            ciphertext = obj.getString("ciphertext"),
            iv = obj.getString("iv"),
            salt = obj.getString("salt"),
            kdf = obj.optString("kdf", KDF_PBKDF2),
            memoryKib = obj.optInt("m", Argon2KeyDeriver.DEFAULT_MEMORY_KIB),
            iterations = obj.optInt("t", Argon2KeyDeriver.DEFAULT_ITERATIONS),
            parallelism = obj.optInt("p", Argon2KeyDeriver.DEFAULT_PARALLELISM),
        )
    }
}
