package me.diamondforge.tokn.backup.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diamondforge.tokn.backup.ImportResult
import me.diamondforge.tokn.domain.usecase.AddAccountUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.importer.ImportOutcome
import me.diamondforge.tokn.importer.otpauth.OtpAuthMigrationImporter
import me.diamondforge.tokn.security.LockManager
import javax.inject.Inject

data class MigrationScanUiState(
    val scanned: Int = 0,
    val expectedTotal: Int = 0,
    val isComplete: Boolean = false,
    val isLoading: Boolean = false,
    val crossVaultPending: String? = null,
    val justAcceptedAt: Long = 0L,                // bumps on each successful scan for haptic effect
    val justDuplicate: Long = 0L,                 // bumps on each duplicate so the UI can show a hint
    val invalidScan: Long = 0L,                   // bumps on each non-migration QR
    val result: ImportResult? = null,
    val errorMalformed: Boolean = false,
)

@HiltViewModel
class MigrationScanViewModel @Inject constructor(
    private val importer: OtpAuthMigrationImporter,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val addAccountUseCase: AddAccountUseCase,
    private val lockManager: LockManager,
) : ViewModel() {

    private val session = MigrationScanSession(importer::peekBatch)

    private val _uiState = MutableStateFlow(MigrationScanUiState())
    val uiState: StateFlow<MigrationScanUiState> = _uiState.asStateFlow()

    fun suppressLock() = lockManager.suppressNextForeground()

    fun onScanned(uri: String) {
        when (val event = session.onScanned(uri)) {
            is ScanEvent.Accepted -> emitState(acceptedBump = true)
            ScanEvent.Duplicate -> emitState(duplicateBump = true)
            ScanEvent.NotMigration -> emitState(invalidBump = true)
            is ScanEvent.CrossVault -> _uiState.update { it.copy(crossVaultPending = uri) }
        }
    }

    fun confirmCrossVault() {
        val uri = _uiState.value.crossVaultPending ?: return
        session.replaceWith(uri)
        _uiState.update { it.copy(crossVaultPending = null) }
        emitState(acceptedBump = true)
    }

    fun dismissCrossVault() {
        _uiState.update { it.copy(crossVaultPending = null) }
    }

    fun commit() {
        if (session.state.isEmpty) return
        val payload = session.joinedPayload()
        _uiState.update { it.copy(isLoading = true, errorMalformed = false) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                importer.parse(payload.toByteArray(Charsets.UTF_8), password = null)
            }
            when (outcome) {
                is ImportOutcome.Success -> {
                    val result = withContext(Dispatchers.IO) {
                        importDeduplicated(outcome.accounts)
                    }
                    _uiState.update { it.copy(isLoading = false, result = result) }
                }
                else -> {
                    // Migration parser only returns Success or Malformed. Treat anything
                    // that isn't Success as a "scanner picked up gibberish" hint.
                    _uiState.update { it.copy(isLoading = false, errorMalformed = true) }
                }
            }
        }
    }

    fun clearResult() {
        _uiState.update { it.copy(result = null, errorMalformed = false) }
    }

    private fun emitState(
        acceptedBump: Boolean = false,
        duplicateBump: Boolean = false,
        invalidBump: Boolean = false,
    ) {
        val now = System.nanoTime()
        _uiState.update { prev ->
            prev.copy(
                scanned = session.state.batchesSeen.size,
                expectedTotal = session.state.expectedBatchCount,
                isComplete = session.state.isComplete,
                justAcceptedAt = if (acceptedBump) now else prev.justAcceptedAt,
                justDuplicate = if (duplicateBump) now else prev.justDuplicate,
                invalidScan = if (invalidBump) now else prev.invalidScan,
            )
        }
    }

    private suspend fun importDeduplicated(
        incoming: List<me.diamondforge.tokn.domain.model.OtpAccount>,
    ): ImportResult {
        val existingSecrets = getAccountsUseCase().first()
            .map { it.secret.normalize() }
            .toHashSet()
        var imported = 0
        for (account in incoming) {
            val normalized = account.secret.normalize()
            if (normalized in existingSecrets) continue
            addAccountUseCase(account.copy(id = 0))
            existingSecrets.add(normalized)
            imported++
        }
        return ImportResult(found = incoming.size, imported = imported)
    }

    private fun String.normalize(): String =
        replace(" ", "").replace("-", "").uppercase()
}
