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
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.domain.usecase.AddAccountUseCase
import me.diamondforge.tokn.security.BiometricHelper
import me.diamondforge.tokn.security.LockManager
import me.diamondforge.tokn.security.VaultPasswordManager
import org.json.JSONObject
import javax.inject.Inject

enum class CryptType { NONE, PASSWORD, BIOMETRIC }

enum class ImportError { Invalid, Redirect }

data class OnboardingUiState(
    val cryptType: CryptType? = null,
    val password: String = "",
    val passwordConfirm: String = "",
    val biometricAvailable: Boolean = false,
    val importedCount: Int? = null,
    val importError: ImportError? = null,
    val isFinishing: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val vaultPasswordManager: VaultPasswordManager,
    private val addAccountUseCase: AddAccountUseCase,
    private val lockManager: LockManager,
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
        _uiState.update { it.copy(importedCount = null, importError = null) }
    }

    fun suppressLock() = lockManager.suppressNextForeground()

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { parseImport(uri) }
            _uiState.update {
                when (result) {
                    is ImportResult.Success -> it.copy(
                        importedCount = result.count,
                        importError = null
                    )

                    ImportResult.Redirect -> it.copy(importError = ImportError.Redirect)
                    ImportResult.Invalid -> it.copy(importError = ImportError.Invalid)
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
                    vaultPasswordManager.setup(state.password)
                }
                preferences.setEncryptionEnabled(type != CryptType.NONE)
                preferences.setBiometricEnabled(type == CryptType.BIOMETRIC)
                preferences.setOnboardingDone(true)
            }
            // Avoid the lock screen flashing while transitioning into the app
            lockManager.suppressNextForeground()
            lockManager.unlock()
            _uiState.update { it.copy(isFinishing = false) }
            onComplete()
        }
    }

    private sealed interface ImportResult {
        data class Success(val count: Int) : ImportResult
        data object Redirect : ImportResult
        data object Invalid : ImportResult
    }

    private suspend fun parseImport(uri: Uri): ImportResult {
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return ImportResult.Invalid

        // Not a valid JSON document at all — treat as unsupported file.
        val root = runCatching { JSONObject(raw) }.getOrElse { return ImportResult.Invalid }

        // Anything that isn't a Tokn backup (regardless of source app) gets redirected to
        // Settings → Backup, where format-specific importers live.
        if (!root.has("accounts") || !root.has("version")) {
            return ImportResult.Redirect
        }

        val accounts = runCatching { parseToknBackup(root) }
            .getOrElse { return ImportResult.Redirect }
        accounts.forEach { addAccountUseCase(it) }
        return ImportResult.Success(accounts.size)
    }

    private fun parseToknBackup(root: JSONObject): List<OtpAccount> {
        val array = root.getJSONArray("accounts")
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            OtpAccount(
                issuer = obj.getString("issuer"),
                accountName = obj.getString("accountName"),
                secret = obj.getString("secret"),
                algorithm = OtpAlgorithm.valueOf(obj.optString("algorithm", "SHA1")),
                digits = obj.optInt("digits", 6),
                period = obj.optInt("period", 30),
                counter = obj.optLong("counter", 0),
                type = OtpType.valueOf(obj.optString("type", "TOTP")),
                sortOrder = obj.optInt("sortOrder", 0),
                group = obj.optString("group").ifBlank { null },
            )
        }
    }

}
