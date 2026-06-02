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
import me.diamondforge.tokn.data.preferences.AppPreferencesRepository
import me.diamondforge.tokn.data.preferences.ThemeMode
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.security.VaultPasswordManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val appPreferences: AppPreferencesRepository,
    private val vaultPasswordManager: VaultPasswordManager,
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
    }.combine(preferences.dynamicColorEnabled) { state, dynamicColor ->
        state.copy(dynamicColorEnabled = dynamicColor)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setAutoLockTimeout(seconds: Int) {
        viewModelScope.launch { preferences.setAutoLockTimeout(seconds) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setBiometricEnabled(enabled) }
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

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColorEnabled(enabled) }
    }

    fun setupEncryption(password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultPasswordManager.setup(password)
            preferences.setEncryptionEnabled(true)
        }
    }

    fun disableEncryption(password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (vaultPasswordManager.verify(password)) {
                vaultPasswordManager.clear()
                preferences.setEncryptionEnabled(false)
            } else {
                _passwordError.update { true }
            }
        }
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
    val dynamicColorEnabled: Boolean = true,
    val passwordVerificationFailed: Boolean = false,
)
