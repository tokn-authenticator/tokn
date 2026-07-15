@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package me.diamondforge.tokn.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.diamondforge.tokn.audit.AuditCategory
import me.diamondforge.tokn.audit.AuditEventType
import me.diamondforge.tokn.domain.model.TapBehavior
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AuditLogScreen(
    onBack: () -> Unit,
    viewModel: AuditLogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()

    var searchVisible by remember { mutableStateOf(false) }
    var showDateRangeDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val closeSearch = {
        searchVisible = false
        viewModel.setSearchQuery("")
    }
    BackHandler(enabled = searchVisible, onBack = closeSearch)

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AnimatedContent(
                targetState = searchVisible,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "audit_log_top_bar",
            ) { isSearching ->
                if (isSearching) {
                    val focusRequester = remember { FocusRequester() }
                    val keyboardController = LocalSoftwareKeyboardController.current
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                    TopAppBar(
                        title = {
                            TextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::setSearchQuery,
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                placeholder = { Text(stringResource(R.string.audit_log_search_hint)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = closeSearch) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.audit_log_exit_search)
                                )
                            }
                        },
                        actions = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.audit_log_clear_search)
                                    )
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                } else {
                    LargeFlexibleTopAppBar(
                        title = { Text(stringResource(R.string.audit_log_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            }
                        },
                        actions = {
                            IconButton(onClick = { showSettingsSheet = true }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.audit_log_settings_title)
                                )
                            }
                            IconButton(onClick = { searchVisible = true }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.audit_log_search_hint)
                                )
                            }
                            IconButton(onClick = { showDateRangeDialog = true }) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = stringResource(R.string.audit_log_date_range_title)
                                )
                            }
                            if (uiState.hasActiveFilters) {
                                IconButton(onClick = { viewModel.clearFilters() }) {
                                    Icon(
                                        Icons.Default.FilterAltOff,
                                        contentDescription = stringResource(R.string.audit_log_filter_clear)
                                    )
                                }
                            }
                            if (uiState.items.isNotEmpty() || uiState.hasActiveFilters) {
                                IconButton(onClick = { showClearConfirm = true }) {
                                    Icon(
                                        Icons.Default.DeleteSweep,
                                        contentDescription = stringResource(R.string.audit_log_clear_action)
                                    )
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (!settingsState.enabled) {
                AuditLogDisabledBanner(onTurnOn = { viewModel.setAuditLoggingEnabled(true) })
            }

            CategoryFilterRow(
                selected = uiState.selectedCategories,
                onToggle = viewModel::toggleCategory,
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    uiState.isLoading -> LoadingIndicator()
                    uiState.items.isEmpty() -> AuditLogEmpty(hasActiveFilters = uiState.hasActiveFilters)
                    else -> {
                        val grouped = groupByDate(uiState.items)
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            grouped.forEach { (label, rows) ->
                                item(key = "header_$label") {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 8.dp
                                        ),
                                    )
                                }
                                items(rows, key = { it.id }) { row ->
                                    AuditLogRowItem(row)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        AuditLogSettingsSheet(
            state = settingsState,
            onDismiss = { showSettingsSheet = false },
            onEnabledChange = viewModel::setAuditLoggingEnabled,
            onRetentionChange = viewModel::setAuditRetentionDays,
            onCategoryToggle = viewModel::setCategoryLoggingEnabled,
        )
    }

    if (showDateRangeDialog) {
        AuditDateRangeDialog(
            initialRange = uiState.dateRange,
            onConfirm = { range ->
                viewModel.setDateRange(range)
                showDateRangeDialog = false
            },
            onDismiss = { showDateRangeDialog = false },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.audit_log_clear_title)) },
            text = { Text(stringResource(R.string.audit_log_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLog()
                    showClearConfirm = false
                }) {
                    Text(
                        text = stringResource(R.string.audit_log_clear_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AuditLogDisabledBanner(onTurnOn: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.audit_log_disabled_banner_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.audit_log_disabled_banner_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = onTurnOn) {
                Text(
                    text = stringResource(R.string.audit_log_disabled_banner_action),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun AuditLogSettingsSheet(
    state: AuditLogSettingsState,
    onDismiss: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onRetentionChange: (Int) -> Unit,
    onCategoryToggle: (AuditCategory, Boolean) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SettingsSectionHeader(stringResource(R.string.audit_log_settings_title))
            SettingsGroup(
                items = buildList {
                    add {
                        SettingsRow(
                            title = stringResource(R.string.audit_log_enable_title),
                            subtitle = stringResource(R.string.audit_log_enable_desc),
                            trailing = {
                                SettingsSwitch(
                                    checked = state.enabled,
                                    onCheckedChange = onEnabledChange
                                )
                            },
                        )
                    }
                    if (state.enabled) {
                        add {
                            SettingsToggleRow(
                                title = stringResource(R.string.audit_log_retention_title),
                                options = listOf(
                                    30 to stringResource(R.string.audit_log_retention_30),
                                    90 to stringResource(R.string.audit_log_retention_90),
                                    180 to stringResource(R.string.audit_log_retention_180),
                                    365 to stringResource(R.string.audit_log_retention_365),
                                ),
                                selected = state.retentionDays,
                                onSelect = onRetentionChange,
                            )
                        }
                    }
                },
            )

            if (state.enabled) {
                SettingsSectionHeader(stringResource(R.string.audit_log_categories_title))
                SettingsGroup(
                    items = AuditCategory.entries.map { category ->
                        {
                            SettingsRow(
                                title = stringResource(categoryLabel(category)),
                                trailing = {
                                    SettingsSwitch(
                                        checked = category !in state.disabledCategories,
                                        onCheckedChange = { enabled ->
                                            onCategoryToggle(
                                                category,
                                                enabled
                                            )
                                        },
                                    )
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selected: Set<AuditCategory>,
    onToggle: (AuditCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AuditCategory.entries.forEach { category ->
            FilterChip(
                selected = category in selected,
                onClick = { onToggle(category) },
                label = { Text(stringResource(categoryLabel(category))) },
            )
        }
    }
}

@Composable
private fun AuditLogRowItem(row: AuditLogRow) {
    ListItem(
        headlineContent = { Text(stringResource(eventTypeLabel(row.type))) },
        supportingContent = {
            val detail = detailText(row)
            val time = relativeTimeLabel(row.timestamp)
            Text(listOfNotNull(row.targetName, detail, time).joinToString(" · "))
        },
    )
}

@Composable
private fun AuditLogEmpty(hasActiveFilters: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(
                if (hasActiveFilters) R.string.audit_log_no_matches_title else R.string.audit_log_empty_title,
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.audit_log_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AuditDateRangeDialog(
    initialRange: LongRange?,
    onConfirm: (LongRange?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialRange?.first,
        initialSelectedEndDateMillis = initialRange?.last,
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Row {
                        if (initialRange != null) {
                            TextButton(onClick = { onConfirm(null) }) {
                                Text(stringResource(R.string.audit_log_filter_clear))
                            }
                        }
                        TextButton(
                            onClick = {
                                val start = state.selectedStartDateMillis
                                val end = state.selectedEndDateMillis
                                val zone = ZoneId.systemDefault()
                                val range = if (start != null && end != null) {
                                    val startLocal =
                                        Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC)
                                            .toLocalDate().atStartOfDay(zone).toInstant()
                                            .toEpochMilli()
                                    val endLocal = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC)
                                        .toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
                                        .toEpochMilli() - 1
                                    startLocal..endLocal
                                } else {
                                    null
                                }
                                onConfirm(range)
                            },
                        ) {
                            Text(stringResource(R.string.audit_log_date_range_apply))
                        }
                    }
                }
                DateRangePicker(state = state, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun groupByDate(rows: List<AuditLogRow>): List<Pair<String, List<AuditLogRow>>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val todayLabel = stringResource(R.string.audit_log_today)
    val yesterdayLabel = stringResource(R.string.audit_log_yesterday)
    return rows
        .groupBy { row ->
            val date = Instant.ofEpochMilli(row.timestamp).atZone(zone).toLocalDate()
            val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)
            when {
                days == 0L -> BucketKey(0, todayLabel)
                days == 1L -> BucketKey(1, yesterdayLabel)
                days in 2..6 -> BucketKey(
                    days,
                    date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                )

                else -> BucketKey(
                    days,
                    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                )
            }
        }
        .toSortedMap(compareBy { it.sortKey })
        .map { (bucket, items) -> bucket.label to items }
}

private data class BucketKey(val sortKey: Long, val label: String)

private fun relativeTimeLabel(timestamp: Long): String {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(timestamp).atZone(zone)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

private fun eventTypeLabel(type: AuditEventType): Int = when (type) {
    AuditEventType.VAULT_UNLOCKED_PASSWORD -> R.string.audit_event_vault_unlocked_password
    AuditEventType.VAULT_UNLOCKED_BIOMETRIC -> R.string.audit_event_vault_unlocked_biometric
    AuditEventType.VAULT_UNLOCK_FAILED_PASSWORD -> R.string.audit_event_vault_unlock_failed_password
    AuditEventType.VAULT_UNLOCK_FAILED_BIOMETRIC -> R.string.audit_event_vault_unlock_failed_biometric
    AuditEventType.VAULT_LOCKED_AUTO -> R.string.audit_event_vault_locked_auto
    AuditEventType.PASSWORD_SET -> R.string.audit_event_password_set
    AuditEventType.PASSWORD_CHANGED -> R.string.audit_event_password_changed
    AuditEventType.PASSWORD_REMOVED -> R.string.audit_event_password_removed
    AuditEventType.BIOMETRIC_ENABLED -> R.string.audit_event_biometric_enabled
    AuditEventType.BIOMETRIC_DISABLED -> R.string.audit_event_biometric_disabled
    AuditEventType.ITEM_ADDED -> R.string.audit_event_item_added
    AuditEventType.ITEM_EDITED -> R.string.audit_event_item_edited
    AuditEventType.ITEM_DELETED -> R.string.audit_event_item_deleted
    AuditEventType.ITEM_RESTORED -> R.string.audit_event_item_restored
    AuditEventType.ITEM_PURGED -> R.string.audit_event_item_purged
    AuditEventType.ITEM_PURGED_AUTO -> R.string.audit_event_item_purged_auto
    AuditEventType.GROUP_RENAMED -> R.string.audit_event_group_renamed
    AuditEventType.GROUP_REMOVED -> R.string.audit_event_group_removed
    AuditEventType.THEME_CHANGED -> R.string.audit_event_theme_changed
    AuditEventType.DYNAMIC_COLOR_ENABLED -> R.string.audit_event_dynamic_color_enabled
    AuditEventType.DYNAMIC_COLOR_DISABLED -> R.string.audit_event_dynamic_color_disabled
    AuditEventType.ICON_FETCH_ENABLED -> R.string.audit_event_icon_fetch_enabled
    AuditEventType.ICON_FETCH_DISABLED -> R.string.audit_event_icon_fetch_disabled
    AuditEventType.SHOW_NEXT_CODE_ENABLED -> R.string.audit_event_show_next_code_enabled
    AuditEventType.SHOW_NEXT_CODE_DISABLED -> R.string.audit_event_show_next_code_disabled
    AuditEventType.TAP_TO_REVEAL_ENABLED -> R.string.audit_event_tap_to_reveal_enabled
    AuditEventType.TAP_TO_REVEAL_DISABLED -> R.string.audit_event_tap_to_reveal_disabled
    AuditEventType.STAY_REVEALED_ENABLED -> R.string.audit_event_stay_revealed_enabled
    AuditEventType.STAY_REVEALED_DISABLED -> R.string.audit_event_stay_revealed_disabled
    AuditEventType.TAP_BEHAVIOR_CHANGED -> R.string.audit_event_tap_behavior_changed
    AuditEventType.SCREENSHOT_PROTECTION_ENABLED -> R.string.audit_event_screenshot_protection_enabled
    AuditEventType.SCREENSHOT_PROTECTION_DISABLED -> R.string.audit_event_screenshot_protection_disabled
    AuditEventType.AUTO_LOCK_TIMEOUT_CHANGED -> R.string.audit_event_auto_lock_timeout_changed
    AuditEventType.PASSWORD_REMINDER_ENABLED -> R.string.audit_event_password_reminder_enabled
    AuditEventType.PASSWORD_REMINDER_DISABLED -> R.string.audit_event_password_reminder_disabled
    AuditEventType.RECYCLE_BIN_SETTING_ENABLED -> R.string.audit_event_recycle_bin_setting_enabled
    AuditEventType.RECYCLE_BIN_SETTING_DISABLED -> R.string.audit_event_recycle_bin_setting_disabled
    AuditEventType.AUDIT_LOG_ENABLED -> R.string.audit_event_audit_log_enabled
    AuditEventType.AUDIT_LOG_DISABLED -> R.string.audit_event_audit_log_disabled
    AuditEventType.AUDIT_LOG_RETENTION_CHANGED -> R.string.audit_event_audit_log_retention_changed
    AuditEventType.AUDIT_LOG_CATEGORY_ENABLED -> R.string.audit_event_audit_log_category_enabled
    AuditEventType.AUDIT_LOG_CATEGORY_DISABLED -> R.string.audit_event_audit_log_category_disabled
    AuditEventType.BACKUP_EXPORTED_ENCRYPTED -> R.string.audit_event_backup_exported_encrypted
    AuditEventType.BACKUP_EXPORTED_UNENCRYPTED -> R.string.audit_event_backup_exported_unencrypted
    AuditEventType.BACKUP_EXPORTED_OTPAUTH_LIST -> R.string.audit_event_backup_exported_otpauth_list
    AuditEventType.BACKUP_EXPORTED_PLAIN_TEXT -> R.string.audit_event_backup_exported_plain_text
    AuditEventType.AUTO_BACKUP_CREATED -> R.string.audit_event_auto_backup_created
    AuditEventType.RECYCLE_BIN_EMPTIED -> R.string.audit_event_recycle_bin_emptied
    AuditEventType.VAULT_IMPORTED -> R.string.audit_event_vault_imported
}

private fun categoryLabel(category: AuditCategory): Int = when (category) {
    AuditCategory.VAULT_AUTH -> R.string.audit_category_vault_auth
    AuditCategory.ITEMS -> R.string.audit_category_items
    AuditCategory.SETTINGS -> R.string.audit_category_settings
    AuditCategory.BACKUP -> R.string.audit_category_backup
    AuditCategory.RECYCLE_BIN -> R.string.audit_category_recycle_bin
    AuditCategory.IMPORT_EXPORT -> R.string.audit_category_import_export
}

@Composable
private fun detailText(row: AuditLogRow): String? {
    val detail = row.detail ?: return null
    return when (row.type) {
        AuditEventType.AUTO_LOCK_TIMEOUT_CHANGED -> when (detail.toIntOrNull()) {
            0 -> stringResource(R.string.lock_immediately)
            30 -> stringResource(R.string.lock_30s)
            60 -> stringResource(R.string.lock_1m)
            300 -> stringResource(R.string.lock_5m)
            else -> detail
        }

        AuditEventType.THEME_CHANGED -> when (detail) {
            "LIGHT" -> stringResource(R.string.theme_light)
            "DARK" -> stringResource(R.string.theme_dark)
            else -> stringResource(R.string.theme_system)
        }

        AuditEventType.TAP_BEHAVIOR_CHANGED -> when (runCatching { TapBehavior.valueOf(detail) }.getOrNull()) {
            TapBehavior.NONE -> stringResource(R.string.tap_none)
            TapBehavior.SINGLE -> stringResource(R.string.tap_single)
            TapBehavior.DOUBLE -> stringResource(R.string.tap_double)
            null -> detail
        }

        AuditEventType.ITEM_PURGED_AUTO, AuditEventType.RECYCLE_BIN_EMPTIED ->
            detail.toIntOrNull()
                ?.let { pluralStringResource(R.plurals.audit_log_detail_count, it, it) } ?: detail

        AuditEventType.AUDIT_LOG_RETENTION_CHANGED ->
            detail.toIntOrNull()
                ?.let { pluralStringResource(R.plurals.audit_log_retention_days, it, it) } ?: detail

        AuditEventType.AUDIT_LOG_CATEGORY_ENABLED, AuditEventType.AUDIT_LOG_CATEGORY_DISABLED ->
            runCatching { AuditCategory.valueOf(detail) }.getOrNull()
                ?.let { stringResource(categoryLabel(it)) } ?: detail

        else -> detail
    }
}
