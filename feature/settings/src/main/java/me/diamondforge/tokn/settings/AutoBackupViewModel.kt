package me.diamondforge.tokn.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.diamondforge.tokn.backup.auto.AutoBackupManager
import me.diamondforge.tokn.backup.auto.AutoBackupPreferencesRepository
import me.diamondforge.tokn.backup.auto.AutoBackupResult
import me.diamondforge.tokn.backup.auto.AutoBackupStrategy
import javax.inject.Inject

@HiltViewModel
class AutoBackupViewModel @Inject constructor(
    private val prefs: AutoBackupPreferencesRepository,
    private val manager: AutoBackupManager,
) : ViewModel() {

    private val _messages = Channel<AutoBackupMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val uiState: StateFlow<AutoBackupUiState> = combine(
        prefs.enabled,
        prefs.location,
        prefs.encrypt,
        prefs.passwordWrapped,
        prefs.versionsToKeep,
    ) { enabled, location, encrypt, wrapped, versions ->
        AutoBackupUiState(
            enabled = enabled,
            location = location,
            encrypt = encrypt,
            hasPassword = wrapped != null,
            versionsToKeep = versions,
        )
    }.combine(prefs.mode) { state, mode ->
        state.copy(mode = mode)
    }.combine(prefs.lastResultAt) { state, at ->
        state.copy(lastResultAt = at)
    }.combine(prefs.lastError) { state, error ->
        state.copy(lastError = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoBackupUiState())

    fun setEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setEnabled(enabled)
        if (enabled) backupNow()
    }

    fun setMode(mode: AutoBackupStrategy) = viewModelScope.launch {
        if (mode == uiState.value.mode) return@launch
        prefs.setMode(mode)
        prefs.setLocation(null)
    }

    fun setLocation(uri: String) = viewModelScope.launch {
        prefs.setLocation(uri)
        if (uiState.value.enabled) backupNow()
    }

    fun setEncrypt(encrypt: Boolean) = viewModelScope.launch {
        prefs.setEncrypt(encrypt)
        if (uiState.value.enabled) backupNow()
    }

    fun setPassword(password: String) = viewModelScope.launch {
        manager.setPassword(password)
        if (uiState.value.enabled) backupNow()
    }

    fun setVersionsToKeep(count: Int) = viewModelScope.launch {
        prefs.setVersionsToKeep(count)
    }

    fun backupNow() = viewModelScope.launch {
        val message = when (val result = manager.backupNow(force = true)) {
            AutoBackupResult.Success -> AutoBackupMessage.SUCCESS
            is AutoBackupResult.Skipped ->
                if (result.reason == "no_location") null else AutoBackupMessage.UNCHANGED

            is AutoBackupResult.Failure ->
                if (result.reason == "no_password") AutoBackupMessage.NO_PASSWORD
                else AutoBackupMessage.FAILED
        }
        if (message != null) _messages.send(message)
    }
}

data class AutoBackupUiState(
    val enabled: Boolean = false,
    val mode: AutoBackupStrategy = AutoBackupStrategy.ROTATING,
    val location: String? = null,
    val encrypt: Boolean = true,
    val hasPassword: Boolean = false,
    val versionsToKeep: Int = 5,
    val lastResultAt: Long = 0L,
    val lastError: String? = null,
)

enum class AutoBackupMessage { SUCCESS, UNCHANGED, FAILED, NO_PASSWORD }
