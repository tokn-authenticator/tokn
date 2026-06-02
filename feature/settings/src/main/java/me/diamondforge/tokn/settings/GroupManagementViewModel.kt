package me.diamondforge.tokn.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.diamondforge.tokn.domain.usecase.DeleteGroupUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.RenameGroupUseCase
import javax.inject.Inject

@HiltViewModel
class GroupManagementViewModel @Inject constructor(
    private val getAccountsUseCase: GetAccountsUseCase,
    private val renameGroupUseCase: RenameGroupUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
) : ViewModel() {

    val uiState: StateFlow<GroupManagementUiState> = getAccountsUseCase()
        .map { accounts ->
            // Group case-insensitively but keep the first-seen casing as the
            // display name, mirroring how GroupsField commits new chips.
            val byLower = linkedMapOf<String, GroupRow>()
            accounts.forEach { account ->
                account.groups.forEach { raw ->
                    val key = raw.lowercase()
                    val existing = byLower[key]
                    if (existing == null) {
                        byLower[key] = GroupRow(name = raw, accountCount = 1)
                    } else {
                        byLower[key] = existing.copy(accountCount = existing.accountCount + 1)
                    }
                }
            }
            GroupManagementUiState(
                groups = byLower.values.sortedBy { it.name.lowercase() },
                isLoading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GroupManagementUiState(isLoading = true),
        )

    fun rename(from: String, to: String) {
        val trimmed = to.trim()
        if (trimmed.isEmpty() || trimmed == from) return
        viewModelScope.launch { renameGroupUseCase(from, trimmed) }
    }

    fun delete(name: String) {
        viewModelScope.launch { deleteGroupUseCase(name) }
    }
}

data class GroupManagementUiState(
    val groups: List<GroupRow> = emptyList(),
    val isLoading: Boolean = false,
)

data class GroupRow(
    val name: String,
    val accountCount: Int,
)
