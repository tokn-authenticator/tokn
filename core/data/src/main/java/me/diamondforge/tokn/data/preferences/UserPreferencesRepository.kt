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
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.userPrefsDataStore

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
    }

    val autoLockTimeoutSeconds: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.AUTO_LOCK_TIMEOUT] ?: 60
    }

    val biometricEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.BIOMETRIC_ENABLED] ?: true
    }

    val screenshotsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SCREENSHOTS_ENABLED] ?: false
    }

    val encryptionEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ENCRYPTION_ENABLED] ?: false
    }

    val onboardingDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_DONE] ?: false
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

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_LOCK_TIMEOUT = intPreferencesKey("auto_lock_timeout")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val SCREENSHOTS_ENABLED = booleanPreferencesKey("screenshots_enabled")
        val ENCRYPTION_ENABLED = booleanPreferencesKey("encryption_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}

enum class ThemeMode { LIGHT, DARK, SYSTEM }
