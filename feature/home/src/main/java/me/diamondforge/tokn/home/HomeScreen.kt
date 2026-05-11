package me.diamondforge.tokn.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.collectAsState
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.diamondforge.tokn.domain.model.OtpType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.absoluteValue

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
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showAddSheet by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var pendingDeleteItem by remember { mutableStateOf<AccountItem?>(null) }

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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) viewModel.updateSearchQuery("")
                    }) {
                        Icon(
                            if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_account))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (showSearch) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                )
            }

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
                            AccountCard(
                                item = item,
                                isDragging = isDragging,
                                iconFetchEnabled = uiState.iconFetchEnabled,
                                onCopy = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("OTP", item.otpResult.code),
                                    )
                                    scope.launch {
                                        snackbarHostState.showSnackbar(copiedMessage)
                                        delay(30_000)
                                        clipboard.clearPrimaryClip()
                                    }
                                },
                                onDelete = { pendingDeleteItem = item },
                                onEdit = { onEditAccount(item.account.id) },
                                onIncrementCounter = { viewModel.incrementHotpCounter(item.account.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDeleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAccount(item.account.id)
                    pendingDeleteItem = null
                }) {
                    Text(text = stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteItem = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            AddOptionsSheet(
                onScanQr = { showAddSheet = false; onScanQr() },
                onFromImage = { showAddSheet = false; onFromImage() },
                onManualEntry = { showAddSheet = false; onManualEntry() },
            )
        }
    }
}

@Composable
private fun AddOptionsSheet(
    onScanQr: () -> Unit,
    onFromImage: () -> Unit,
    onManualEntry: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.add_scan_qr)) },
            leadingContent = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onScanQr),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.add_from_image)) },
            leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onFromImage),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.add_manually)) },
            leadingContent = { Icon(Icons.Default.Keyboard, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onManualEntry),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ReorderableCollectionItemScope.AccountCard(
    item: AccountItem,
    isDragging: Boolean,
    iconFetchEnabled: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onIncrementCounter: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { false })

    LaunchedEffect(dismissState.targetValue) {
        if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onCopy, onLongClick = onEdit),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 1.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IssuerAvatar(issuer = item.account.issuer, iconFetchEnabled = iconFetchEnabled)
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
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = null,
                    modifier = Modifier.draggableHandle(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
            modifier = modifier.size(38.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
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
