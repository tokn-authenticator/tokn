package me.diamondforge.tokn.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
open class KeystoreManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val keystore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    open fun getDatabasePassphrase(): ByteArray {
        val stored = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (stored != null) {
            return decrypt(stored)
        }
        val passphrase = generateRandomBytes(32)
        val committed = prefs.edit().putString(KEY_DB_PASSPHRASE, encrypt(passphrase)).commit()
        check(committed) { "Failed to persist database passphrase" }
        return passphrase
    }

    @Synchronized
    open fun readLegacyPassphraseOrNull(): ByteArray? {
        val stored = prefs.getString(KEY_DB_PASSPHRASE, null) ?: return null
        return decrypt(stored)
    }

    @Synchronized
    open fun clearLegacyPassphrase() {
        prefs.edit(commit = true) { remove(KEY_DB_PASSPHRASE) }
    }

    fun generateMasterKey(): ByteArray = generateRandomBytes(32)

    open fun encrypt(data: ByteArray): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    open fun decrypt(encoded: String): ByteArray {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH * 8, iv))
        return cipher.doFinal(encrypted)
    }

    private fun getOrCreateKey(): SecretKey {
        if (!keystore.containsAlias(KEY_ALIAS)) {
            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            keyGen.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            keyGen.generateKey()
        }
        return (keystore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    open fun hasBiometricKey(): Boolean = keystore.containsAlias(BIOMETRIC_KEY_ALIAS)

    open fun deleteBiometricKey() {
        if (keystore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            keystore.deleteEntry(BIOMETRIC_KEY_ALIAS)
        }
    }

    open fun biometricEncryptCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateBiometricKey())
        return cipher
    }

    open fun biometricDecryptCipher(wrappedKey: String): Cipher {
        val combined = Base64.decode(wrappedKey, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keystore.getKey(BIOMETRIC_KEY_ALIAS, null) as SecretKey,
            GCMParameterSpec(GCM_TAG_LENGTH * 8, iv),
        )
        return cipher
    }

    open fun biometricWrap(authenticatedCipher: Cipher, data: ByteArray): String {
        val iv = authenticatedCipher.iv
        val encrypted = authenticatedCipher.doFinal(data)
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    open fun biometricUnwrap(authenticatedCipher: Cipher, wrappedKey: String): ByteArray {
        val combined = Base64.decode(wrappedKey, Base64.NO_WRAP)
        val body = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        return authenticatedCipher.doFinal(body)
    }

    private fun getOrCreateBiometricKey(): SecretKey {
        if (!keystore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val builder = KeyGenParameterSpec.Builder(
                BIOMETRIC_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                )
            }
            keyGen.init(builder.build())
            keyGen.generateKey()
        }
        return keystore.getKey(BIOMETRIC_KEY_ALIAS, null) as SecretKey
    }

    private fun generateRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "authenticator_db_key"
        private const val BIOMETRIC_KEY_ALIAS = "vault_biometric_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val PREFS_NAME = "authenticator_secure_prefs"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
    }
}
