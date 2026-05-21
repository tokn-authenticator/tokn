// Selection action-mode and expanding FAB-menu UX inspired by Aegis Authenticator
// (https://github.com/beemdevelopment/Aegis, GPL-3.0). Reimplemented in Compose.
package me.diamondforge.tokn.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.collectAsState
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.diamondforge.tokn.domain.model.OtpType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.absoluteValue

private const val FAB_STAGGER_MS = 50L

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

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            listItems = listItems.toMutableList().apply { add(to.index, removeAt(from.index)) }
            viewModel.reorderAccounts(listItems.map { it.account })
        },
    )

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
                    item {
                        FilterChip(
                            selected = uiState.selectedGroup == null,
                            onClick = { viewModel.selectGroup(null) },
                            label = { Text(stringResource(R.string.group_all)) },
                        )
                    }
                    items(uiState.availableGroups) { group ->
                        FilterChip(
                            selected = uiState.selectedGroup == group,
                            onClick = { viewModel.selectGroup(group) },
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
                    isFiltered = uiState.searchQuery.isNotBlank() || uiState.selectedGroup != null,
                )
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(listItems, key = { it.account.id }) { item ->
                        ReorderableItem(reorderableState, key = item.account.id) { isDragging ->
                            val copiedMessage = stringResource(R.string.code_copied)
                            val isSelected = item.account.id in uiState.selectedIds
                            val canReorder = uiState.selectedIds.size == 1
                            AccountCard(
                                item = item,
                                isDragging = isDragging,
                                iconFetchEnabled = uiState.iconFetchEnabled,
                                inSelectionMode = selectionMode,
                                isSelected = isSelected,
                                canReorder = canReorder,
                                onTap = {
                                    if (selectionMode) {
                                        viewModel.toggleSelection(item.account.id)
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.copyToClipboard(item)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(copiedMessage)
                                        }
                                    }
                                },
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
                }
            }
        }
    }

        AnimatedVisibility(
            visible = fabMenuOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { fabMenuOpen = false },
                    ),
            )
        }

        if (!selectionMode) {
            val totalFabItems = 3
            // Bottom-up reveal on open, top-down collapse on close.
            // Aegis-style: items "expand from" / "collapse into" the FAB.
            var visibleFabItems by remember { mutableIntStateOf(0) }
            LaunchedEffect(fabMenuOpen) {
                if (fabMenuOpen) {
                    for (c in 1..totalFabItems) {
                        visibleFabItems = c
                        delay(FAB_STAGGER_MS)
                    }
                } else {
                    for (c in (totalFabItems - 1) downTo 0) {
                        visibleFabItems = c
                        delay(FAB_STAGGER_MS)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Threshold per row: top item appears last on open / disappears first on close.
                // Row index 0 (top) shows when count > 2; row 2 (bottom, closest to FAB) when count > 0.
                AnimatedVisibility(
                    visible = visibleFabItems > 2,
                    enter = fadeIn() + scaleIn() + expandVertically(),
                    exit = fadeOut() + scaleOut() + shrinkVertically(),
                ) {
                    FabMenuItem(
                        label = stringResource(R.string.add_scan_qr),
                        icon = Icons.Default.QrCodeScanner,
                    ) {
                        fabMenuOpen = false
                        onScanQr()
                    }
                }
                AnimatedVisibility(
                    visible = visibleFabItems > 1,
                    enter = fadeIn() + scaleIn() + expandVertically(),
                    exit = fadeOut() + scaleOut() + shrinkVertically(),
                ) {
                    FabMenuItem(
                        label = stringResource(R.string.add_from_image),
                        icon = Icons.Default.Image,
                    ) {
                        fabMenuOpen = false
                        onFromImage()
                    }
                }
                AnimatedVisibility(
                    visible = visibleFabItems > 0,
                    enter = fadeIn() + scaleIn() + expandVertically(),
                    exit = fadeOut() + scaleOut() + shrinkVertically(),
                ) {
                    FabMenuItem(
                        label = stringResource(R.string.add_manually),
                        icon = Icons.Default.Keyboard,
                    ) {
                        fabMenuOpen = false
                        onManualEntry()
                    }
                }
                val fabRotation by animateFloatAsState(
                    targetValue = if (fabMenuOpen) 45f else 0f,
                    label = "fab-rotation",
                )
                FloatingActionButton(onClick = { fabMenuOpen = !fabMenuOpen }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_account),
                        modifier = Modifier.rotate(fabRotation),
                    )
                }
            }
        }
    }

    if (showBulkDeleteConfirm) {
        val count = uiState.selectedIds.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = {
                Text(
                    pluralStringResource(R.plurals.delete_count_confirm_title, count, count),
                )
            },
            text = {
                Text(
                    pluralStringResource(R.plurals.delete_count_confirm_message, count, count),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    showBulkDeleteConfirm = false
                }) {
                    Text(text = stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    selectionMode: Boolean,
    selectedCount: Int,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onSettings: () -> Unit,
    onClearSelection: () -> Unit,
    onEditSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSelectAll: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    AnimatedContent(
        targetState = selectionMode,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "topbar",
    ) { inSelection ->
        if (inSelection) {
            var overflowOpen by remember { mutableStateOf(false) }
            TopAppBar(
                title = { Text(selectedCount.toString()) },
                navigationIcon = {
                    IconButton(onClick = onClearSelection) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_exit_selection),
                        )
                    }
                },
                actions = {
                    if (selectedCount == 1) {
                        IconButton(onClick = onEditSelected) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.edit),
                            )
                        }
                    }
                    IconButton(onClick = onDeleteSelected) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                        )
                    }
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.select_all)) },
                            onClick = {
                                overflowOpen = false
                                onSelectAll()
                            },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        } else {
            AnimatedContent(
                targetState = showSearch,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "search-topbar",
            ) { isSearching ->
                if (isSearching) {
                    val focusRequester = remember { FocusRequester() }
                    val keyboardController = LocalSoftwareKeyboardController.current
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onToggleSearch) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_exit_search),
                                )
                            }
                        },
                        title = {
                            TextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                placeholder = {
                                    Text(stringResource(R.string.search_placeholder))
                                },
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
                        actions = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.cd_clear_search),
                                    )
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                } else {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        actions = {
                            IconButton(onClick = onToggleSearch) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search),
                                )
                            }
                            IconButton(onClick = onSettings) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.settings),
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        }
    }
}

