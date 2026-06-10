package me.diamondforge.tokn.passwordreminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.security.vault.VaultManager
import javax.inject.Inject

@HiltViewModel
class PasswordReminderViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val vaultManager: VaultManager,
) : ViewModel() {

    // currentTimeMillis is read per combine, so dueness re-evaluates on each app open.
    val shouldPrompt: StateFlow<Boolean> = combine(
        preferences.passwordReminderEnabled,
        preferences.passwordReminderLastShownAt,
        preferences.passwordReminderStage,
    ) { enabled, lastShownAt, stage ->
        enabled && PasswordReminderSchedule.isDue(System.currentTimeMillis(), lastShownAt, stage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Interval after the next correct entry, for the confirmation toast.
    val nextReminderDays: StateFlow<Int> = preferences.passwordReminderStage
        .map { PasswordReminderSchedule.intervalDays(PasswordReminderSchedule.nextStageOnSuccess(it)) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PasswordReminderSchedule.intervalDays(PasswordReminderSchedule.nextStageOnSuccess(0)),
        )

    suspend fun verify(password: String): Boolean =
        withContext(Dispatchers.IO) { vaultManager.verifyPassword(password) }

    // Password proven outside the reminder (e.g. unlock): defer without escalating.
    fun markSeen() {
        viewModelScope.launch {
            preferences.setPasswordReminderLastShownAt(System.currentTimeMillis())
        }
    }

    fun recordSuccess() {
        viewModelScope.launch {
            val stage = preferences.passwordReminderStage.first()
            preferences.setPasswordReminderStage(PasswordReminderSchedule.nextStageOnSuccess(stage))
            preferences.setPasswordReminderLastShownAt(System.currentTimeMillis())
        }
    }

    // Wrong then dismissed: drop the stage to tighten the cadence, but return soon (snooze timing).
    fun recordFailure() {
        viewModelScope.launch {
            val stage = preferences.passwordReminderStage.first()
            val dropped = PasswordReminderSchedule.nextStageOnFailure(stage)
            preferences.setPasswordReminderStage(dropped)
            preferences.setPasswordReminderLastShownAt(
                PasswordReminderSchedule.snoozedLastShownAt(System.currentTimeMillis(), dropped),
            )
        }
    }

    fun snooze() {
        viewModelScope.launch {
            val stage = preferences.passwordReminderStage.first()
            preferences.setPasswordReminderLastShownAt(
                PasswordReminderSchedule.snoozedLastShownAt(System.currentTimeMillis(), stage),
            )
        }
    }
}
