package me.diamondforge.tokn.data.icon

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import me.diamondforge.tokn.data.preferences.AppPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

data class IconPackState(
    val pack: InstalledIconPack,
    val enabled: Boolean,
)

@Singleton
class IconPackRegistry @Inject constructor(
    private val manager: IconPackManager,
    private val appPreferences: AppPreferencesRepository,
) {
    val orderedPacks: Flow<List<IconPackState>> = combine(
        manager.installed,
        appPreferences.disabledIconPacks,
        appPreferences.iconPackOrder,
    ) { installed, disabled, order ->
        orderPacks(installed, order).map { IconPackState(it, it.pack.uuid !in disabled) }
    }

    val activePacks: Flow<List<InstalledIconPack>> = orderedPacks.map { states ->
        states.filter { it.enabled }.map { it.pack }
    }

    suspend fun setEnabled(uuid: String, enabled: Boolean) =
        appPreferences.setIconPackEnabled(uuid, enabled)

    suspend fun setOrder(uuids: List<String>) = appPreferences.setIconPackOrder(uuids)

    suspend fun forget(uuid: String) = appPreferences.forgetIconPack(uuid)

    companion object {
        fun orderPacks(
            packs: List<InstalledIconPack>,
            order: List<String>,
        ): List<InstalledIconPack> {
            val rank = order.withIndex().associate { (index, uuid) -> uuid to index }
            return packs.sortedWith(
                compareBy<InstalledIconPack> { rank[it.pack.uuid] ?: Int.MAX_VALUE }
                    .thenBy { it.pack.name.lowercase() }
                    .thenBy { it.pack.uuid },
            )
        }
    }
}
