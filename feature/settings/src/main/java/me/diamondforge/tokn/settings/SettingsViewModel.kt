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
import me.diamondforge.tokn.audit.AuditEventType
import me.diamondforge.tokn.audit.AuditLogger
import me.diamondforge.tokn.audit.NoopAuditLogger
import me.diamondforge.tokn.data.preferences.AppPreferencesRepository
import me.diamondforge.tokn.data.preferences.ThemeMode
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.domain.model.TapBehavior
import me.diamondforge.tokn.domain.security.PasswordReminderSchedule
import me.diamondforge.tokn.domain.usecase.GetTrashedAccountsUseCase
import me.diamondforge.tokn.domain.usecase.PurgeAccountsUseCase
import me.diamondforge.tokn.security.vault.VaultManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val appPreferences: AppPreferencesRepository,
    private val vaultManager: VaultManager,
    getTrashedAccountsUseCase: GetTrashedAccountsUseCase,
    private val purgeAccountsUseCase: PurgeAccountsUseCase,
    private val auditLogger: AuditLogger = NoopAuditLogger,
) : ViewModel() {

    private val trashed = getTrashedAccountsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
    }.combine(preferences.stayRevealedEnabled) { state, stayRevealed ->
        state.copy(stayRevealedEnabled = stayRevealed)
    }.combine(preferences.tapBehavior) { state, tapBehavior ->
        state.copy(tapBehavior = tapBehavior)
    }.combine(preferences.dynamicColorEnabled) { state, dynamicColor ->
        state.copy(dynamicColorEnabled = dynamicColor)
    }.combine(preferences.showNextCodeEnabled) { state, showNextCode ->
        state.copy(showNextCodeEnabled = showNextCode)
    }.combine(preferences.recycleBinEnabled) { state, recycleBin ->
        state.copy(recycleBinEnabled = recycleBin)
    }.combine(trashed) { state, trashedAccounts ->
        state.copy(trashedCount = trashedAccounts.size)
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
        viewModelScope.launch {
            preferences.setThemeMode(mode)
            auditLogger.log(AuditEventType.THEME_CHANGED, detail = mode.name)
        }
    }

    fun setAutoLockTimeout(seconds: Int) {
        viewModelScope.launch {
            preferences.setAutoLockTimeout(seconds)
            auditLogger.log(AuditEventType.AUTO_LOCK_TIMEOUT_CHANGED, detail = seconds.toString())
        }
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
        viewModelScope.launch {
            preferences.setScreenshotsEnabled(enabled)
            auditLogger.log(
                if (enabled) AuditEventType.SCREENSHOT_PROTECTION_DISABLED
                else AuditEventType.SCREENSHOT_PROTECTION_ENABLED,
            )
        }
    }

    fun setIconFetchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setIconFetchEnabled(enabled)
            auditLogger.log(
                if (enabled) AuditEventType.ICON_FETCH_ENABLED else AuditEventType.ICON_FETCH_DISABLED,
            )
        }
    }

    fun setTapToRevealEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setTapToRevealEnabled(enabled)
            auditLogger.log(
                if (enabled) AuditEventType.TAP_TO_REVEAL_ENABLED else AuditEventType.TAP_TO_REVEAL_DISABLED,
            )
        }
    }

    fun setStayRevealedEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setStayRevealedEnabled(enabled)
            auditLogger.log(
                if (enabled) AuditEventType.STAY_REVEALED_ENABLED else AuditEventType.STAY_REVEALED_DISABLED,
            )
        }
    }

    fun setTapBehavior(behavior: TapBehavior) {
        viewModelScope.launch {
            preferences.setTapBehavior(behavior)
            auditLogger.log(AuditEventType.TAP_BEHAVIOR_CHANGED, detail = behavior.name)
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setDynamicColorEnabled(enabled)
            auditLogger.log(
                if (enabled) AuditEventType.DYNAMIC_COLOR_ENABLED else AuditEventType.DYNAMIC_COLOR_DISABLED,
            )
        }
    }

    fun setShowNextCodeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setShowNextCodeEnabled(enabled)
            auditLogger.log(
                if (enabled) AuditEventType.SHOW_NEXT_CODE_ENABLED else AuditEventType.SHOW_NEXT_CODE_DISABLED,
            )
        }
    }

    fun setRecycleBinEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setRecycleBinEnabled(enabled)
            auditLogger.log(
                if (enabled) AuditEventType.RECYCLE_BIN_SETTING_ENABLED
                else AuditEventType.RECYCLE_BIN_SETTING_DISABLED,
            )
        }
    }

    fun disableRecycleBin() {
        viewModelScope.launch {
            purgeAccountsUseCase(trashed.value.map { it.id }.toSet())
            preferences.setRecycleBinEnabled(false)
            auditLogger.log(AuditEventType.RECYCLE_BIN_SETTING_DISABLED)
        }
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
        viewModelScope.launch {
            preferences.setPasswordReminderEnabled(enabled)
            auditLogger.log(
                if (enabled) AuditEventType.PASSWORD_REMINDER_ENABLED
                else AuditEventType.PASSWORD_REMINDER_DISABLED,
            )
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
    val stayRevealedEnabled: Boolean = false,
    val tapBehavior: TapBehavior = TapBehavior.SINGLE,
    val dynamicColorEnabled: Boolean = true,
    val showNextCodeEnabled: Boolean = false,
    val recycleBinEnabled: Boolean = true,
    val trashedCount: Int = 0,
    val passwordReminderEnabled: Boolean = true,
    val passwordReminderLastShownAt: Long = 0L,
    val passwordReminderNextDays: Int = 0,
    val passwordVerificationFailed: Boolean = false,
)
