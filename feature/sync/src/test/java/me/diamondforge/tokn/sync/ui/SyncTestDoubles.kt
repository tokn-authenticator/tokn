package me.diamondforge.tokn.sync.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import me.diamondforge.tokn.data.preferences.SyncMethod
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.data.security.VaultAuthGate
import me.diamondforge.tokn.security.BiometricHelper
import me.diamondforge.tokn.security.KeystoreManager
import me.diamondforge.tokn.security.VaultPasswordManager
import me.diamondforge.tokn.security.vault.VaultManager
import me.diamondforge.tokn.security.vault.VaultSession

internal class FakeSyncPreferences(context: Context) : UserPreferencesRepository(context) {
    val lastSync = MutableStateFlow(SyncMethod.LAN)
    override val lastSyncMethod = lastSync
    override suspend fun setLastSyncMethod(method: SyncMethod) { lastSync.value = method }
}

internal fun newVaultAuthGate(context: Context): VaultAuthGate {
    val vault = VaultManager(
        context,
        KeystoreManager(context),
        VaultSession(),
        VaultPasswordManager(KeystoreManager(context), context),
    )
    return VaultAuthGate(FakeSyncPreferences(context), vault, BiometricHelper(context))
}
