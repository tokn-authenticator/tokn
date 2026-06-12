package me.diamondforge.tokn.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diamondforge.tokn.data.preferences.AppPreferencesRepository
import me.diamondforge.tokn.data.preferences.ThemeMode
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.domain.model.TapBehavior
import me.diamondforge.tokn.domain.security.PasswordReminderSchedule
import me.diamondforge.tokn.security.vault.VaultManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val appPreferences: AppPreferencesRepository,
    private val vaultManager: VaultManager,
) : ViewModel() {

    private val _passwordError = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.themeMode,
        preferences.autoLockTimeoutSeconds,
        preferences.biometricEnabled,
        preferences.screenshotsEnabled,
        preferences.encryptionEnabled,
    ) { theme, timeout, biometric, screenshots, encryption ->
        SettingsUiState(
            themeMode = theme,
            autoLockTimeoutSeconds = timeout,
            biometricEnabled = biometric,
            screenshotsEnabled = screenshots,
            encryptionEnabled = encryption,
        )
    }.combine(_passwordError) { state, error ->
        state.copy(passwordVerificationFailed = error)
    }.combine(appPreferences.iconFetchEnabled) { state, iconFetch ->
        state.copy(iconFetchEnabled = iconFetch)
    }.combine(preferences.tapToRevealEnabled) { state, tapToReveal ->
        state.copy(tapToRevealEnabled = tapToReveal)
    }.combine(preferences.tapBehavior) { state, tapBehavior ->
        state.copy(tapBehavior = tapBehavior)
    }.combine(preferences.dynamicColorEnabled) { state, dynamicColor ->
        state.copy(dynamicColorEnabled = dynamicColor)
    }.combine(preferences.showNextCodeEnabled) { state, showNextCode ->
        state.copy(showNextCodeEnabled = showNextCode)
    }.combine(preferences.passwordReminderEnabled) { state, passwordReminder ->
        state.copy(passwordReminderEnabled = passwordReminder)
    }.combine(preferences.passwordReminderLastShownAt) { state, lastShownAt ->
        state.copy(passwordReminderLastShownAt = lastShownAt)
    }.combine(preferences.passwordReminderStage) { state, stage ->
        // Re-read the clock per emission so the countdown reflects the current day.
        state.copy(
            passwordReminderNextDays = PasswordReminderSchedule.daysUntilDue(
                System.currentTimeMillis(),
                state.passwordReminderLastShownAt,
                stage,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setAutoLockTimeout(seconds: Int) {
        viewModelScope.launch { preferences.setAutoLockTimeout(seconds) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setBiometricEnabled(enabled)
            // Enabling provisions the slot via a prompt in MainActivity (needs the Activity).
            if (!enabled) {
                withContext(Dispatchers.IO) { vaultManager.disableBiometric() }
            }
        }
    }

    fun setScreenshotsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setScreenshotsEnabled(enabled) }
    }

    fun setIconFetchEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setIconFetchEnabled(enabled) }
    }

    fun setTapToRevealEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setTapToRevealEnabled(enabled) }
    }

    fun setTapBehavior(behavior: TapBehavior) {
        viewModelScope.launch { preferences.setTapBehavior(behavior) }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColorEnabled(enabled) }
    }

    fun setShowNextCodeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setShowNextCodeEnabled(enabled) }
    }

    fun setupEncryption(password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultManager.setPassword(password)
            preferences.setEncryptionEnabled(true)
            // Seed so enabling encryption here doesn't fire the reminder immediately.
            preferences.setPasswordReminderLastShownAt(System.currentTimeMillis())
            preferences.setPasswordReminderStage(0)
        }
    }

    fun disableEncryption(password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (vaultManager.verifyPassword(password)) {
                vaultManager.removePassword()
                preferences.setEncryptionEnabled(false)
                preferences.setBiometricEnabled(false)
            } else {
                _passwordError.update { true }
            }
        }
    }

    fun setPasswordReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setPasswordReminderEnabled(enabled) }
    }

    fun clearPasswordError() = _passwordError.update { false }
}

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoLockTimeoutSeconds: Int = 60,
    val biometricEnabled: Boolean = true,
    val screenshotsEnabled: Boolean = false,
    val encryptionEnabled: Boolean = false,
    val iconFetchEnabled: Boolean = false,
    val tapToRevealEnabled: Boolean = false,
    val tapBehavior: TapBehavior = TapBehavior.SINGLE,
    val dynamicColorEnabled: Boolean = true,
    val showNextCodeEnabled: Boolean = false,
    val passwordReminderEnabled: Boolean = true,
    val passwordReminderLastShownAt: Long = 0L,
    val passwordReminderNextDays: Int = 0,
    val passwordVerificationFailed: Boolean = false,
)
