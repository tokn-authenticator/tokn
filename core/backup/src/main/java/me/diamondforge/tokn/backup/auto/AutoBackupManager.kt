package me.diamondforge.tokn.backup.auto

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.diamondforge.tokn.audit.AuditEventType
import me.diamondforge.tokn.audit.AuditLogger
import me.diamondforge.tokn.audit.NoopAuditLogger
import me.diamondforge.tokn.backup.EncryptedBackupManager
import me.diamondforge.tokn.backup.serializeAccountsToJson
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.security.KeystoreManager
import me.diamondforge.tokn.security.vault.VaultSession
import me.diamondforge.tokn.security.vault.VaultState
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AutoBackupManager @Inject constructor(
    private val prefs: AutoBackupPreferencesRepository,
    private val writer: AutoBackupWriter,
    private val getAccounts: Provider<GetAccountsUseCase>,
    private val encryptedBackupManager: EncryptedBackupManager,
    private val keystoreManager: KeystoreManager,
    private val session: VaultSession,
    private val auditLogger: AuditLogger = NoopAuditLogger,
) {
    suspend fun setPassword(password: String) {
        prefs.setPasswordWrapped(keystoreManager.encrypt(password.toByteArray(Charsets.UTF_8)))
    }

    suspend fun backupNow(force: Boolean = false): AutoBackupResult {
        if (!prefs.enabled.first()) return AutoBackupResult.Skipped("disabled")
        val location = prefs.location.first() ?: return AutoBackupResult.Skipped("no_location")
        if (!session.isUnlocked) return AutoBackupResult.Skipped("locked")

        return try {
            val json = serializeAccountsToJson(getAccounts.get().invoke().first())
            val hash = sha256(json)
            if (!force && hash == prefs.lastBackupHash.first()) {
                return AutoBackupResult.Skipped("unchanged")
            }
            val encrypt = prefs.encrypt.first()
            val bytes = if (encrypt) {
                val wrapped = prefs.passwordWrapped.first()
                    ?: return AutoBackupResult.Failure("no_password")
                val password = keystoreManager.decrypt(wrapped).toString(Charsets.UTF_8)
                encryptedBackupManager.encrypt(json, password)
            } else {
                json.toByteArray(Charsets.UTF_8)
            }
            val now = System.currentTimeMillis()
            writer.write(Uri.parse(location), bytes, encrypt, prefs.versionsToKeep.first(), now)
            prefs.setLastResult(hash, now, null)
            auditLogger.log(AuditEventType.AUTO_BACKUP_CREATED)
            AutoBackupResult.Success
        } catch (e: Exception) {
            prefs.setLastResult(
                prefs.lastBackupHash.first(),
                System.currentTimeMillis(),
                e.message ?: e.javaClass.simpleName,
            )
            AutoBackupResult.Failure(e.message ?: "backup_failed")
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun observe(scope: CoroutineScope) {
        session.state
            .flatMapLatest { state ->
                if (state == VaultState.UNLOCKED) {
                    getAccounts.get().invoke().debounce(DEBOUNCE_MS)
                } else {
                    emptyFlow()
                }
            }
            .catch { }
            .onEach { backupNow() }
            .launchIn(scope)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val DEBOUNCE_MS = 2500L
    }
}
