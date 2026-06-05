package me.diamondforge.tokn.onboarding

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.domain.usecase.AddAccountUseCase
import me.diamondforge.tokn.importer.ImportOutcome
import me.diamondforge.tokn.importer.ImporterRegistry
import me.diamondforge.tokn.security.BiometricHelper
import me.diamondforge.tokn.security.LockManager
import me.diamondforge.tokn.security.vault.VaultManager
import javax.inject.Inject

enum class CryptType { NONE, PASSWORD, BIOMETRIC }

enum class ImportError { Invalid, Redirect, WrongPassword }

data class PendingToknImport(val uri: Uri)

data class OnboardingUiState(
    val cryptType: CryptType? = null,
    val password: String = "",
    val passwordConfirm: String = "",
    val biometricAvailable: Boolean = false,
    val importedCount: Int? = null,
    val importError: ImportError? = null,
    val pendingTokn: PendingToknImport? = null,
    val isFinishing: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val vaultManager: VaultManager,
    private val addAccountUseCase: AddAccountUseCase,
    private val lockManager: LockManager,
    private val importerRegistry: ImporterRegistry,
    biometricHelper: BiometricHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(biometricAvailable = biometricHelper.isAvailable()),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun setCryptType(type: CryptType) {
        _uiState.update { it.copy(cryptType = type) }
    }

    fun setPassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun setPasswordConfirm(value: String) {
        _uiState.update { it.copy(passwordConfirm = value) }
    }

    fun clearImportFeedback() {
        _uiState.update {
            it.copy(importedCount = null, importError = null, pendingTokn = null)
        }
    }

    fun cancelPendingImport() {
        _uiState.update { it.copy(pendingTokn = null, importError = null) }
    }

    fun suppressLock() = lockManager.suppressNextForeground()

    /**
     * Routes the picked file through the same importer registry used by Settings
     * so an encrypted Tokn vault (the default export!) actually imports here
     * instead of triggering the "looks like another app" redirect.
     */
    fun importBackup(uri: Uri, password: String? = null) {
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) { readBytes(uri) }
                ?: run {
                    _uiState.update { it.copy(importError = ImportError.Invalid) }
                    return@launch
                }

            val importer = importerRegistry.detect(raw)
                ?: run {
                    _uiState.update {
                        it.copy(importError = ImportError.Invalid, pendingTokn = null)
                    }
                    return@launch
                }

            if (importer.id != "tokn") {
                // External / migration-QR sources: punt to Settings.
                _uiState.update {
                    it.copy(importError = ImportError.Redirect, pendingTokn = null)
                }
                return@launch
            }

            when (val outcome = withContext(Dispatchers.IO) { importer.parse(raw, password) }) {
                is ImportOutcome.Success -> {
                    withContext(Dispatchers.IO) {
                        outcome.accounts.forEach { addAccountUseCase(it) }
                    }
                    _uiState.update {
                        it.copy(
                            importedCount = outcome.accounts.size,
                            importError = null,
                            pendingTokn = null,
                        )
                    }
                }

                ImportOutcome.NeedsPassword -> {
                    _uiState.update {
                        it.copy(pendingTokn = PendingToknImport(uri), importError = null)
                    }
                }

                is ImportOutcome.WrongPassword -> {
                    _uiState.update {
                        it.copy(
                            pendingTokn = PendingToknImport(uri),
                            importError = ImportError.WrongPassword,
                        )
                    }
                }

                is ImportOutcome.Malformed, ImportOutcome.Unsupported -> {
                    _uiState.update {
                        it.copy(importError = ImportError.Invalid, pendingTokn = null)
                    }
                }
            }
        }
    }

    fun finish(onComplete: () -> Unit) {
        val state = _uiState.value
        val type = state.cryptType ?: return
        _uiState.update { it.copy(isFinishing = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (type != CryptType.NONE) {
                    vaultManager.setPassword(state.password)
                }
                // edge case, user want  a other method
                if (type != CryptType.BIOMETRIC) {
                    vaultManager.disableBiometric()
                }
                preferences.setEncryptionEnabled(type != CryptType.NONE)
                preferences.setBiometricEnabled(type == CryptType.BIOMETRIC)
                preferences.setOnboardingDone(true)
            }
            lockManager.suppressNextForeground()
            lockManager.unlock()
            _uiState.update { it.copy(isFinishing = false) }
            onComplete()
        }
    }

    private fun readBytes(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()
}
