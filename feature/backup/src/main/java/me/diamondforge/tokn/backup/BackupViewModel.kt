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
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

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
            }.onFailure { e ->
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
                    // Detect if this is accidentally an Aegis file
                    if (looksLikeAegisBackup(raw))
                        throw BackupException(R.string.error_looks_like_aegis)
                    // Detect unencrypted Tokn backup
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
            }.onSuccess { count ->
                _uiState.update { it.copy(isLoading = false, importedCount = count) }
            }.onFailure { e ->
                val error = when (e) {
                    is BackupException -> BackupError(R.string.import_error_title, e.messageRes)
                    else -> BackupError(R.string.import_error_title, R.string.error_generic, e.message)
                }
                _uiState.update { it.copy(isLoading = false, error = error) }
            }
        }
    }

    fun importAegisBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                        ?: throw BackupException(R.string.error_file_not_readable)
                }
            }.onSuccess { raw ->
                when {
                    !isValidJson(raw) ->
                        _uiState.update { it.copy(isLoading = false, error = BackupError(R.string.import_error_title, R.string.error_not_json)) }
                    !looksLikeAegisBackup(raw) ->
                        _uiState.update { it.copy(isLoading = false, error = BackupError(R.string.import_error_title, R.string.error_not_aegis_backup)) }
                    isAegisEncrypted(raw) ->
                        _uiState.update { it.copy(isLoading = false, pendingEncryptedAegisUri = uri) }
                    else -> {
                        // parseAegisJson + DB writes off the main thread.
                        runCatching {
                            withContext(Dispatchers.IO) {
                                importDeduplicated(parseAegisJson(raw))
                            }
                        }
                            .onSuccess { count ->
                                _uiState.update { it.copy(isLoading = false, importedCount = count) }
                            }
                            .onFailure {
                                _uiState.update { it.copy(isLoading = false, error = BackupError(R.string.import_error_title, R.string.error_not_aegis_backup)) }
                            }
                    }
                }
            }.onFailure { e ->
                val error = when (e) {
                    is BackupException -> BackupError(R.string.import_error_title, e.messageRes)
                    else -> BackupError(R.string.import_error_title, R.string.error_generic, e.message)
                }
                _uiState.update { it.copy(isLoading = false, error = error) }
            }
        }
    }

    fun importAegisWithPassword(password: String) {
        val uri = _uiState.value.pendingEncryptedAegisUri ?: return
        _uiState.update { it.copy(pendingEncryptedAegisUri = null, isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                        ?: throw BackupException(R.string.error_file_not_readable)
                    val dbJson = decryptAegisEncrypted(json, password)
                    val accounts = parseAegisDb(dbJson)
                    importDeduplicated(accounts)
                }
            }.onSuccess { count ->
                _uiState.update { it.copy(isLoading = false, importedCount = count) }
            }.onFailure { e ->
                val error = when (e) {
                    is BackupException -> BackupError(R.string.import_error_title, e.messageRes)
                    is javax.crypto.AEADBadTagException,
                    is javax.crypto.BadPaddingException ->
                        BackupError(R.string.import_error_title, R.string.error_wrong_password)
                    else -> BackupError(R.string.import_error_title, R.string.error_generic, e.message)
                }
                _uiState.update { it.copy(isLoading = false, error = error) }
            }
        }
    }

    /**
     * Inserts every incoming account whose normalized secret is not already
     * present locally. Re-importing the same backup is a no-op rather than
     * a silent duplication. Mirrors the dedup logic used by sync transfers
     * so the two import paths behave consistently.
     */
    private suspend fun importDeduplicated(incoming: List<OtpAccount>): Int {
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
        return imported
    }

    private fun String.normalize(): String =
        replace(" ", "").replace("-", "").uppercase()

    fun cancelEncryptedImport() {
        _uiState.update { it.copy(pendingEncryptedAegisUri = null) }
    }

    fun suppressLock() = lockManager.suppressNextForeground()

    fun clearMessages() = _uiState.update { it.copy(error = null, exportSuccess = false, importedCount = null) }

    private class BackupException(val messageRes: Int) : Exception()

    private fun isValidJson(raw: String): Boolean =
        runCatching { JSONObject(raw); true }.getOrDefault(false)

    private fun isUnencryptedToknBackup(raw: String): Boolean =
        runCatching { JSONObject(raw).let { it.has("accounts") && it.has("version") } }.getOrDefault(false)

    private fun looksLikeAegisBackup(raw: String): Boolean =
        runCatching { JSONObject(raw).let { it.has("db") && it.has("version") } }.getOrDefault(false)

    private fun isAegisEncrypted(json: String): Boolean =
        runCatching { JSONObject(json).opt("db") is String }.getOrDefault(false)

    private fun parseAegisJson(json: String): List<OtpAccount> {
        val db = JSONObject(json).getJSONObject("db")
        return parseAegisDb(db.toString())
    }

    private fun parseAegisDb(dbJson: String): List<OtpAccount> {
        val entries = JSONObject(dbJson).getJSONArray("entries")
        return (0 until entries.length()).mapNotNull { i ->
            val entry = entries.getJSONObject(i)
            val type = when (entry.optString("type").lowercase()) {
                "totp" -> OtpType.TOTP
                "hotp" -> OtpType.HOTP
                else -> return@mapNotNull null
            }
            val info = entry.getJSONObject("info")
            val secret = info.optString("secret").ifBlank { return@mapNotNull null }
            val algo = when (info.optString("algo").uppercase()) {
                "SHA256" -> OtpAlgorithm.SHA256
                "SHA512" -> OtpAlgorithm.SHA512
                else -> OtpAlgorithm.SHA1
            }
            OtpAccount(
                issuer = entry.optString("issuer", ""),
                accountName = entry.optString("name", ""),
                secret = secret,
                algorithm = algo,
                digits = info.optInt("digits", 6),
                period = info.optInt("period", 30),
                counter = info.optLong("counter", 0),
                type = type,
            )
        }
    }

    private fun decryptAegisEncrypted(json: String, password: String): String {
        val root = JSONObject(json)
        val header = root.getJSONObject("header")
        val slots = header.getJSONArray("slots")
        val params = header.getJSONObject("params")

        var masterKey: ByteArray? = null
        for (i in 0 until slots.length()) {
            val slot = slots.getJSONObject(i)
            if (slot.getInt("type") != 1) continue  // type 1 = password-based

            val salt = slot.getString("salt").decodeHex()
            val n = slot.getInt("n")
            val r = slot.getInt("r")
            val p = slot.getInt("p")
            val encryptedKey = slot.getString("key").decodeHex()
            val keyNonce = slot.getJSONObject("key_params").getString("nonce").decodeHex()
            val keyTag = slot.getJSONObject("key_params").getString("tag").decodeHex()

            val derivedKey = org.bouncycastle.crypto.generators.SCrypt.generate(
                password.toByteArray(Charsets.UTF_8), salt, n, r, p, 32,
            )
            runCatching {
                val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    javax.crypto.Cipher.DECRYPT_MODE,
                    javax.crypto.spec.SecretKeySpec(derivedKey, "AES"),
                    javax.crypto.spec.GCMParameterSpec(128, keyNonce),
                )
                masterKey = cipher.doFinal(encryptedKey + keyTag)
            }
            if (masterKey != null) break
        }

        if (masterKey == null) {
            // Check if there were any password slots at all
            val hasPasswordSlot = (0 until slots.length()).any { slots.getJSONObject(it).getInt("type") == 1 }
            throw if (hasPasswordSlot) BackupException(R.string.error_wrong_password)
            else BackupException(R.string.error_aegis_no_password_slot)
        }

        val dbCiphertext = android.util.Base64.decode(root.getString("db"), android.util.Base64.DEFAULT)
        val dbNonce = params.getString("nonce").decodeHex()
        val dbTag = params.getString("tag").decodeHex()

        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(masterKey, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, dbNonce),
        )
        return cipher.doFinal(dbCiphertext + dbTag).toString(Charsets.UTF_8)
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0)
        return ByteArray(length / 2) { i ->
            ((Character.digit(this[i * 2], 16) shl 4) + Character.digit(this[i * 2 + 1], 16)).toByte()
        }
    }

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
    val importedCount: Int? = null,
    val pendingEncryptedAegisUri: Uri? = null,
)

data class BackupError(val titleRes: Int, val messageRes: Int, val messageArg: String? = null)
