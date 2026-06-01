package me.diamondforge.tokn.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diamondforge.tokn.data.icon.IconImageUtil
import me.diamondforge.tokn.data.icon.IconPackManager
import me.diamondforge.tokn.data.icon.InstalledIconPack
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.domain.usecase.GetAccountByIdUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.UpdateAccountUseCase
import javax.inject.Inject

@HiltViewModel
class EditAccountViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val getAccountByIdUseCase: GetAccountByIdUseCase,
    private val updateAccountUseCase: UpdateAccountUseCase,
    private val iconPackManager: IconPackManager,
    getAccountsUseCase: GetAccountsUseCase,
) : ViewModel() {

    private val accountId: Long = checkNotNull(savedStateHandle["accountId"])

    private val _uiState = MutableStateFlow(EditAccountUiState())
    val uiState: StateFlow<EditAccountUiState> = _uiState.asStateFlow()

    val installedPacks: StateFlow<List<InstalledIconPack>> = iconPackManager.installed

    // Suggestion source for the group chip field: every distinct group
    // currently in use across all stored accounts, case-insensitive,
    // alphabetically sorted.
    val availableGroups: StateFlow<List<String>> = getAccountsUseCase()
        .map { accounts ->
            accounts.flatMap { it.groups }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val account = getAccountByIdUseCase(accountId) ?: return@launch
            _uiState.update {
                it.copy(
                    issuer = account.issuer,
                    accountName = account.accountName,
                    secret = account.secret,
                    algorithm = account.algorithm,
                    digits = account.digits,
                    period = account.period,
                    type = account.type,
                    groups = account.groups,
                    counter = account.counter.toString(),
                    customIconBytes = account.customIconBytes,
                    iconPackId = account.iconPackId,
                    iconPackFile = account.iconPackFile,
                    packIconPath = resolvePackPath(account.iconPackId, account.iconPackFile),
                    isLoaded = true,
                )
            }
        }
    }

    fun updateIssuer(value: String) = _uiState.update { it.copy(issuer = value) }
    fun updateAccountName(value: String) = _uiState.update { it.copy(accountName = value) }
    fun updateSecret(value: String) = _uiState.update { it.copy(secret = value) }
    fun updateAlgorithm(value: OtpAlgorithm) = _uiState.update { it.copy(algorithm = value) }
    fun updateDigits(value: Int) = _uiState.update { it.copy(digits = value) }
    fun updatePeriod(value: Int) = _uiState.update { it.copy(period = value) }
    fun updateType(value: OtpType) = _uiState.update { it.copy(type = value) }
    fun updateGroups(values: List<String>) = _uiState.update { it.copy(groups = values) }
    fun updateCounter(value: String) =
        _uiState.update { it.copy(counter = value.filter { c -> c.isDigit() }) }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun pickCustomIcon(uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { IconImageUtil.loadAndResize(context, uri) }
            if (bytes != null) {
                _uiState.update {
                    it.copy(
                        customIconBytes = bytes,
                        iconPackId = null,
                        iconPackFile = null,
                        packIconPath = null,
                    )
                }
            }
        }
    }

    fun pickPackIcon(packUuid: String, filename: String) {
        val path = resolvePackPath(packUuid, filename) ?: return
        _uiState.update {
            it.copy(
                customIconBytes = null,
                iconPackId = packUuid,
                iconPackFile = filename,
                packIconPath = path,
            )
        }
    }

    fun clearIcon() {
        _uiState.update {
            it.copy(
                customIconBytes = null,
                iconPackId = null,
                iconPackFile = null,
                packIconPath = null,
            )
        }
    }

    private fun resolvePackPath(packId: String?, filename: String?): String? {
        if (packId == null || filename == null) return null
        return iconPackManager.iconFile(packId, filename)?.absolutePath
    }

    fun saveChanges(onSuccess: () -> Unit) {
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
        val parsedCounter = state.counter.trim().toLongOrNull()
        if (state.type == OtpType.HOTP && (parsedCounter == null || parsedCounter < 0)) {
            _uiState.update { it.copy(error = "Counter must be a non-negative number") }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                val current = getAccountByIdUseCase(accountId) ?: error("Account not found")
                updateAccountUseCase(
                    current.copy(
                        issuer = state.issuer.trim(),
                        accountName = state.accountName.trim(),
                        secret = state.secret.trim().uppercase().replace(" ", ""),
                        algorithm = state.algorithm,
                        digits = state.digits,
                        period = state.period,
                        type = state.type,
                        groups = state.groups,
                        counter = if (state.type == OtpType.HOTP) parsedCounter
                            ?: current.counter else current.counter,
                        customIconBytes = state.customIconBytes,
                        iconPackId = state.iconPackId,
                        iconPackFile = state.iconPackFile,
                    ),
                )
            }.onSuccess { onSuccess() }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message ?: "Failed to save",
                            isSaving = false
                        )
                    }
                }
        }
    }
}

data class EditAccountUiState(
    val issuer: String = "",
    val accountName: String = "",
    val secret: String = "",
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
    val type: OtpType = OtpType.TOTP,
    val groups: List<String> = emptyList(),
    val counter: String = "0",
    val error: String? = null,
    val isLoaded: Boolean = false,
    val isSaving: Boolean = false,
    val customIconBytes: ByteArray? = null,
    val iconPackId: String? = null,
    val iconPackFile: String? = null,
    val packIconPath: String? = null,
) {
    val hasIcon: Boolean get() = customIconBytes != null || packIconPath != null
}
