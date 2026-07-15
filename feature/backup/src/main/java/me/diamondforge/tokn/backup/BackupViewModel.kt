package me.diamondforge.tokn.backup

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diamondforge.tokn.audit.AuditEventType
import me.diamondforge.tokn.audit.AuditLogger
import me.diamondforge.tokn.audit.NoopAuditLogger
import me.diamondforge.tokn.data.security.VaultAuthGate
import me.diamondforge.tokn.data.security.VaultAuthMode
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.ImportAccountsUseCase
import me.diamondforge.tokn.domain.usecase.ListGroupsUseCase
import me.diamondforge.tokn.importer.ExternalImporter
import me.diamondforge.tokn.importer.ImportOutcome
import me.diamondforge.tokn.importer.ImporterRegistry
import me.diamondforge.tokn.security.LockManager
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val listGroupsUseCase: ListGroupsUseCase,
    private val importAccountsUseCase: ImportAccountsUseCase,
    private val encryptedBackupManager: EncryptedBackupManager,
    private val lockManager: LockManager,
    private val importerRegistry: ImporterRegistry,
    private val vaultAuthGate: VaultAuthGate,
    private val auditLogger: AuditLogger = NoopAuditLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    val externalImporters: List<ExternalImporter> get() = importerRegistry.all()

    suspend fun authMode(): VaultAuthMode = vaultAuthGate.mode()

    suspend fun verifyVaultPassword(password: String): Boolean =
        vaultAuthGate.verifyPassword(password)

    fun exportUnencryptedBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val json = serializeAccountsToJson(
                        getAccountsUseCase().first(),
                        listGroupsUseCase().first(),
                    )
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open output stream")
                }
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, exportSuccess = true) }
                auditLogger.log(AuditEventType.BACKUP_EXPORTED_UNENCRYPTED)
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = BackupError(
                            R.string.export_error_title,
                            R.string.error_file_not_writable
                        )
                    )
                }
            }
        }
    }

    fun exportOtpAuthUriList(uri: Uri) =
        exportText(
            uri,
            AuditEventType.BACKUP_EXPORTED_OTPAUTH_LIST
        ) { serializeAccountsAsOtpAuthUriList(it) }

    fun exportPlainText(uri: Uri) =
        exportText(
            uri,
            AuditEventType.BACKUP_EXPORTED_PLAIN_TEXT
        ) { serializeAccountsAsPlainText(it) }

    private fun exportText(uri: Uri, event: AuditEventType, render: (List<OtpAccount>) -> String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = render(getAccountsUseCase().first())
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open output stream")
                }
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, exportSuccess = true) }
                auditLogger.log(event)
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = BackupError(
                            R.string.export_error_title,
                            R.string.error_file_not_writable,
                        ),
                    )
                }
            }
        }
    }

    fun exportBackup(uri: Uri, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val json = serializeAccountsToJson(
                        getAccountsUseCase().first(),
                        listGroupsUseCase().first(),
                    )
                    encryptedBackupManager.exportToUri(context, uri, json, password)
                }
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, exportSuccess = true) }
                auditLogger.log(AuditEventType.BACKUP_EXPORTED_ENCRYPTED)
            }.onFailure { e ->
                val error = when {
                    e.message?.contains("output stream") == true ->
                        BackupError(R.string.export_error_title, R.string.error_file_not_writable)

                    else ->
                        BackupError(R.string.export_error_title, R.string.error_generic, e.message)
                }
                _uiState.update { it.copy(isLoading = false, error = error) }
            }
        }
    }

    /**
     * Entry point for external-app imports. Reads the file once and either applies the
     * importer the user picked, or auto-detects via the registry when [importerId] is null.
     * Surfaces format-specific outcomes (NeedsPassword / WrongPassword / Unsupported)
     * through [BackupUiState] so the screen can react without knowing the underlying format.
     */
    fun importExternal(uri: Uri, importerId: String? = null, password: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw BackupException(R.string.error_file_not_readable)
                    val importer = importerId?.let(importerRegistry::byId)
                        ?: importerRegistry.detect(raw)
                        ?: throw BackupException(R.string.error_external_not_recognized)
                    importer to raw
                }
            }.onSuccess { (importer, raw) ->
                handleOutcome(uri, importer, importer.parse(raw, password))
            }.onFailure { e ->
                val error = when (e) {
                    is BackupException -> BackupError(R.string.import_error_title, e.messageRes)
                    else -> BackupError(
                        R.string.import_error_title,
                        R.string.error_generic,
                        e.message
                    )
                }
                _uiState.update { it.copy(isLoading = false, error = error) }
            }
        }
    }

    private fun handleOutcome(uri: Uri, importer: ExternalImporter, outcome: ImportOutcome) {
        when (outcome) {
            is ImportOutcome.Success -> {
                viewModelScope.launch {
                    val summary =
                        withContext(Dispatchers.IO) {
                            importAccountsUseCase(outcome.accounts, outcome.declaredGroups)
                        }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            importResult = ImportResult(
                                found = summary.found,
                                imported = summary.imported,
                                unsupportedCount = outcome.unsupportedCount,
                            ),
                            pendingExternal = null,
                        )
                    }
                    auditLogger.log(AuditEventType.VAULT_IMPORTED, detail = importer.displayName)
                }
            }

            ImportOutcome.NeedsPassword -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingExternal = PendingExternalImport(
                            uri,
                            importer.id,
                            importer.displayName
                        ),
                    )
                }
            }

            is ImportOutcome.WrongPassword -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingExternal = PendingExternalImport(
                            uri,
                            importer.id,
                            importer.displayName
                        ),
                        error = BackupError(
                            R.string.import_error_title,
                            R.string.error_wrong_password
                        ),
                    )
                }
            }

            ImportOutcome.Unsupported -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingExternal = null,
                        error = BackupError(
                            R.string.import_error_title,
                            R.string.error_external_unsupported
                        ),
                    )
                }
            }

            is ImportOutcome.Malformed -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingExternal = null,
                        error = BackupError(
                            R.string.import_error_title,
                            R.string.error_external_malformed
                        ),
                    )
                }
            }
        }
    }

    fun cancelExternalImport() {
        _uiState.update { it.copy(pendingExternal = null) }
    }

    fun suppressLock() = lockManager.suppressNextForeground()

    fun clearMessages() =
        _uiState.update { it.copy(error = null, exportSuccess = false, importResult = null) }

    private class BackupException(val messageRes: Int) : Exception()
}

data class BackupUiState(
    val isLoading: Boolean = false,
    val error: BackupError? = null,
    val exportSuccess: Boolean = false,
    val importResult: ImportResult? = null,
    val pendingExternal: PendingExternalImport? = null,
)

data class ImportResult(val found: Int, val imported: Int, val unsupportedCount: Int = 0) {
    val skipped: Int get() = found - imported
}

data class PendingExternalImport(
    val uri: Uri,
    val importerId: String,
    val displayName: String,
)

data class BackupError(val titleRes: Int, val messageRes: Int, val messageArg: String? = null)
