package me.diamondforge.tokn.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.diamondforge.tokn.audit.AuditEventType
import me.diamondforge.tokn.audit.AuditLogger
import me.diamondforge.tokn.audit.NoopAuditLogger
import me.diamondforge.tokn.domain.usecase.GetTrashedAccountsUseCase
import me.diamondforge.tokn.domain.usecase.PurgeAccountsUseCase
import me.diamondforge.tokn.domain.usecase.PurgeExpiredTrashUseCase
import me.diamondforge.tokn.domain.usecase.PurgeExpiredTrashUseCase.Companion.RETENTION_MS
import me.diamondforge.tokn.domain.usecase.RestoreAccountsUseCase
import javax.inject.Inject

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    getTrashedAccountsUseCase: GetTrashedAccountsUseCase,
    private val restoreAccountsUseCase: RestoreAccountsUseCase,
    private val purgeAccountsUseCase: PurgeAccountsUseCase,
    private val purgeExpiredTrashUseCase: PurgeExpiredTrashUseCase,
    private val auditLogger: AuditLogger = NoopAuditLogger,
) : ViewModel() {

    init {
        viewModelScope.launch { purgeExpiredTrashUseCase() }
    }

    val uiState: StateFlow<RecycleBinUiState> = getTrashedAccountsUseCase()
        .map { trashed ->
            val now = System.currentTimeMillis()
            RecycleBinUiState(
                items = trashed.map { account ->
                    TrashedRow(
                        id = account.id,
                        issuer = account.issuer,
                        accountName = account.accountName,
                        remainingMillis = (account.deletedAt + RETENTION_MS - now).coerceAtLeast(0),
                    )
                },
                isLoading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecycleBinUiState(isLoading = true),
        )

    fun restore(id: Long) {
        viewModelScope.launch { restoreAccountsUseCase(setOf(id)) }
    }

    fun deleteForever(id: Long) {
        viewModelScope.launch { purgeAccountsUseCase(setOf(id)) }
    }

    fun emptyBin() {
        val ids = uiState.value.items.map { it.id }.toSet()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            purgeAccountsUseCase(ids)
            auditLogger.log(AuditEventType.RECYCLE_BIN_EMPTIED, detail = ids.size.toString())
        }
    }
}

data class RecycleBinUiState(
    val items: List<TrashedRow> = emptyList(),
    val isLoading: Boolean = false,
)

data class TrashedRow(
    val id: Long,
    val issuer: String,
    val accountName: String,
    val remainingMillis: Long,
)
