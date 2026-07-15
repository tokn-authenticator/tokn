package me.diamondforge.tokn.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.domain.model.GroupSort
import me.diamondforge.tokn.domain.usecase.CreateGroupUseCase
import me.diamondforge.tokn.domain.usecase.DeleteGroupUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.ListGroupsUseCase
import me.diamondforge.tokn.domain.usecase.RenameGroupUseCase
import me.diamondforge.tokn.domain.usecase.ReorderGroupsUseCase
import me.diamondforge.tokn.domain.usecase.SetGroupColorUseCase
import javax.inject.Inject

@HiltViewModel
class GroupManagementViewModel @Inject constructor(
    private val getAccountsUseCase: GetAccountsUseCase,
    private val listGroupsUseCase: ListGroupsUseCase,
    private val createGroupUseCase: CreateGroupUseCase,
    private val renameGroupUseCase: RenameGroupUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
    private val setGroupColorUseCase: SetGroupColorUseCase,
    private val reorderGroupsUseCase: ReorderGroupsUseCase,
    private val userPreferences: UserPreferencesRepository,
) : ViewModel() {

    private val _groupSort: StateFlow<GroupSort> = userPreferences.groupSort
        .stateIn(viewModelScope, SharingStarted.Eagerly, GroupSort.CUSTOM)

    val uiState: StateFlow<GroupManagementUiState> = combine(
        listGroupsUseCase(),
        getAccountsUseCase(),
        userPreferences.groupSort,
    ) { groups, accounts, sort ->
        val ordered = when (sort) {
            GroupSort.CUSTOM -> groups
            GroupSort.NAME_ASC -> groups.sortedBy { it.name.lowercase() }
            GroupSort.NAME_DESC -> groups.sortedByDescending { it.name.lowercase() }
        }
        val rows = ordered.map { group ->
            GroupRow(
                name = group.name,
                colorArgb = group.colorArgb,
                accountCount = accounts.count { account ->
                    account.groups.any { it.equals(group.name, ignoreCase = true) }
                },
            )
        }
        GroupManagementUiState(groups = rows, isLoading = false, sort = sort)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GroupManagementUiState(isLoading = true),
    )

    fun create(name: String, colorArgb: Int? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { createGroupUseCase(trimmed, colorArgb) }
    }

    fun rename(from: String, to: String) {
        val trimmed = to.trim()
        if (trimmed.isEmpty() || trimmed == from) return
        viewModelScope.launch { renameGroupUseCase(from, trimmed) }
    }

    fun delete(name: String) {
        viewModelScope.launch { deleteGroupUseCase(name) }
    }

    fun setColor(name: String, colorArgb: Int?) {
        viewModelScope.launch { setGroupColorUseCase(name, colorArgb) }
    }

    fun setSort(sort: GroupSort) {
        viewModelScope.launch { userPreferences.setGroupSort(sort) }
    }

    fun reorder(names: List<String>) {
        if (_groupSort.value != GroupSort.CUSTOM) return
        viewModelScope.launch { reorderGroupsUseCase(names) }
    }
}

data class GroupManagementUiState(
    val groups: List<GroupRow> = emptyList(),
    val isLoading: Boolean = false,
    val sort: GroupSort = GroupSort.CUSTOM,
)

data class GroupRow(
    val name: String,
    val accountCount: Int,
    val colorArgb: Int? = null,
)
