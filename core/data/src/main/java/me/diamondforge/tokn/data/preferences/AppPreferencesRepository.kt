package me.diamondforge.tokn.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appPrefsDataStore: DataStore<Preferences> by preferencesDataStore("app_preferences")

@Singleton
open class AppPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.appPrefsDataStore)

    open val iconFetchEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ICON_FETCH_ENABLED] ?: false
    }

    open suspend fun setIconFetchEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ICON_FETCH_ENABLED] = enabled }
    }

    private object Keys {
        val ICON_FETCH_ENABLED = booleanPreferencesKey("icon_fetch_enabled")
    }
}
