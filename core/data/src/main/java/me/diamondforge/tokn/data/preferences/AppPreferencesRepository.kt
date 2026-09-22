package me.diamondforge.tokn.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

    open val disabledIconPacks: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[Keys.DISABLED_ICON_PACKS] ?: emptySet()
    }

    open val iconPackOrder: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[Keys.ICON_PACK_ORDER]
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    open suspend fun setIconPackEnabled(uuid: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.DISABLED_ICON_PACKS] ?: emptySet()
            prefs[Keys.DISABLED_ICON_PACKS] =
                if (enabled) current - uuid else current + uuid
        }
    }

    open suspend fun setIconPackOrder(uuids: List<String>) {
        dataStore.edit { it[Keys.ICON_PACK_ORDER] = uuids.joinToString(",") }
    }

    open suspend fun forgetIconPack(uuid: String) {
        dataStore.edit { prefs ->
            prefs[Keys.DISABLED_ICON_PACKS] = (prefs[Keys.DISABLED_ICON_PACKS] ?: emptySet()) - uuid
            val order = prefs[Keys.ICON_PACK_ORDER]
                ?.split(',')
                ?.filter { it.isNotBlank() && it != uuid }
                .orEmpty()
            if (order.isEmpty()) prefs.remove(Keys.ICON_PACK_ORDER)
            else prefs[Keys.ICON_PACK_ORDER] = order.joinToString(",")
        }
    }

    private object Keys {
        val ICON_FETCH_ENABLED = booleanPreferencesKey("icon_fetch_enabled")
        val DISABLED_ICON_PACKS = stringSetPreferencesKey("disabled_icon_packs")
        val ICON_PACK_ORDER = stringPreferencesKey("icon_pack_order")
    }
}
