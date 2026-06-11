package me.diamondforge.tokn.data.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.security.BiometricHelper
import me.diamondforge.tokn.security.vault.VaultManager
import javax.inject.Inject
import javax.inject.Singleton

enum class VaultAuthMode { NONE, BIOMETRIC, PASSWORD }

/**
 * Decides what credential, if any, the user must present before a sensitive
 * action (exporting a backup, sending the vault to another device). Mirrors the
 * unlock path: biometric when it's enabled and provisioned, otherwise the vault
 * password. With no app lock there is nothing to check.
 */
@Singleton
class VaultAuthGate @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val vaultManager: VaultManager,
    private val biometricHelper: BiometricHelper,
) {
    suspend fun mode(): VaultAuthMode = when {
        !preferences.encryptionEnabled.first() -> VaultAuthMode.NONE
        preferences.biometricEnabled.first() &&
            withContext(Dispatchers.IO) { vaultManager.canBiometricUnlock() } &&
            biometricHelper.isAvailable() -> VaultAuthMode.BIOMETRIC

        else -> VaultAuthMode.PASSWORD
    }

    suspend fun verifyPassword(password: String): Boolean =
        withContext(Dispatchers.IO) { vaultManager.verifyPassword(password) }
}
