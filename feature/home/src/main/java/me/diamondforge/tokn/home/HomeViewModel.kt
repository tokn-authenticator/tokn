package me.diamondforge.tokn.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.diamondforge.tokn.data.preferences.AppPreferencesRepository
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.usecase.DeleteAccountUseCase
import me.diamondforge.tokn.domain.usecase.GenerateOtpUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.IncrementHotpCounterUseCase
import me.diamondforge.tokn.domain.usecase.OtpResult
import me.diamondforge.tokn.domain.usecase.ReorderAccountsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getAccountsUseCase: GetAccountsUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val reorderAccountsUseCase: ReorderAccountsUseCase,
    private val generateOtpUseCase: GenerateOtpUseCase,
    private val incrementHotpCounterUseCase: IncrementHotpCounterUseCase,
    private val appPreferences: AppPreferencesRepository,
) : ViewModel() {

    private val _currentTimeMillis = MutableStateFlow(System.currentTimeMillis())
    private val _accounts = getAccountsUseCase()
    private val _searchQuery = MutableStateFlow("")
    private val _selectedGroup = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        _accounts, _currentTimeMillis, _searchQuery, _selectedGroup, appPreferences.iconFetchEnabled,
    ) { accounts, time, query, selectedGroup, iconFetchEnabled ->
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

        HomeUiState(
            isLoading = false,
            items = filtered.map { account ->
                val otpResult = runCatching { generateOtpUseCase(account, time) }
                    .getOrElse { OtpResult("------", -1L, account.period * 1000L) }
                AccountItem(account = account, otpResult = otpResult)
            },
            searchQuery = query,
            availableGroups = availableGroups,
            selectedGroup = selectedGroup,
            iconFetchEnabled = iconFetchEnabled,
        )
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

    fun reorderAccounts(accounts: List<OtpAccount>) {
        viewModelScope.launch { reorderAccountsUseCase(accounts) }
    }

    fun incrementHotpCounter(id: Long) {
        viewModelScope.launch { incrementHotpCounterUseCase(id) }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectGroup(group: String?) {
        _selectedGroup.value = group
    }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val items: List<AccountItem> = emptyList(),
    val searchQuery: String = "",
    val availableGroups: List<String> = emptyList(),
    val selectedGroup: String? = null,
    val iconFetchEnabled: Boolean = false,
)

data class AccountItem(
    val account: OtpAccount,
    val otpResult: OtpResult,
)
