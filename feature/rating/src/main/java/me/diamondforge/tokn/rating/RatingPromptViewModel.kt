package me.diamondforge.tokn.rating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.domain.rating.RatingPromptGate
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.security.vault.VaultSession
import me.diamondforge.tokn.security.vault.VaultState
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RatingPromptViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val installInfo: InstallInfoProvider,
    private val getAccounts: Lazy<GetAccountsUseCase>,
    vaultSession: VaultSession,
) : ViewModel() {

    private val fromPlayStore = installInfo.isFromPlayStore()
    private val firstInstallTime = installInfo.firstInstallTime()

    private val hasTokens: Flow<Boolean> = vaultSession.state.flatMapLatest { state ->
        if (state == VaultState.UNLOCKED) getAccounts.get()().map { it.isNotEmpty() } else flowOf(
            false
        )
    }

    val shouldPrompt: StateFlow<Boolean> = combine(
        preferences.ratingPromptHandled,
        preferences.ratingPromptSnoozedUntil,
        preferences.ratingPromptLaunchCount,
        hasTokens,
    ) { handled, snoozedUntil, launchCount, tokens ->
        RatingPromptGate.isEligible(
            now = System.currentTimeMillis(),
            firstInstallTime = firstInstallTime,
            launchCount = launchCount,
            hasTokens = tokens,
            fromPlayStore = fromPlayStore,
            handled = handled,
            snoozedUntil = snoozedUntil,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun recordLaunch() {
        viewModelScope.launch {
            val count = preferences.ratingPromptLaunchCount.first()
            if (count < RatingPromptGate.MIN_LAUNCHES) {
                preferences.setRatingPromptLaunchCount(count + 1)
            }
        }
    }

    fun markHandled() {
        viewModelScope.launch { preferences.setRatingPromptHandled(true) }
    }

    fun snooze() {
        viewModelScope.launch {
            preferences.setRatingPromptSnoozedUntil(RatingPromptGate.snoozedUntil(System.currentTimeMillis()))
        }
    }
}
