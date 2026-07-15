package me.diamondforge.tokn.add

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diamondforge.tokn.data.icon.IconImageUtil
import me.diamondforge.tokn.data.icon.IconMatchType
import me.diamondforge.tokn.data.icon.IconPackManager
import me.diamondforge.tokn.data.icon.InstalledIconPack
import me.diamondforge.tokn.data.icon.suggestionsFor
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.domain.usecase.AddAccountUseCase
import me.diamondforge.tokn.domain.usecase.ListGroupsUseCase
import me.diamondforge.tokn.importer.otpauth.OtpAuthParser
import me.diamondforge.tokn.security.LockManager
import javax.inject.Inject

@HiltViewModel
class AddAccountViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val addAccountUseCase: AddAccountUseCase,
    private val lockManager: LockManager,
    private val iconPackManager: IconPackManager,
    listGroupsUseCase: ListGroupsUseCase,
) : ViewModel() {

    val installedPacks: StateFlow<List<InstalledIconPack>> = iconPackManager.installed

    private val _uiState = MutableStateFlow(AddAccountUiState())
    val uiState: StateFlow<AddAccountUiState> = _uiState.asStateFlow()

    val availableGroups: StateFlow<List<String>> = listGroupsUseCase()
        .map { groups -> groups.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            combine(
                _uiState.map { it.issuer }.distinctUntilChanged(),
                installedPacks,
            ) { issuer, packs -> issuer to packs }
                .collect { (issuer, packs) -> applyIconSuggestion(issuer, packs) }
        }
    }

    private fun applyIconSuggestion(issuer: String, packs: List<InstalledIconPack>) {
        if (_uiState.value.iconExplicitlySet) return
        val match = packs.firstNotNullOfOrNull { pack ->
            pack.suggestionsFor(issuer)
                .firstOrNull { it.matchType == IconMatchType.EXACT || it.matchType == IconMatchType.NORMAL }
                ?.let { pack to it.icon }
        }
        if (match != null) {
            val (pack, icon) = match
            val path =
                iconPackManager.iconFile(pack.pack.uuid, icon.filename)?.absolutePath ?: return
            _uiState.update {
                if (it.iconExplicitlySet) it
                else it.copy(
                    customIconBytes = null,
                    iconPackId = pack.pack.uuid,
                    iconPackFile = icon.filename,
                    packIconPath = path,
                )
            }
        } else {
            _uiState.update {
                if (it.iconExplicitlySet || it.packIconPath == null) it
                else it.copy(iconPackId = null, iconPackFile = null, packIconPath = null)
            }
        }
    }

    fun suppressLock() = lockManager.suppressNextForeground()

    fun onQrScanned(rawValue: String) {
        val account = OtpAuthParser.parse(rawValue)
        if (account != null) {
            _uiState.update { it.copy(parsedAccount = account, error = null) }
        } else {
            _uiState.update { it.copy(error = context.getString(R.string.add_invalid_qr_code)) }
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

    fun pickCustomIcon(uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { IconImageUtil.loadAndResize(context, uri) }
            if (bytes != null) _uiState.update {
                it.copy(
                    customIconBytes = bytes,
                    iconPackId = null,
                    iconPackFile = null,
                    packIconPath = null,
                    iconExplicitlySet = true,
                )
            }
        }
    }

    fun pickPackIcon(packUuid: String, filename: String) {
        val path = iconPackManager.iconFile(packUuid, filename)?.absolutePath ?: return
        _uiState.update {
            it.copy(
                customIconBytes = null,
                iconPackId = packUuid,
                iconPackFile = filename,
                packIconPath = path,
                iconExplicitlySet = true,
            )
        }
    }

    fun clearIcon() = _uiState.update {
        it.copy(
            customIconBytes = null,
            iconPackId = null,
            iconPackFile = null,
            packIconPath = null,
            iconExplicitlySet = true,
        )
    }

    fun saveAccount(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isSaving) return
        if (state.secret.isBlank()) {
            _uiState.update { it.copy(error = context.getString(R.string.add_error_secret_required)) }
            return
        }
        val cleanedSecret = state.secret.trim().uppercase().replace(" ", "").trimEnd('=')
        if (!cleanedSecret.matches(Regex("[A-Z2-7]+"))) {
            _uiState.update { it.copy(error = context.getString(R.string.add_error_invalid_secret)) }
            return
        }
        if (state.accountName.isBlank()) {
            _uiState.update { it.copy(error = context.getString(R.string.add_error_account_name_required)) }
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
                        groups = state.groups,
                        customIconBytes = state.customIconBytes,
                        iconPackId = state.iconPackId,
                        iconPackFile = state.iconPackFile,
                    ),
                )
            }.onSuccess { onSuccess() }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            error = throwable.message
                                ?: context.getString(R.string.add_error_save_failed),
                            isSaving = false,
                        )
                    }
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
    val groups: List<String> = emptyList(),
    val parsedAccount: OtpAccount? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
    val customIconBytes: ByteArray? = null,
    val iconPackId: String? = null,
    val iconPackFile: String? = null,
    val packIconPath: String? = null,
    val iconExplicitlySet: Boolean = false,
) {
    val hasIcon: Boolean get() = customIconBytes != null || packIconPath != null
    val hasSuggestedIcon: Boolean get() = !iconExplicitlySet && packIconPath != null
}
