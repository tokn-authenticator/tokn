package me.diamondforge.tokn.onboarding

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.importer.ExternalImporter
import me.diamondforge.tokn.importer.ImportOutcome
import me.diamondforge.tokn.security.KeystoreManager
import me.diamondforge.tokn.security.VaultPasswordManager
import me.diamondforge.tokn.security.vault.VaultManager
import me.diamondforge.tokn.security.vault.VaultSession

internal class FakeOnboardingPreferences(context: Context) : UserPreferencesRepository(context) {
    val encryption = MutableStateFlow<Boolean?>(null)
    val biometric = MutableStateFlow<Boolean?>(null)
    val onboardingDoneFlag = MutableStateFlow(false)
    val reminderLastShownAt = MutableStateFlow(0L)
    val reminderStage = MutableStateFlow(-1)

    override suspend fun setEncryptionEnabled(enabled: Boolean) { encryption.value = enabled }
    override suspend fun setBiometricEnabled(enabled: Boolean) { biometric.value = enabled }
    override suspend fun setOnboardingDone(done: Boolean) { onboardingDoneFlag.value = done }
    override suspend fun setPasswordReminderLastShownAt(timestamp: Long) { reminderLastShownAt.value = timestamp }
    override suspend fun setPasswordReminderStage(stage: Int) { reminderStage.value = stage }
}

internal class FakeOnboardingVault(context: Context) : VaultManager(
    context,
    KeystoreManager(context),
    VaultSession(),
    VaultPasswordManager(KeystoreManager(context), context),
) {
    val passwordSet = MutableStateFlow(false)
    val biometricDisabled = MutableStateFlow(false)

    override fun setPassword(password: String) { passwordSet.value = true }
    override fun disableBiometric() { biometricDisabled.value = true }
}

internal class FakeImporter(
    override val id: String,
    private val outcome: ImportOutcome,
    private val handles: Boolean = true,
) : ExternalImporter {
    override val displayName: String = id
    override val noteRes: Int? = null
    override val acceptedMimeTypes: Array<String> = arrayOf("*/*")
    override fun canHandle(raw: ByteArray): Boolean = handles
    override fun parse(raw: ByteArray, password: String?): ImportOutcome = outcome
}
