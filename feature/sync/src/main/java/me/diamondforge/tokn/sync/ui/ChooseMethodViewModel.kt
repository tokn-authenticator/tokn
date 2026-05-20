package me.diamondforge.tokn.sync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.diamondforge.tokn.data.preferences.SyncMethod
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import javax.inject.Inject

@HiltViewModel
class ChooseMethodViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

    val lastMethod: StateFlow<SyncMethod> = prefs.lastSyncMethod
        .stateIn(viewModelScope, SharingStarted.Eagerly, SyncMethod.LAN)

    fun commit(method: SyncMethod) {
        viewModelScope.launch { prefs.setLastSyncMethod(method) }
    }
}
