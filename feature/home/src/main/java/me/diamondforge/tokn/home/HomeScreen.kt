package me.diamondforge.tokn.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.diamondforge.tokn.domain.model.AccountSort
import me.diamondforge.tokn.domain.model.TapBehavior
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScanQr: () -> Unit,
    onFromImage: () -> Unit,
    onManualEntry: () -> Unit,
    onSettings: () -> Unit,
    onBackup: () -> Unit,
    onEditAccount: (Long) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var fabMenuOpen by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    val selectionMode = uiState.selectionMode

    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }
    BackHandler(enabled = fabMenuOpen) { fabMenuOpen = false }
    BackHandler(enabled = showSearch && !selectionMode) {
        showSearch = false
        viewModel.updateSearchQuery("")
    }

    val items = uiState.items
    var listItems by remember(items) { mutableStateOf(items) }
    val canDrag = uiState.sort == AccountSort.CUSTOM

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            // Only honour drags in CUSTOM. Any other sort is derived from
            // account fields and persisting a manual order would silently
            // overwrite the user's saved CUSTOM ordering.
            if (!canDrag) return@rememberReorderableLazyListState
            listItems = listItems.toMutableList().apply { add(to.index, removeAt(from.index)) }
            viewModel.reorderAccounts(listItems.map { it.account })
        },
    )

    // Sort changes are a "tell me where the new winners are" gesture, not a
    // "preserve my reading position" gesture. Without this, keyed LazyColumn
    // keeps the previously-focused item in view, which hides the actual new
    // top item the user just sorted to.
    LaunchedEffect(uiState.sort) {
        if (uiState.sort != AccountSort.CUSTOM) {
            lazyListState.scrollToItem(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                HomeTopBar(
                    selectionMode = selectionMode,
                    selectedCount = uiState.selectedIds.size,
                    showSearch = showSearch,
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onToggleSearch = {
                        showSearch = !showSearch
                        if (!showSearch) viewModel.updateSearchQuery("")
                    },
                    onSettings = onSettings,
                    onClearSelection = { viewModel.clearSelection() },
                    onEditSelected = {
                        val id = uiState.selectedIds.singleOrNull() ?: return@HomeTopBar
                        viewModel.clearSelection()
                        onEditAccount(id)
                    },
                    onDeleteSelected = { showBulkDeleteConfirm = true },
                    onSelectAll = { viewModel.selectAll() },
                    sort = uiState.sort,
                    onSetSort = viewModel::setSort,
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (uiState.availableGroups.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        item(key = "__all__") {
                            FilterChip(
                                selected = uiState.selectedGroups.isEmpty(),
                                onClick = { viewModel.clearGroupFilter() },
                                label = { Text(stringResource(R.string.group_all)) },
                            )
                        }
                        items(uiState.availableGroups, key = { it.lowercase() }) { group ->
                            val isOn = uiState.selectedGroups.any {
                                it.equals(group, ignoreCase = true)
                            }
                            FilterChip(
                                selected = isOn,
                                onClick = { viewModel.toggleGroupFilter(group) },
                                label = { Text(group) },
                            )
                        }
                    }
                }

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (listItems.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        isFiltered = uiState.searchQuery.isNotBlank() || uiState.selectedGroups.isNotEmpty(),
                    )
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 32.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(listItems, key = { it.account.id }) { item ->
                            ReorderableItem(reorderableState, key = item.account.id) { isDragging ->
                                val copiedMessage = stringResource(R.string.code_copied)
                                val isSelected = item.account.id in uiState.selectedIds
                                val canReorder = canDrag && uiState.selectedIds.size == 1
                                val isMasked = uiState.tapToRevealEnabled &&
                                        item.account.id !in uiState.revealedIds
                                AccountCard(
                                    item = item,
                                    isDragging = isDragging,
                                    iconFetchEnabled = uiState.iconFetchEnabled,
                                    inSelectionMode = selectionMode,
                                    isSelected = isSelected,
                                    canReorder = canReorder,
                                    isMasked = isMasked,
                                    onTap = {
                                        if (selectionMode) {
                                            viewModel.toggleSelection(item.account.id)
                                        } else if (isMasked) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.reveal(item)
                                        } else when (uiState.tapBehavior) {
                                            TapBehavior.NONE -> Unit
                                            TapBehavior.SINGLE -> {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.copyToClipboard(item)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(copiedMessage)
                                                }
                                            }

                                            TapBehavior.DOUBLE -> Unit
                                        }
                                    },
                                    onDoubleTap = if (uiState.tapBehavior == TapBehavior.DOUBLE) {
                                        {
                                            if (!selectionMode && !isMasked) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.copyToClipboard(item)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(copiedMessage)
                                                }
                                            }
                                        }
                                    } else null,
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (selectionMode) {
                                            viewModel.toggleSelection(item.account.id)
                                        } else {
                                            viewModel.startSelection(item.account.id)
                                        }
                                    },
                                    onIncrementCounter = { viewModel.incrementHotpCounter(item.account.id) },
                                )
                            }
                        }

                        item(key = "__footer__") {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.home_account_count,
                                    listItems.size,
                                    listItems.size,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        if (!selectionMode) {
            HomeFabMenu(
                open = fabMenuOpen,
                onOpenChange = { fabMenuOpen = it },
                onScanQr = onScanQr,
                onFromImage = onFromImage,
                onManualEntry = onManualEntry,
            )
        }
    }

    if (showBulkDeleteConfirm) {
        BulkDeleteDialog(
            count = uiState.selectedIds.size,
            onConfirm = {
                viewModel.deleteSelected()
                showBulkDeleteConfirm = false
            },
            onDismiss = { showBulkDeleteConfirm = false },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, isFiltered: Boolean = false) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isFiltered) stringResource(R.string.no_results_title)
                else stringResource(R.string.no_accounts_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isFiltered) stringResource(R.string.no_results_subtitle)
                else stringResource(R.string.no_accounts_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
