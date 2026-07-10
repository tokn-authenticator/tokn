package me.diamondforge.tokn.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import me.diamondforge.tokn.data.preferences.AppPreferencesRepository
import me.diamondforge.tokn.data.preferences.FakePreferencesDataStore
import me.diamondforge.tokn.data.preferences.ThemeMode
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.domain.model.TapBehavior
import me.diamondforge.tokn.security.KeystoreManager
import me.diamondforge.tokn.security.VaultPasswordManager
import me.diamondforge.tokn.security.vault.VaultManager
import me.diamondforge.tokn.security.vault.VaultSession

internal class FakeUserPreferences(context: Context) : UserPreferencesRepository(FakePreferencesDataStore()) {
    val theme = MutableStateFlow(ThemeMode.SYSTEM)
    val autoLock = MutableStateFlow(60)
    val biometric = MutableStateFlow(true)
    val screenshots = MutableStateFlow(false)
    val encryption = MutableStateFlow(false)
    val tapReveal = MutableStateFlow(false)
    val tapBehaviorState = MutableStateFlow(TapBehavior.SINGLE)
    val dynamicColor = MutableStateFlow(true)
    val showNext = MutableStateFlow(false)
    val reminderEnabled = MutableStateFlow(true)
    val reminderLastShownAt = MutableStateFlow(0L)
    val reminderStage = MutableStateFlow(0)

    override val themeMode = theme
    override val autoLockTimeoutSeconds = autoLock
    override val biometricEnabled = biometric
    override val screenshotsEnabled = screenshots
    override val encryptionEnabled = encryption
    override val tapToRevealEnabled = tapReveal
    override val tapBehavior = tapBehaviorState
    override val dynamicColorEnabled = dynamicColor
    override val showNextCodeEnabled = showNext
    override val passwordReminderEnabled = reminderEnabled
    override val passwordReminderLastShownAt = reminderLastShownAt
    override val passwordReminderStage = reminderStage

    override suspend fun setThemeMode(mode: ThemeMode) { theme.value = mode }
    override suspend fun setAutoLockTimeout(seconds: Int) { autoLock.value = seconds }
    override suspend fun setBiometricEnabled(enabled: Boolean) { biometric.value = enabled }
    override suspend fun setScreenshotsEnabled(enabled: Boolean) { screenshots.value = enabled }
    override suspend fun setEncryptionEnabled(enabled: Boolean) { encryption.value = enabled }
    override suspend fun setTapToRevealEnabled(enabled: Boolean) { tapReveal.value = enabled }
    override suspend fun setTapBehavior(behavior: TapBehavior) { tapBehaviorState.value = behavior }
    override suspend fun setDynamicColorEnabled(enabled: Boolean) { dynamicColor.value = enabled }
    override suspend fun setShowNextCodeEnabled(enabled: Boolean) { showNext.value = enabled }
    override suspend fun setPasswordReminderEnabled(enabled: Boolean) { reminderEnabled.value = enabled }
    override suspend fun setPasswordReminderLastShownAt(timestamp: Long) { reminderLastShownAt.value = timestamp }
    override suspend fun setPasswordReminderStage(stage: Int) { reminderStage.value = stage }
}

internal class FakeAppPreferences(context: Context) : AppPreferencesRepository(FakePreferencesDataStore()) {
    val iconFetch = MutableStateFlow(false)
    override val iconFetchEnabled = iconFetch
    override suspend fun setIconFetchEnabled(enabled: Boolean) { iconFetch.value = enabled }
}

internal class FakeVaultManager(context: Context) : VaultManager(
    context,
    KeystoreManager(context),
    VaultSession(),
    VaultPasswordManager(KeystoreManager(context), context),
) {
    var verifyResult = true
    val passwordSet = MutableStateFlow(false)
    val passwordRemoved = MutableStateFlow(false)
    val biometricDisabled = MutableStateFlow(false)

    override fun verifyPassword(password: String): Boolean = verifyResult
    override fun setPassword(password: String) { passwordSet.value = true }
    override fun removePassword() { passwordRemoved.value = true }
    override fun disableBiometric() { biometricDisabled.value = true }
}
