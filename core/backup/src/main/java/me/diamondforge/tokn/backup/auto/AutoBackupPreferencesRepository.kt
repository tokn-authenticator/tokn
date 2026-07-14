package me.diamondforge.tokn.backup.auto

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.autoBackupPrefsDataStore: DataStore<Preferences> by preferencesDataStore("auto_backup_preferences")

@Singleton
open class AutoBackupPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.autoBackupPrefsDataStore)

    open val enabled: Flow<Boolean> = dataStore.data.map { it[Keys.ENABLED] ?: false }

    open val mode: Flow<AutoBackupStrategy> = dataStore.data.map { prefs ->
        prefs[Keys.MODE]?.let { runCatching { AutoBackupStrategy.valueOf(it) }.getOrNull() }
            ?: AutoBackupStrategy.ROTATING
    }

    open val location: Flow<String?> = dataStore.data.map { it[Keys.LOCATION]?.ifBlank { null } }

    open val encrypt: Flow<Boolean> = dataStore.data.map { it[Keys.ENCRYPT] ?: true }

    open val passwordWrapped: Flow<String?> =
        dataStore.data.map { it[Keys.PASSWORD_WRAPPED]?.ifBlank { null } }

    open val versionsToKeep: Flow<Int> = dataStore.data.map { it[Keys.VERSIONS_TO_KEEP] ?: 5 }

    open val lastBackupHash: Flow<String?> =
        dataStore.data.map { it[Keys.LAST_BACKUP_HASH]?.ifBlank { null } }

    open val lastResultAt: Flow<Long> = dataStore.data.map { it[Keys.LAST_RESULT_AT] ?: 0L }

    open val lastError: Flow<String?> = dataStore.data.map { it[Keys.LAST_ERROR]?.ifBlank { null } }

    open suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLED] = enabled }
    }

    open suspend fun setMode(mode: AutoBackupStrategy) {
        dataStore.edit { it[Keys.MODE] = mode.name }
    }

    open suspend fun setLocation(uri: String?) {
        dataStore.edit {
            if (uri.isNullOrBlank()) it.remove(Keys.LOCATION) else it[Keys.LOCATION] = uri
        }
    }

    open suspend fun setEncrypt(encrypt: Boolean) {
        dataStore.edit { it[Keys.ENCRYPT] = encrypt }
    }

    open suspend fun setPasswordWrapped(wrapped: String?) {
        dataStore.edit {
            if (wrapped.isNullOrBlank()) it.remove(Keys.PASSWORD_WRAPPED)
            else it[Keys.PASSWORD_WRAPPED] = wrapped
        }
    }

    open suspend fun setVersionsToKeep(count: Int) {
        dataStore.edit { it[Keys.VERSIONS_TO_KEEP] = count }
    }

    open suspend fun setLastResult(hash: String?, timestamp: Long, error: String?) {
        dataStore.edit {
            if (hash.isNullOrBlank()) it.remove(Keys.LAST_BACKUP_HASH)
            else it[Keys.LAST_BACKUP_HASH] = hash
            it[Keys.LAST_RESULT_AT] = timestamp
            if (error.isNullOrBlank()) it.remove(Keys.LAST_ERROR) else it[Keys.LAST_ERROR] = error
        }
    }

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val MODE = stringPreferencesKey("mode")
        val LOCATION = stringPreferencesKey("location")
        val ENCRYPT = booleanPreferencesKey("encrypt")
        val PASSWORD_WRAPPED = stringPreferencesKey("password_wrapped")
        val VERSIONS_TO_KEEP = intPreferencesKey("versions_to_keep")
        val LAST_BACKUP_HASH = stringPreferencesKey("last_backup_hash")
        val LAST_RESULT_AT = longPreferencesKey("last_result_at")
        val LAST_ERROR = stringPreferencesKey("last_error")
    }
}
