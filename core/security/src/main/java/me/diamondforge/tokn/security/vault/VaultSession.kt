package me.diamondforge.tokn.security.vault

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Arrays
import javax.inject.Inject
import javax.inject.Singleton

enum class VaultState { LOCKED, UNLOCKED }

@Singleton
class VaultSession @Inject constructor() {

    @Volatile
    private var masterKey: ByteArray? = null

    private val _state = MutableStateFlow(VaultState.LOCKED)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    val isUnlocked: Boolean get() = masterKey != null

    @Synchronized
    fun unlock(key: ByteArray) {
        masterKey?.let { Arrays.fill(it, 0) }
        masterKey = key.copyOf()
        _state.value = VaultState.UNLOCKED
    }

    @Synchronized
    fun lock() {
        masterKey?.let { Arrays.fill(it, 0) }
        masterKey = null
        _state.value = VaultState.LOCKED
    }

    fun requireKey(): ByteArray =
        (masterKey ?: error("Vault is locked")).copyOf()
}
