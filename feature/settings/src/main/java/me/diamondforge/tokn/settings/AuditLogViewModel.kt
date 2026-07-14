package me.diamondforge.tokn.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.diamondforge.tokn.audit.AuditCategory
import me.diamondforge.tokn.audit.AuditEventType
import me.diamondforge.tokn.audit.AuditLogDao
import me.diamondforge.tokn.audit.AuditLogger
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.domain.usecase.GetAccountsByIdsUseCase
import javax.inject.Inject

@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val auditLogDao: AuditLogDao,
    private val preferences: UserPreferencesRepository,
    private val getAccountsByIdsUseCase: GetAccountsByIdsUseCase,
    private val auditLogger: AuditLogger,
) : ViewModel() {

    private val selectedCategories = MutableStateFlow<Set<AuditCategory>>(emptySet())
    private val searchQuery = MutableStateFlow("")
    private val dateRange = MutableStateFlow<LongRange?>(null)

    init {
        viewModelScope.launch {
            val days = preferences.auditRetentionDays.first()
            val cutoff = System.currentTimeMillis() - days.toLong() * DAY_MS
            auditLogDao.deleteOlderThan(cutoff)
        }
    }

    private val rows = auditLogDao.getAll().map { entries ->
        val itemIds = entries
            .filter { it.category == AuditCategory.ITEMS.name && it.targetId != null }
            .mapNotNull { it.targetId }
            .toSet()
        val names = if (itemIds.isEmpty()) {
            emptyMap()
        } else {
            getAccountsByIdsUseCase(itemIds).associate { account ->
                account.id to (account.issuer.ifBlank { account.accountName })
            }
        }
        entries.mapNotNull { entry ->
            val type = runCatching { AuditEventType.valueOf(entry.type) }.getOrNull()
                ?: return@mapNotNull null
            AuditLogRow(
                id = entry.id,
                type = type,
                timestamp = entry.timestamp,
                targetId = entry.targetId,
                targetName = entry.targetId?.let { names[it] },
                detail = entry.detail,
            )
        }
    }

    val uiState: StateFlow<AuditLogUiState> = combine(
        rows,
        selectedCategories,
        searchQuery,
        dateRange,
    ) { allRows, categories, query, range ->
        val filtered = allRows.filter { row ->
            (categories.isEmpty() || row.type.category in categories) &&
                    (range == null || row.timestamp in range) &&
                    (query.isBlank() || row.matches(query))
        }
        AuditLogUiState(
            items = filtered,
            isLoading = false,
            selectedCategories = categories,
            searchQuery = query,
            dateRange = range,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AuditLogUiState(isLoading = true)
    )

    val settingsState: StateFlow<AuditLogSettingsState> = combine(
        preferences.auditLoggingEnabled,
        preferences.auditRetentionDays,
        preferences.auditDisabledCategories,
    ) { enabled, days, disabledCategories ->
        AuditLogSettingsState(enabled, days, disabledCategories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuditLogSettingsState())

    fun setAuditLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            auditLogger.log(
                if (enabled) AuditEventType.AUDIT_LOG_ENABLED else AuditEventType.AUDIT_LOG_DISABLED,
            )
            preferences.setAuditLoggingEnabled(enabled)
        }
    }

    fun setAuditRetentionDays(days: Int) {
        viewModelScope.launch {
            preferences.setAuditRetentionDays(days)
            auditLogger.log(AuditEventType.AUDIT_LOG_RETENTION_CHANGED, detail = days.toString())
        }
    }

    fun setCategoryLoggingEnabled(category: AuditCategory, enabled: Boolean) {
        viewModelScope.launch {
            val current = preferences.auditDisabledCategories.first()
            val updated = if (enabled) current - category else current + category
            preferences.setAuditDisabledCategories(updated)
            auditLogger.log(
                if (enabled) AuditEventType.AUDIT_LOG_CATEGORY_ENABLED else AuditEventType.AUDIT_LOG_CATEGORY_DISABLED,
                detail = category.name,
            )
        }
    }

    fun toggleCategory(category: AuditCategory) {
        selectedCategories.value = selectedCategories.value.let { current ->
            if (category in current) current - category else current + category
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setDateRange(range: LongRange?) {
        dateRange.value = range
    }

    fun clearFilters() {
        selectedCategories.value = emptySet()
        searchQuery.value = ""
        dateRange.value = null
    }

    fun clearLog() {
        viewModelScope.launch { auditLogDao.clearAll() }
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

data class AuditLogRow(
    val id: Long,
    val type: AuditEventType,
    val timestamp: Long,
    val targetId: Long?,
    val targetName: String?,
    val detail: String?,
) {
    fun matches(query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return targetName?.contains(needle, ignoreCase = true) == true ||
                detail?.contains(needle, ignoreCase = true) == true ||
                type.name.replace('_', ' ').contains(needle, ignoreCase = true)
    }
}

data class AuditLogUiState(
    val items: List<AuditLogRow> = emptyList(),
    val isLoading: Boolean = false,
    val selectedCategories: Set<AuditCategory> = emptySet(),
    val searchQuery: String = "",
    val dateRange: LongRange? = null,
) {
    val hasActiveFilters: Boolean
        get() = selectedCategories.isNotEmpty() || searchQuery.isNotBlank() || dateRange != null
}

data class AuditLogSettingsState(
    val enabled: Boolean = true,
    val retentionDays: Int = 90,
    val disabledCategories: Set<AuditCategory> = emptySet(),
)
