package me.diamondforge.tokn.home

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import me.diamondforge.tokn.data.preferences.AppPreferencesRepository
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.domain.model.AccountSort

/**
 * Shared in-test stand-ins for the DataStore-backed preference repos.
 * Kept as top-level `internal` classes so individual test files can mutate
 * the public StateFlow fields without each file re-declaring its own copy.
 */
internal class FakeAppPreferences(context: Context) : AppPreferencesRepository(context) {
    val fetch = MutableStateFlow(false)
    override val iconFetchEnabled = fetch
}

internal class FakeUserPreferences(context: Context) : UserPreferencesRepository(context) {
    val tap = MutableStateFlow(false)
    override val tapToRevealEnabled = tap
    val sort = MutableStateFlow(AccountSort.CUSTOM)
    override val accountSort = sort
}
