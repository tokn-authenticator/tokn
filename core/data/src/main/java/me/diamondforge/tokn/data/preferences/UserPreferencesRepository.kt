package me.diamondforge.tokn.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.diamondforge.tokn.domain.model.AccountSort
import me.diamondforge.tokn.domain.model.TapBehavior
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
open class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.userPrefsDataStore

    open val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
    }

    open val autoLockTimeoutSeconds: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.AUTO_LOCK_TIMEOUT] ?: 60
    }

    open val biometricEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.BIOMETRIC_ENABLED] ?: true
    }

    open val screenshotsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SCREENSHOTS_ENABLED] ?: false
    }

    open val encryptionEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ENCRYPTION_ENABLED] ?: false
    }

    open val tapToRevealEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.TAP_TO_REVEAL_ENABLED] ?: false
    }

    open val dynamicColorEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.DYNAMIC_COLOR_ENABLED] ?: true
    }

    open val onboardingDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_DONE] ?: false
    }

    open val lastSyncMethod: Flow<SyncMethod> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_SYNC_METHOD]
            ?.let { runCatching { SyncMethod.valueOf(it) }.getOrNull() }
            ?: SyncMethod.LAN
    }

    open val accountSort: Flow<AccountSort> = dataStore.data.map { prefs ->
        prefs[Keys.ACCOUNT_SORT]
            ?.let { runCatching { AccountSort.valueOf(it) }.getOrNull() }
            ?: AccountSort.CUSTOM
    }

    open val tapBehavior: Flow<TapBehavior> = dataStore.data.map { prefs ->
        prefs[Keys.TAP_BEHAVIOR]
            ?.let { runCatching { TapBehavior.valueOf(it) }.getOrNull() }
            ?: TapBehavior.SINGLE
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAutoLockTimeout(seconds: Int) {
        dataStore.edit { it[Keys.AUTO_LOCK_TIMEOUT] = seconds }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setScreenshotsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SCREENSHOTS_ENABLED] = enabled }
    }

    suspend fun setEncryptionEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ENCRYPTION_ENABLED] = enabled }
    }

    suspend fun setTapToRevealEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.TAP_TO_REVEAL_ENABLED] = enabled }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR_ENABLED] = enabled }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setLastSyncMethod(method: SyncMethod) {
        dataStore.edit { it[Keys.LAST_SYNC_METHOD] = method.name }
    }

    suspend fun setAccountSort(sort: AccountSort) {
        dataStore.edit { it[Keys.ACCOUNT_SORT] = sort.name }
    }

    suspend fun setTapBehavior(behavior: TapBehavior) {
        dataStore.edit { it[Keys.TAP_BEHAVIOR] = behavior.name }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_LOCK_TIMEOUT = intPreferencesKey("auto_lock_timeout")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val SCREENSHOTS_ENABLED = booleanPreferencesKey("screenshots_enabled")
        val ENCRYPTION_ENABLED = booleanPreferencesKey("encryption_enabled")
        val TAP_TO_REVEAL_ENABLED = booleanPreferencesKey("tap_to_reveal_enabled")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val LAST_SYNC_METHOD = stringPreferencesKey("last_sync_method")
        val ACCOUNT_SORT = stringPreferencesKey("account_sort")
        val TAP_BEHAVIOR = stringPreferencesKey("tap_behavior")
    }
}

enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class SyncMethod { LAN, WFD, QR }