@Composable
private fun FabMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ReorderableCollectionItemScope.AccountCard(
    item: AccountItem,
    isDragging: Boolean,
    iconFetchEnabled: Boolean,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    canReorder: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onIncrementCounter: () -> Unit,
) {
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isDragging -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelected) {
                SelectionCheckmark()
            } else {
                IssuerAvatar(issuer = item.account.issuer, iconFetchEnabled = iconFetchEnabled)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.account.issuer,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = item.account.accountName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatOtpCode(item.otpResult.code),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (!inSelectionMode) {
                when (item.account.type) {
                    OtpType.TOTP -> {
                        val progress by animateFloatAsState(
                            targetValue = item.otpResult.remainingMillis.toFloat() /
                                item.otpResult.periodMillis.toFloat(),
                            label = "countdown",
                        )
                        val secondsRemaining = (item.otpResult.remainingMillis / 1000).toInt()
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(40.dp),
                                color = if (secondsRemaining <= 5)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Text(
                                text = secondsRemaining.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    OtpType.HOTP -> {
                        IconButton(onClick = onIncrementCounter) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                IconButton(onClick = onTap) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (canReorder) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.cd_drag_handle),
                    modifier = Modifier.draggableHandle(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SelectionCheckmark() {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun IssuerAvatar(issuer: String, iconFetchEnabled: Boolean, modifier: Modifier = Modifier) {
    if (iconFetchEnabled) {
        val url = remember(issuer) {
            val slug = issuer.lowercase().replace(Regex("[^a-z0-9]"), "")
            "https://cdn.simpleicons.org/$slug"
        }
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier.size(38.dp),
            contentScale = ContentScale.Fit,
        ) {
            val state = painter.state.collectAsState()
            if (state.value is AsyncImagePainter.State.Success) {
                SubcomposeAsyncImageContent()
            } else {
                LetterAvatarBox(issuer)
            }
        }
    } else {
        LetterAvatarBox(issuer, modifier)
    }
}

@Composable
private fun LetterAvatarBox(issuer: String, modifier: Modifier = Modifier) {
    val avatarColors = listOf(
        Color(0xFF1976D2),
        Color(0xFF388E3C),
        Color(0xFFF57C00),
        Color(0xFF7B1FA2),
        Color(0xFFD32F2F),
        Color(0xFF0097A7),
        Color(0xFF5D4037),
        Color(0xFF455A64),
        Color(0xFF00796B),
        Color(0xFFC62828),
    )
    val color = remember(issuer) { avatarColors[issuer.hashCode().absoluteValue % avatarColors.size] }
    val letter = issuer.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
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

private fun formatOtpCode(code: String): String = when (code.length) {
    6 -> "${code.substring(0, 3)} ${code.substring(3)}"
    8 -> "${code.substring(0, 4)} ${code.substring(4)}"
    else -> code
}
