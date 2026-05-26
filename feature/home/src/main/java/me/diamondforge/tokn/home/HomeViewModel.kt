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
import me.diamondforge.tokn.data.icon.IconPackManager
import me.diamondforge.tokn.data.preferences.AppPreferencesRepository
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.domain.usecase.DeleteAccountUseCase
import me.diamondforge.tokn.domain.usecase.DeleteAccountsUseCase
import me.diamondforge.tokn.domain.usecase.GenerateOtpUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.IncrementHotpCounterUseCase
import me.diamondforge.tokn.domain.usecase.OtpResult
import me.diamondforge.tokn.domain.usecase.ReorderAccountsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val deleteAccountsUseCase: DeleteAccountsUseCase,
    private val reorderAccountsUseCase: ReorderAccountsUseCase,
    private val generateOtpUseCase: GenerateOtpUseCase,
    private val incrementHotpCounterUseCase: IncrementHotpCounterUseCase,
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
    private val _selectedGroup = MutableStateFlow<String?>(null)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _reveals = MutableStateFlow<Map<Long, RevealRecord>>(emptyMap())

    val uiState: StateFlow<HomeUiState> = combine(
        combine(_accounts, _currentTimeMillis, _searchQuery, _selectedGroup, _selectedIds, ::Quint),
        appPreferences.iconFetchEnabled,
    ) { quint, iconFetchEnabled ->
        val (accounts, time, query, selectedGroup, selectedIds) = quint
        val availableGroups = accounts.mapNotNull { it.group }.distinct().sorted()

        val filtered = accounts
            .filter { account ->
                query.isBlank() ||
                    account.issuer.contains(query, ignoreCase = true) ||
                    account.accountName.contains(query, ignoreCase = true)
            }
            .filter { account ->
                selectedGroup == null || account.group == selectedGroup
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
            selectedGroup = selectedGroup,
            iconFetchEnabled = iconFetchEnabled,
            selectedIds = sanitizedSelection,
        )
    }.combine(userPreferences.tapToRevealEnabled) { state, enabled ->
        state.copy(tapToRevealEnabled = enabled)
    }.combine(_reveals) { state, reveals ->
        // A reveal is valid only while the displayed code matches the one
        // captured at reveal time AND the per-type timeout hasn't elapsed.
        // Keying on the code (not wall-clock expiry) means a TOTP rollover
        // re-masks atomically with the new code arriving — no flash of the
        // freshly generated code.
        val now = _currentTimeMillis.value
        val revealed = state.items
            .mapNotNull { item ->
                val record = reveals[item.account.id] ?: return@mapNotNull null
                if (record.code == item.otpResult.code && record.expiresAt > now) {
                    item.account.id
                } else null
            }
            .toSet()
        state.copy(revealedIds = revealed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(isLoading = true))

    init {
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

    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        _selectedIds.value = emptySet()
        viewModelScope.launch { deleteAccountsUseCase(ids) }
    }

    fun reorderAccounts(accounts: List<OtpAccount>) {
        viewModelScope.launch { reorderAccountsUseCase(accounts) }
    }

    fun incrementHotpCounter(id: Long) {
        viewModelScope.launch { incrementHotpCounterUseCase(id) }
    }

    /**
     * Copies the displayed code to the clipboard and schedules a clear after
     * [CLIPBOARD_CLEAR_DELAY_MS]. For HOTP accounts the counter is advanced
     * so the next render shows the *next* unused code — pasting the just-
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
    }

    fun copyToClipboard(item: AccountItem) {
        val code = item.otpResult.code
        val clip = ClipData.newPlainText("OTP", code).apply {
            description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)

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

    fun selectGroup(group: String?) {
        _selectedGroup.value = group
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
    val selectedGroup: String? = null,
    val iconFetchEnabled: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val tapToRevealEnabled: Boolean = false,
    val revealedIds: Set<Long> = emptySet(),
) {
    val selectionMode: Boolean get() = selectedIds.isNotEmpty()
}

private data class Quint<A, B, C, D, E>(
    val a: A,
    val b: B,
    val c: C,
    val d: D,
    val e: E,
)

data class AccountItem(
    val account: OtpAccount,
    val otpResult: OtpResult,
    val packIconPath: String? = null,
)

private data class RevealRecord(
    val code: String,
    val expiresAt: Long,
)
