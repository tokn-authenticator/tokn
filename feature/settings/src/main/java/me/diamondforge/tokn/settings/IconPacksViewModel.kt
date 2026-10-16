package me.diamondforge.tokn.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
import me.diamondforge.tokn.data.icon.IconPackRegistry
import me.diamondforge.tokn.data.icon.InstallResult
import me.diamondforge.tokn.data.icon.InstalledIconPack
import me.diamondforge.tokn.data.icon.bestAutoMatch
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.UpdateAccountUseCase
import javax.inject.Inject

@HiltViewModel
class IconPacksViewModel @Inject constructor(
    private val iconPackManager: IconPackManager,
    private val iconPackRegistry: IconPackRegistry,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val updateAccountUseCase: UpdateAccountUseCase,
) : ViewModel() {

    private val _ephemeral = MutableStateFlow(EphemeralState())

    private val packUsageCounts: Flow<Map<String, Int>> =
        getAccountsUseCase().map { accounts ->
            accounts.asSequence()
                .mapNotNull { it.iconPackId }
                .groupingBy { it }
                .eachCount()
        }

    val uiState: StateFlow<IconPacksUiState> = combine(
        iconPackRegistry.orderedPacks,
        packUsageCounts,
        _ephemeral,
    ) { packs, usage, ephemeral ->
        IconPacksUiState(
            rows = packs.map { state ->
                IconPackRow(
                    pack = state.pack,
                    enabled = state.enabled,
                    usedBy = usage[state.pack.pack.uuid] ?: 0,
                )
            },
            importProgress = ephemeral.importProgress,
            importSummary = ephemeral.importSummary,
            autoMatch = ephemeral.autoMatch,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IconPacksUiState())

    fun importPacks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _ephemeral.update {
            it.copy(importProgress = ImportProgress(0, uris.size), importSummary = null)
        }
        viewModelScope.launch {
            val installed = mutableListOf<InstalledIconPack>()
            val failures = mutableListOf<String>()
            uris.forEachIndexed { index, uri ->
                _ephemeral.update {
                    it.copy(importProgress = ImportProgress(index + 1, uris.size))
                }
                when (val result = iconPackManager.install(uri)) {
                    is InstallResult.Success -> {
                        installed += result.pack
                        iconPackRegistry.setEnabled(result.pack.pack.uuid, enabled = true)
                    }

                    InstallResult.MissingPackJson -> failures += "pack.json missing"
                    is InstallResult.InvalidPackJson -> failures += result.reason
                    is InstallResult.Failed -> failures += result.reason
                }
            }
            val proposal = if (installed.isEmpty()) null else computeAutoMatch(installed)
            _ephemeral.update {
                it.copy(
                    importProgress = null,
                    importSummary = ImportSummary(installed.size, failures.toList()),
                    autoMatch = proposal?.takeIf { p -> p.isNotEmpty },
                )
            }
        }
    }

    fun setPackEnabled(uuid: String, enabled: Boolean) {
        viewModelScope.launch { iconPackRegistry.setEnabled(uuid, enabled) }
    }

    fun reorder(uuids: List<String>) {
        viewModelScope.launch { iconPackRegistry.setOrder(uuids) }
    }

    fun uninstall(uuid: String) {
        viewModelScope.launch {
            iconPackManager.uninstall(uuid)
            iconPackRegistry.forget(uuid)
        }
    }

    fun applyAutoMatch(includeReplacements: Boolean) {
        val pending = _ephemeral.value.autoMatch ?: return
        _ephemeral.update { it.copy(autoMatch = null) }
        val toWrite = pending.fresh + if (includeReplacements) pending.replacements else emptyList()
        if (toWrite.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            toWrite.forEach { assignment ->
                updateAccountUseCase(
                    assignment.account.copy(
                        iconPackId = assignment.packUuid,
                        iconPackFile = assignment.filename,
                    ),
                )
            }
        }
    }

    fun dismissAutoMatch() {
        _ephemeral.update { it.copy(autoMatch = null) }
    }

    fun clearImportSummary() {
        _ephemeral.update { it.copy(importSummary = null) }
    }

    private suspend fun computeAutoMatch(newPacks: List<InstalledIconPack>): AutoMatchProposal {
        val newUuids = newPacks.mapTo(mutableSetOf()) { it.pack.uuid }
        val ranked = iconPackRegistry.orderedPacks.first()
            .filter { it.enabled && it.pack.pack.uuid in newUuids }
            .map { it.pack }
        val accounts = withContext(Dispatchers.IO) { getAccountsUseCase().first() }

        val fresh = mutableListOf<IconAssignment>()
        val replacements = mutableListOf<IconAssignment>()
        for (account in accounts) {
            if (account.customIconBytes != null) continue
            val (pack, icon) = bestAutoMatch(ranked, account.issuer) ?: continue
            val uuid = pack.pack.uuid
            if (account.iconPackId == uuid && account.iconPackFile == icon.filename) continue
            val assignment = IconAssignment(account, uuid, icon.filename)
            val currentPackId = account.iconPackId
            val currentFile = account.iconPackFile
            val hasVisibleIcon = currentPackId != null && currentFile != null &&
                    iconPackManager.iconFile(currentPackId, currentFile) != null
            if (hasVisibleIcon) replacements += assignment else fresh += assignment
        }
        return AutoMatchProposal(
            packNames = ranked.map { it.pack.name },
            fresh = fresh,
            replacements = replacements,
        )
    }
}

data class IconPacksUiState(
    val rows: List<IconPackRow> = emptyList(),
    val importProgress: ImportProgress? = null,
    val importSummary: ImportSummary? = null,
    val autoMatch: AutoMatchProposal? = null,
) {
    val isImporting: Boolean get() = importProgress != null
}

data class IconPackRow(
    val pack: InstalledIconPack,
    val enabled: Boolean,
    val usedBy: Int,
) {
    val uuid: String get() = pack.pack.uuid
}

data class ImportProgress(val current: Int, val total: Int)

data class ImportSummary(val imported: Int, val failures: List<String>)

private data class EphemeralState(
    val importProgress: ImportProgress? = null,
    val importSummary: ImportSummary? = null,
    val autoMatch: AutoMatchProposal? = null,
)

data class IconAssignment(
    val account: OtpAccount,
    val packUuid: String,
    val filename: String,
)

data class AutoMatchProposal(
    val packNames: List<String>,
    val fresh: List<IconAssignment>,
    val replacements: List<IconAssignment>,
) {
    val isNotEmpty: Boolean get() = fresh.isNotEmpty() || replacements.isNotEmpty()
}
