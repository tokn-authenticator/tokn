package me.diamondforge.tokn.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.domain.usecase.AddAccountUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.importer.ExternalImporter
import me.diamondforge.tokn.importer.ImportOutcome
import me.diamondforge.tokn.importer.ImporterRegistry
import me.diamondforge.tokn.security.LockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val addAccountUseCase: AddAccountUseCase,
    private val encryptedBackupManager: EncryptedBackupManager,
    private val lockManager: LockManager,
    private val importerRegistry: ImporterRegistry,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    val externalImporters: List<ExternalImporter> get() = importerRegistry.all()

    fun exportUnencryptedBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val accounts = getAccountsUseCase().first()
                    val json = serializeAccounts(accounts)
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open output stream")
                }
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, exportSuccess = true) }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, error = BackupError(R.string.export_error_title, R.string.error_file_not_writable)) }
            }
        }
    }

    fun exportBackup(uri: Uri, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val accounts = getAccountsUseCase().first()
                    val json = serializeAccounts(accounts)
                    encryptedBackupManager.exportToUri(context, uri, json, password)
                }
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, exportSuccess = true) }
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

    fun importBackup(uri: Uri, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                        ?: throw BackupException(R.string.error_file_not_readable)
                    if (isUnencryptedToknBackup(raw)) {
                        val accounts = runCatching { deserializeAccounts(raw) }
                            .getOrElse { throw BackupException(R.string.error_not_tokn_backup) }
                        return@withContext importDeduplicated(accounts)
                    }
                    if (password.isEmpty())
                        throw BackupException(R.string.error_password_required)
                    val json = runCatching { encryptedBackupManager.importFromUri(context, uri, password) }
                        .getOrElse { e ->
                            throw if (e is javax.crypto.AEADBadTagException || e is javax.crypto.BadPaddingException)
                                BackupException(R.string.error_wrong_password)
                            else
                                BackupException(R.string.error_not_tokn_backup)
                        }
                    val accounts = runCatching { deserializeAccounts(json) }
                        .getOrElse { throw BackupException(R.string.error_not_tokn_backup) }
                    importDeduplicated(accounts)
                }
            }.onSuccess { result ->
                _uiState.update { it.copy(isLoading = false, importResult = result) }
            }.onFailure { e ->
                val error = when (e) {
                    is BackupException -> BackupError(R.string.import_error_title, e.messageRes)
                    else -> BackupError(R.string.import_error_title, R.string.error_generic, e.message)
                }
                _uiState.update { it.copy(isLoading = false, error = error) }
            }
        }
    }

    /**
     * Entry point for external-app imports. Reads the file once and either applies the
     * importer the user picked or — if [importerId] is null — auto-detects via the
     * registry. Surfaces format-specific outcomes (NeedsPassword / WrongPassword /
     * Unsupported) through [BackupUiState] so the screen can react without knowing the
     * underlying format.
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
                    else -> BackupError(R.string.import_error_title, R.string.error_generic, e.message)
                }
                _uiState.update { it.copy(isLoading = false, error = error) }
            }
        }
    }

    private fun handleOutcome(uri: Uri, importer: ExternalImporter, outcome: ImportOutcome) {
        when (outcome) {
            is ImportOutcome.Success -> {
                viewModelScope.launch {
                    val result = withContext(Dispatchers.IO) { importDeduplicated(outcome.accounts) }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            importResult = result,
                            pendingExternal = null,
                        )
                    }
                }
            }
            ImportOutcome.NeedsPassword -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingExternal = PendingExternalImport(uri, importer.id, importer.displayName),
                    )
                }
            }
            is ImportOutcome.WrongPassword -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingExternal = PendingExternalImport(uri, importer.id, importer.displayName),
                        error = BackupError(R.string.import_error_title, R.string.error_wrong_password),
                    )
                }
            }
            ImportOutcome.Unsupported -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingExternal = null,
                        error = BackupError(R.string.import_error_title, R.string.error_external_unsupported),
                    )
                }
            }
            is ImportOutcome.Malformed -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingExternal = null,
                        error = BackupError(R.string.import_error_title, R.string.error_external_malformed),
                    )
                }
            }
        }
    }

    fun cancelExternalImport() {
        _uiState.update { it.copy(pendingExternal = null) }
    }

    /**
     * Mirrors dedup used by sync transfers: only insert accounts whose normalized secret
     * isn't already present. Re-importing the same file becomes a no-op.
     */
    private suspend fun importDeduplicated(incoming: List<OtpAccount>): ImportResult {
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

    fun suppressLock() = lockManager.suppressNextForeground()

    fun clearMessages() = _uiState.update { it.copy(error = null, exportSuccess = false, importResult = null) }

    private class BackupException(val messageRes: Int) : Exception()

    private fun isUnencryptedToknBackup(raw: String): Boolean =
        runCatching { JSONObject(raw).let { it.has("accounts") && it.has("version") } }.getOrDefault(false)

    private fun serializeAccounts(accounts: List<OtpAccount>): String {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject().apply {
                    put("issuer", account.issuer)
                    put("accountName", account.accountName)
                    put("secret", account.secret)
                    put("algorithm", account.algorithm.name)
                    put("digits", account.digits)
                    put("period", account.period)
                    put("counter", account.counter)
                    put("type", account.type.name)
                    put("sortOrder", account.sortOrder)
                    account.group?.let { put("group", it) }
                },
            )
        }
        return JSONObject().apply { put("accounts", array); put("version", 1) }.toString()
    }

    private fun deserializeAccounts(json: String): List<OtpAccount> {
        val root = JSONObject(json)
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

data class BackupUiState(
    val isLoading: Boolean = false,
    val error: BackupError? = null,
    val exportSuccess: Boolean = false,
    val importResult: ImportResult? = null,
    val pendingExternal: PendingExternalImport? = null,
)

data class ImportResult(val found: Int, val imported: Int) {
    val skipped: Int get() = found - imported
}

data class PendingExternalImport(
    val uri: Uri,
    val importerId: String,
    val displayName: String,
)

data class BackupError(val titleRes: Int, val messageRes: Int, val messageArg: String? = null)
