package me.diamondforge.tokn.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.security.LockManager
import me.diamondforge.tokn.domain.usecase.AddAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddAccountViewModel @Inject constructor(
    private val addAccountUseCase: AddAccountUseCase,
    private val lockManager: LockManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddAccountUiState())
    val uiState: StateFlow<AddAccountUiState> = _uiState.asStateFlow()

    fun suppressLock() = lockManager.suppressNextForeground()

    fun onQrScanned(rawValue: String) {
        runCatching { OtpAuthParser.parse(rawValue) }
            .onSuccess { account -> _uiState.update { it.copy(parsedAccount = account, error = null) } }
            .onFailure { _uiState.update { it.copy(error = "Invalid QR code") } }
    }

    fun updateIssuer(value: String) = _uiState.update { it.copy(issuer = value) }
    fun updateAccountName(value: String) = _uiState.update { it.copy(accountName = value) }
    fun updateSecret(value: String) = _uiState.update { it.copy(secret = value) }
    fun updateAlgorithm(value: OtpAlgorithm) = _uiState.update { it.copy(algorithm = value) }
    fun updateDigits(value: Int) = _uiState.update { it.copy(digits = value) }
    fun updatePeriod(value: Int) = _uiState.update { it.copy(period = value) }
    fun updateType(value: OtpType) = _uiState.update { it.copy(type = value) }
    fun updateGroup(value: String) = _uiState.update { it.copy(group = value) }

    fun saveAccount(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isSaving) return
        if (state.secret.isBlank()) {
            _uiState.update { it.copy(error = "Secret is required") }
            return
        }
        val cleanedSecret = state.secret.trim().uppercase().replace(" ", "").trimEnd('=')
        if (!cleanedSecret.matches(Regex("[A-Z2-7]+"))) {
            _uiState.update { it.copy(error = "Invalid secret key: only letters A–Z and digits 2–7 are allowed") }
            return
        }
        if (state.accountName.isBlank()) {
            _uiState.update { it.copy(error = "Account name is required") }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                addAccountUseCase(
                    OtpAccount(
                        issuer = state.issuer.trim(),
                        accountName = state.accountName.trim(),
                        secret = state.secret.trim().uppercase().replace(" ", ""),
                        algorithm = state.algorithm,
                        digits = state.digits,
                        period = state.period,
                        type = state.type,
                        group = state.group.trim().ifBlank { null },
                    ),
                )
            }.onSuccess { onSuccess() }
                .onFailure { throwable ->
                    _uiState.update { it.copy(error = throwable.message ?: "Failed to save account", isSaving = false) }
                }
        }
    }

    fun applyParsedAccount() {
        val parsed = _uiState.value.parsedAccount ?: return
        _uiState.update {
            it.copy(
                issuer = parsed.issuer,
                accountName = parsed.accountName,
                secret = parsed.secret,
                algorithm = parsed.algorithm,
                digits = parsed.digits,
                period = parsed.period,
                type = parsed.type,
                parsedAccount = null,
            )
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

data class AddAccountUiState(
    val issuer: String = "",
    val accountName: String = "",
    val secret: String = "",
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
    val type: OtpType = OtpType.TOTP,
    val group: String = "",
    val parsedAccount: OtpAccount? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
)
