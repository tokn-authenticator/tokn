package me.diamondforge.tokn.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diamondforge.tokn.data.icon.IconPackManager
import me.diamondforge.tokn.data.icon.InstallResult
import me.diamondforge.tokn.data.icon.InstalledIconPack
import me.diamondforge.tokn.data.icon.suggestionsFor
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.UpdateAccountUseCase
import javax.inject.Inject

@HiltViewModel
class IconPacksViewModel @Inject constructor(
    private val iconPackManager: IconPackManager,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val updateAccountUseCase: UpdateAccountUseCase,
) : ViewModel() {

    private val _ephemeral = MutableStateFlow(EphemeralState())

    private val packUsageCounts: kotlinx.coroutines.flow.Flow<Map<String, Int>> =
        getAccountsUseCase().map { accounts ->
            accounts.asSequence()
                .mapNotNull { it.iconPackId }
                .groupingBy { it }
                .eachCount()
        }

    val uiState: StateFlow<IconPacksUiState> = combine(
        iconPackManager.installed,
        packUsageCounts,
        _ephemeral,
    ) { packs, usage, ephemeral ->
        IconPacksUiState(
            packs = packs,
            usageByUuid = usage,
            isImporting = ephemeral.isImporting,
            importError = ephemeral.importError,
            autoMatch = ephemeral.autoMatch,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IconPacksUiState())

    fun importPack(uri: Uri) {
        _ephemeral.update { it.copy(isImporting = true, importError = null) }
        viewModelScope.launch {
            val result = iconPackManager.install(uri)
            when (result) {
                is InstallResult.Success -> {
                    val match = computeAutoMatch(result.pack)
                    _ephemeral.update {
                        it.copy(
                            isImporting = false,
                            importError = null,
                            autoMatch = match.takeIf { m -> m.assignments.isNotEmpty() },
                        )
                    }
                }

                InstallResult.MissingPackJson ->
                    _ephemeral.update {
                        it.copy(
                            isImporting = false,
                            importError = "pack.json missing"
                        )
                    }

                is InstallResult.InvalidPackJson ->
                    _ephemeral.update { it.copy(isImporting = false, importError = result.reason) }

                is InstallResult.Failed ->
                    _ephemeral.update { it.copy(isImporting = false, importError = result.reason) }
            }
        }
    }

    fun uninstall(uuid: String) {
        viewModelScope.launch { iconPackManager.uninstall(uuid) }
    }

    fun applyAutoMatch() {
        val pending = _ephemeral.value.autoMatch ?: return
        _ephemeral.update { it.copy(autoMatch = null) }
        viewModelScope.launch(Dispatchers.IO) {
            pending.assignments.forEach { (account, icon) ->
                updateAccountUseCase(
                    account.copy(
                        iconPackId = pending.packUuid,
                        iconPackFile = icon,
                        customIconBytes = null,
                    ),
                )
            }
        }
    }

    fun dismissAutoMatch() {
        _ephemeral.update { it.copy(autoMatch = null) }
    }

    fun clearImportError() {
        _ephemeral.update { it.copy(importError = null) }
    }

    private suspend fun computeAutoMatch(installed: InstalledIconPack): AutoMatchProposal {
        val accounts = withContext(Dispatchers.IO) {
            getAccountsUseCase().first()
        }
        val assignments = accounts.mapNotNull { account ->
            val best =
                installed.suggestionsFor(account.issuer).firstOrNull() ?: return@mapNotNull null
            if (account.iconPackId == installed.pack.uuid &&
                account.iconPackFile == best.icon.filename
            ) return@mapNotNull null
            if (account.customIconBytes != null) return@mapNotNull null
            account to best.icon.filename
        }
        return AutoMatchProposal(
            packUuid = installed.pack.uuid,
            packName = installed.pack.name,
            assignments = assignments,
        )
    }
}

data class IconPacksUiState(
    val packs: List<InstalledIconPack> = emptyList(),
    val usageByUuid: Map<String, Int> = emptyMap(),
    val isImporting: Boolean = false,
    val importError: String? = null,
    val autoMatch: AutoMatchProposal? = null,
)

private data class EphemeralState(
    val isImporting: Boolean = false,
    val importError: String? = null,
    val autoMatch: AutoMatchProposal? = null,
)

data class AutoMatchProposal(
    val packUuid: String,
    val packName: String,
    val assignments: List<Pair<OtpAccount, String>>,
)
