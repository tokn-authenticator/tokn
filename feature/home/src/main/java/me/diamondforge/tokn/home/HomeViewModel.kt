package me.diamondforge.tokn.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.diamondforge.tokn.data.icon.IconPackManager
import me.diamondforge.tokn.data.preferences.AppPreferencesRepository
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.domain.model.AccountSort
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.domain.model.TapBehavior
import me.diamondforge.tokn.domain.usecase.DeleteAccountUseCase
import me.diamondforge.tokn.domain.usecase.DeleteAccountsUseCase
import me.diamondforge.tokn.domain.usecase.GenerateOtpUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.IncrementHotpCounterUseCase
import me.diamondforge.tokn.domain.usecase.OtpResult
import me.diamondforge.tokn.domain.usecase.PurgeAccountsUseCase
import me.diamondforge.tokn.domain.usecase.PurgeExpiredTrashUseCase
import me.diamondforge.tokn.domain.usecase.RecordUsageUseCase
import me.diamondforge.tokn.domain.usecase.ReorderAccountsUseCase
import me.diamondforge.tokn.domain.usecase.RestoreAccountsUseCase
import me.diamondforge.tokn.home.HomeViewModel.Companion.CLIPBOARD_CLEAR_DELAY_MS
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val deleteAccountsUseCase: DeleteAccountsUseCase,
    private val restoreAccountsUseCase: RestoreAccountsUseCase,
    private val purgeAccountsUseCase: PurgeAccountsUseCase,
    private val purgeExpiredTrashUseCase: PurgeExpiredTrashUseCase,
    private val reorderAccountsUseCase: ReorderAccountsUseCase,
    private val generateOtpUseCase: GenerateOtpUseCase,
    private val incrementHotpCounterUseCase: IncrementHotpCounterUseCase,
    private val recordUsageUseCase: RecordUsageUseCase,
    private val appPreferences: AppPreferencesRepository,
    private val userPreferences: UserPreferencesRepository,
    private val iconPackManager: IconPackManager,
) : ViewModel() {

    private val clipboard: ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private var clearClipboardJob: Job? = null

    private val _currentTimeMillis = MutableStateFlow(System.currentTimeMillis())
    private val _accounts = getAccountsUseCase()
    private val _searchQuery = MutableStateFlow("")
    private val _selectedGroups = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _reveals = MutableStateFlow<Map<Long, RevealRecord>>(emptyMap())

    // Cached mirror of the persisted sort so reorderAccounts() can check
    // synchronously without re-reading the DataStore on every drag tick.
    private val _accountSort: StateFlow<AccountSort> = userPreferences.accountSort
        .stateIn(viewModelScope, SharingStarted.Eagerly, AccountSort.CUSTOM)

    // Sort once whenever accounts or sort change. The per-tick uiState combine
    // below then leaves order alone, only the OTP codes refresh each second.
    private val sortedView: Flow<SortedView> =
        combine(_accounts, _accountSort) { accounts, sort ->
            SortedView(
                accounts.sortedFor(sort),
                sort
            )
        }

    private val partialState: Flow<HomeUiState> = combine(
        sortedView,
        _currentTimeMillis,
        _searchQuery,
        _selectedGroups,
        _selectedIds,
    ) { sorted, time, query, selectedGroups, selectedIds ->
        val accounts = sorted.accounts
        val availableGroups = accounts.flatMap { it.groups }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

        // Drop filter chips for groups that no longer exist (last account
        // in that group was deleted) so a stale selection can't permanently
        // hide every row.
        val existingGroupKeys = availableGroups.mapTo(mutableSetOf()) { it.lowercase() }
        val sanitizedGroupSelection = selectedGroups.filterTo(mutableSetOf()) {
            it.lowercase() in existingGroupKeys
        }
        if (sanitizedGroupSelection.size != selectedGroups.size) {
            _selectedGroups.value = sanitizedGroupSelection
        }

        val filtered = accounts
            .filter { account ->
                query.isBlank() ||
                        account.issuer.contains(query, ignoreCase = true) ||
                        account.accountName.contains(query, ignoreCase = true)
            }
            .filter { account ->
                sanitizedGroupSelection.isEmpty() ||
                        account.groups.any { g ->
                            sanitizedGroupSelection.any { it.equals(g, ignoreCase = true) }
                        }
            }

        // Prune selection of ids that no longer exist (e.g. after delete).
        val existingIds = accounts.map { it.id }.toSet()
        val sanitizedSelection = selectedIds.intersect(existingIds)
        if (sanitizedSelection.size != selectedIds.size) {
            _selectedIds.value = sanitizedSelection
        }

        HomeUiState(
            isLoading = false,
            items = filtered.map { account ->
                val otpResult = runCatching { generateOtpUseCase(account, time) }
                    .getOrElse { OtpResult("------", -1L, account.period * 1000L) }
                val packFilePath = account.iconPackId?.let { pid ->
                    account.iconPackFile?.let { fname ->
                        iconPackManager.iconFile(pid, fname)?.absolutePath
                    }
                }
                AccountItem(account = account, otpResult = otpResult, packIconPath = packFilePath)
            },
            searchQuery = query,
            availableGroups = availableGroups,
            selectedGroups = sanitizedGroupSelection,
            selectedIds = sanitizedSelection,
            sort = sorted.sort,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        partialState,
        appPreferences.iconFetchEnabled,
        userPreferences.tapToRevealEnabled,
        userPreferences.stayRevealedEnabled,
        _reveals,
    ) { state, iconFetchEnabled, tapToReveal, stayRevealed, reveals ->
        // A reveal is valid only while the displayed code matches the one
        // captured at reveal time AND the per-type timeout hasn't elapsed.
        // Keying on the code (not wall-clock expiry) means a TOTP rollover
        // re-masks atomically with the new code arriving: no flash of the
        // freshly generated code.
        val now = _currentTimeMillis.value
        val revealed = state.items
            .mapNotNull { item ->
                val record = reveals[item.account.id] ?: return@mapNotNull null
                val codeMatches = stayRevealed || record.code == item.otpResult.code
                if (codeMatches && record.expiresAt > now) {
                    item.account.id
                } else null
            }
            .toSet()
        state.copy(
            iconFetchEnabled = iconFetchEnabled,
            tapToRevealEnabled = tapToReveal,
            revealedIds = revealed,
        )
    }.combine(userPreferences.tapBehavior) { state, tapBehavior ->
        state.copy(tapBehavior = tapBehavior)
    }.combine(userPreferences.showNextCodeEnabled) { state, showNextCode ->
        if (!showNextCode) return@combine state
        state.copy(
            items = state.items.map { item ->
                if (item.account.type != OtpType.TOTP) return@map item
                val nextTime = _currentTimeMillis.value + item.account.period * 1000L
                val next =
                    runCatching { generateOtpUseCase(item.account, nextTime).code }.getOrNull()
                item.copy(nextCode = next)
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(isLoading = true))

    init {
        viewModelScope.launch { purgeExpiredTrashUseCase() }
        viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                _currentTimeMillis.value = now
                delay(1_000 - (now % 1_000))
            }
        }
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch { deleteAccountUseCase(id) }
    }

    fun startSelection(id: Long) {
        _selectedIds.value = setOf(id)
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (id in current) current - id else current + id
    }

    fun selectAll() {
        _selectedIds.value = uiState.value.items.map { it.account.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected(immediately: Boolean = false) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        _selectedIds.value = emptySet()
        viewModelScope.launch {
            if (immediately) purgeAccountsUseCase(ids) else deleteAccountsUseCase(ids)
        }
    }

    fun restoreAccounts(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch { restoreAccountsUseCase(ids) }
    }

    /**
     * Persists a new manual order. No-op outside [AccountSort.CUSTOM] so a
     * stray onMove from the reorderable LazyList during a sort transition
     * can't corrupt the user's saved custom order.
     */
    fun reorderAccounts(accounts: List<OtpAccount>) {
        if (_accountSort.value != AccountSort.CUSTOM) return
        viewModelScope.launch { reorderAccountsUseCase(accounts) }
    }

    fun incrementHotpCounter(id: Long) {
        viewModelScope.launch { incrementHotpCounterUseCase(id) }
    }

    fun setSort(sort: AccountSort) {
        viewModelScope.launch { userPreferences.setAccountSort(sort) }
    }

    /**
     * Copies the displayed code to the clipboard and schedules a clear after
     * [CLIPBOARD_CLEAR_DELAY_MS]. For HOTP accounts the counter is advanced
     * so the next render shows the *next* unused code: pasting the just-
     * copied value will succeed on the server, and the user won't see a
     * stale code on their next glance.
     *
     * The clear job is held in [clearClipboardJob] and cancelled before a
     * new copy is scheduled. Without that, two rapid copies leave two
     * coroutines racing, and an unrelated clipboard value the user copied
     * later from another app could be wiped mid-flight.
     */
    fun reveal(item: AccountItem) {
        val expiresAt = when (item.account.type) {
            OtpType.TOTP -> Long.MAX_VALUE
            OtpType.HOTP -> System.currentTimeMillis() + REVEAL_HOTP_MS
        }
        _reveals.update {
            it + (item.account.id to RevealRecord(item.otpResult.code, expiresAt))
        }
        recordUsage(item.account.id)
    }

    fun copyToClipboard(item: AccountItem) {
        val code = item.otpResult.code
        val clip = ClipData.newPlainText("OTP", code).apply {
            description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)
        recordUsage(item.account.id)

        if (item.account.type == OtpType.HOTP) {
            // Drop the reveal so the freshly generated code never
            // briefly shows up unmasked.
            _reveals.update { it - item.account.id }
            viewModelScope.launch { incrementHotpCounterUseCase(item.account.id) }
        }

        clearClipboardJob?.cancel()
        clearClipboardJob = viewModelScope.launch {
            delay(CLIPBOARD_CLEAR_DELAY_MS)
            // ClipboardManager.clearPrimaryClip() requires API 28+.
            // minSdk is 26, so guard and fall back to an empty clip.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleGroupFilter(group: String) {
        val current = _selectedGroups.value
        _selectedGroups.value = if (current.any { it.equals(group, ignoreCase = true) }) {
            current.filterNotTo(mutableSetOf()) { it.equals(group, ignoreCase = true) }
        } else {
            current + group
        }
    }

    fun clearGroupFilter() {
        _selectedGroups.value = emptySet()
    }

    /**
     * Records a usage event. Every interaction (reveal, copy) counts on its
     * own so spam-tapping bumps the count predictably; without that the
     * "most used" sort can't reflect intent in real time.
     */
    private fun recordUsage(id: Long) {
        val now = System.currentTimeMillis()
        viewModelScope.launch { recordUsageUseCase(id, now) }
    }

    companion object {
        private const val CLIPBOARD_CLEAR_DELAY_MS = 30_000L
        private const val REVEAL_HOTP_MS = 15_000L
    }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val items: List<AccountItem> = emptyList(),
    val searchQuery: String = "",
    val availableGroups: List<String> = emptyList(),
    val selectedGroups: Set<String> = emptySet(),
    val iconFetchEnabled: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val tapToRevealEnabled: Boolean = false,
    val tapBehavior: TapBehavior = TapBehavior.SINGLE,
    val revealedIds: Set<Long> = emptySet(),
    val sort: AccountSort = AccountSort.CUSTOM,
) {
    val selectionMode: Boolean get() = selectedIds.isNotEmpty()
}

data class AccountItem(
    val account: OtpAccount,
    val otpResult: OtpResult,
    val packIconPath: String? = null,
    val nextCode: String? = null,
)

private data class RevealRecord(
    val code: String,
    val expiresAt: Long,
)

private data class SortedView(
    val accounts: List<OtpAccount>,
    val sort: AccountSort,
)
