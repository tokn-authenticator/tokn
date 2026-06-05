package me.diamondforge.tokn.security.vault

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import me.diamondforge.tokn.security.KeystoreManager
import me.diamondforge.tokn.security.VaultPasswordManager
import me.diamondforge.tokn.security.crypto.Argon2KeyDeriver
import java.security.SecureRandom
import java.util.Arrays
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class VaultManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keystoreManager: KeystoreManager,
    private val session: VaultSession,
    private val legacyPassword: VaultPasswordManager,
) {
    private val slotStore = SlotStore(context)

    @Synchronized
    open fun initIfNeeded() {
        if (slotStore.isInitialized()) return

        val masterKey = keystoreManager.readLegacyPassphraseOrNull()
            ?: keystoreManager.generateMasterKey()
        try {
            slotStore.save(listOf(noAuthSlot(masterKey)))
            if (legacyPassword.hasPassword()) {
                slotStore.legacyPasswordPresent = true
            }
            keystoreManager.clearLegacyPassphrase()
        } finally {
            Arrays.fill(masterKey, 0)
        }
    }

    open fun hasPassword(): Boolean = slotStore.load().any { it is PasswordSlot }

    open fun hasPasswordOrLegacy(): Boolean = hasPassword() || slotStore.legacyPasswordPresent

    open fun hasBiometric(): Boolean =
        slotStore.load().any { it is KeystoreSlot && it.requiresAuth }

    open fun verifyPassword(password: String): Boolean {
        val slots = slotStore.load()
        val passwordSlot = slots.firstOrNull { it is PasswordSlot } as? PasswordSlot
        if (passwordSlot != null) {
            val masterKey = unwrapPasswordSlot(passwordSlot, password) ?: return false
            Arrays.fill(masterKey, 0)
            return true
        }
        if (slotStore.legacyPasswordPresent) return legacyPassword.verify(password)
        return false
    }

    open fun requiresUnlock(): Boolean {
        val slots = slotStore.load()
        return slots.any { it is PasswordSlot } ||
            slots.any { it is KeystoreSlot && it.requiresAuth } ||
            slotStore.legacyPasswordPresent
    }

    open fun canBiometricUnlock(): Boolean = hasBiometric() && keystoreManager.hasBiometricKey()

    open fun canProvisionBiometricFromKeystore(): Boolean =
        !hasBiometric() && slotStore.load().any { it is KeystoreSlot && !it.requiresAuth }

    open fun needsRootOfTrustUpgrade(): Boolean {
        val slots = slotStore.load()
        val hasNoAuth = slots.any { it is KeystoreSlot && !it.requiresAuth }
        return hasNoAuth && (slots.any { it is PasswordSlot } || slotStore.legacyPasswordPresent)
    }

    @Synchronized
    open fun tryAutoUnlock(): Boolean {
        if (session.isUnlocked) return true
        val slot = slotStore.load().firstOrNull { it is KeystoreSlot && !it.requiresAuth }
            as? KeystoreSlot ?: return false
        val masterKey = keystoreManager.decrypt(slot.wrappedKey)
        try {
            session.unlock(masterKey)
        } finally {
            Arrays.fill(masterKey, 0)
        }
        return true
    }

    @Synchronized
    open fun unlockWithPassword(password: String): Boolean {
        val slots = slotStore.load()
        val passwordSlot = slots.firstOrNull { it is PasswordSlot } as? PasswordSlot

        if (passwordSlot != null) {
            val masterKey = unwrapPasswordSlot(passwordSlot, password) ?: return false
            try {
                session.unlock(masterKey)
            } finally {
                Arrays.fill(masterKey, 0)
            }
            return true
        }

        if (slotStore.legacyPasswordPresent) {
            return upgradeLegacyPassword(password, slots)
        }

        return false
    }

    private fun upgradeLegacyPassword(password: String, slots: List<Slot>): Boolean {
        if (!legacyPassword.verify(password)) return false

        val noAuth = slots.firstOrNull { it is KeystoreSlot && !it.requiresAuth } as? KeystoreSlot
            ?: return false
        val masterKey = keystoreManager.decrypt(noAuth.wrappedKey)
        try {
            val upgraded = slots.filterNot { it === noAuth } + passwordSlotFrom(password, masterKey)
            slotStore.save(upgraded)
            slotStore.legacyPasswordPresent = false
            legacyPassword.clear()
            session.unlock(masterKey)
        } finally {
            Arrays.fill(masterKey, 0)
        }
        return true
    }

    open fun biometricEncryptCipher(): Cipher = keystoreManager.biometricEncryptCipher()

    open fun biometricDecryptCipher(): Cipher? {
        val slot = biometricSlot() ?: return null
        return runCatching { keystoreManager.biometricDecryptCipher(slot.wrappedKey) }.getOrNull()
    }

    @Synchronized
    open fun provisionAndUnlockBiometric(authenticatedEncryptCipher: Cipher): Boolean {
        val noAuth = slotStore.load().firstOrNull { it is KeystoreSlot && !it.requiresAuth }
            as? KeystoreSlot ?: return false
        val masterKey = keystoreManager.decrypt(noAuth.wrappedKey)
        try {
            val wrapped = keystoreManager.biometricWrap(authenticatedEncryptCipher, masterKey)
            val slots = slotStore.load().filterNot { it is KeystoreSlot && it.requiresAuth } +
                KeystoreSlot(newUuid(), requiresAuth = true, wrappedKey = wrapped)
            slotStore.save(slots)
            session.unlock(masterKey)
        } finally {
            Arrays.fill(masterKey, 0)
        }
        return true
    }

    @Synchronized
    open fun finishBiometricUnlock(authenticatedCipher: Cipher): Boolean {
        val slot = biometricSlot() ?: return false
        val masterKey = runCatching {
            keystoreManager.biometricUnwrap(authenticatedCipher, slot.wrappedKey)
        }.getOrNull() ?: return false
        try {
            session.unlock(masterKey)
        } finally {
            Arrays.fill(masterKey, 0)
        }
        return true
    }

    @Synchronized
    open fun setPassword(password: String) {
        val masterKey = session.requireKey()
        try {
            val slots = slotStore.load()
                .filterNot { it is KeystoreSlot && !it.requiresAuth }
                .filterNot { it is PasswordSlot }
            slotStore.save(slots + passwordSlotFrom(password, masterKey))
            slotStore.legacyPasswordPresent = false
            legacyPassword.clear()
        } finally {
            Arrays.fill(masterKey, 0)
        }
    }

    @Synchronized
    open fun removePassword() {
        val masterKey = session.requireKey()
        try {
            slotStore.save(listOf(noAuthSlot(masterKey)))
            slotStore.legacyPasswordPresent = false
            legacyPassword.clear()
            keystoreManager.deleteBiometricKey()
        } finally {
            Arrays.fill(masterKey, 0)
        }
    }

    @Synchronized
    open fun enableBiometric(authenticatedCipher: Cipher) {
        val masterKey = session.requireKey()
        try {
            val wrapped = keystoreManager.biometricWrap(authenticatedCipher, masterKey)
            val slots = slotStore.load().filterNot { it is KeystoreSlot && it.requiresAuth }
            slotStore.save(slots + KeystoreSlot(newUuid(), requiresAuth = true, wrappedKey = wrapped))
        } finally {
            Arrays.fill(masterKey, 0)
        }
    }

    @Synchronized
    open fun disableBiometric() {
        val slots = slotStore.load().filterNot { it is KeystoreSlot && it.requiresAuth }
        slotStore.save(slots)
        keystoreManager.deleteBiometricKey()
    }

    private fun biometricSlot(): KeystoreSlot? =
        slotStore.load().firstOrNull { it is KeystoreSlot && it.requiresAuth } as? KeystoreSlot

    private fun noAuthSlot(masterKey: ByteArray): KeystoreSlot =
        KeystoreSlot(newUuid(), requiresAuth = false, wrappedKey = keystoreManager.encrypt(masterKey))

    private fun unwrapPasswordSlot(slot: PasswordSlot, password: String): ByteArray? {
        val kek = Argon2KeyDeriver.derive(
            password = password.toByteArray(Charsets.UTF_8),
            salt = slot.salt,
            memoryKib = slot.memoryKib,
            iterations = slot.iterations,
            parallelism = slot.parallelism,
        )
        return try {
            AesGcm.decrypt(kek, slot.wrappedKey)
        } catch (_: AEADBadTagException) {
            null
        } finally {
            Arrays.fill(kek, 0)
        }
    }

    private fun passwordSlotFrom(password: String, masterKey: ByteArray): PasswordSlot {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val kek = Argon2KeyDeriver.derive(password.toByteArray(Charsets.UTF_8), salt)
        try {
            return PasswordSlot(
                uuid = newUuid(),
                salt = salt,
                memoryKib = Argon2KeyDeriver.DEFAULT_MEMORY_KIB,
                iterations = Argon2KeyDeriver.DEFAULT_ITERATIONS,
                parallelism = Argon2KeyDeriver.DEFAULT_PARALLELISM,
                wrappedKey = AesGcm.encrypt(kek, masterKey),
            )
        } finally {
            Arrays.fill(kek, 0)
        }
    }

    private fun newUuid(): String = UUID.randomUUID().toString()

    companion object {
        private const val SALT_LENGTH = 16
    }
}
