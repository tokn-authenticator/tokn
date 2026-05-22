package me.diamondforge.tokn.security

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class VaultPasswordManager @Inject constructor(
    private val keystoreManager: KeystoreManager,
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasPassword(): Boolean = prefs.contains(KEY_PASSWORD_SLOT)

    fun setup(password: String) {
        val passphrase = keystoreManager.getDatabasePassphrase()
        try {
            val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
            val derivedKey = deriveKey(password, salt)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, derivedKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(passphrase)
            val payload = "${b64(salt)}:${b64(iv)}:${b64(encrypted)}"
            prefs.edit {
                putString(KEY_PASSWORD_SLOT, keystoreManager.encrypt(payload.toByteArray()))
            }
        } finally {
            java.util.Arrays.fill(passphrase, 0)
        }
    }

    fun verify(password: String): Boolean = runCatching {
        val stored = prefs.getString(KEY_PASSWORD_SLOT, null) ?: return false
        val payload = keystoreManager.decrypt(stored).toString(Charsets.UTF_8)
        val parts = payload.split(":")
        val salt = Base64.decode(parts[0], Base64.NO_WRAP)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[2], Base64.NO_WRAP)
        val derivedKey = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, derivedKey, GCMParameterSpec(128, iv))
        val plaintext = cipher.doFinal(encrypted)
        java.util.Arrays.fill(plaintext, 0)
        true
    }.getOrDefault(false)

    fun clear() = prefs.edit { remove(KEY_PASSWORD_SLOT) }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        try {
            val keyBytes = factory.generateSecret(spec).encoded
            val key = SecretKeySpec(keyBytes, "AES")
            java.util.Arrays.fill(keyBytes, 0)
            return key
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    companion object {
        private const val PREFS_NAME = "vault_password_prefs"
        private const val KEY_PASSWORD_SLOT = "password_slot"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val SALT_LENGTH = 32
        private const val ITERATIONS = 310_000
    }
}
