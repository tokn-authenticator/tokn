package me.diamondforge.tokn.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.diamondforge.tokn.domain.model.AccountSort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTopBar(
    selectionMode: Boolean,
    selectedCount: Int,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onSettings: () -> Unit,
    onClearSelection: () -> Unit,
    onEditSelected: () -> Unit,
    onExportSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSelectAll: () -> Unit,
    sort: AccountSort,
    onSetSort: (AccountSort) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    AnimatedContent(
        targetState = selectionMode,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "topbar",
    ) { inSelection ->
        if (inSelection) {
            SelectionTopBar(
                selectedCount = selectedCount,
                onClearSelection = onClearSelection,
                onEditSelected = onEditSelected,
                onExportSelected = onExportSelected,
                onDeleteSelected = onDeleteSelected,
                onSelectAll = onSelectAll,
                scrollBehavior = scrollBehavior,
            )
        } else {
            AnimatedContent(
                targetState = showSearch,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "search-topbar",
            ) { isSearching ->
                if (isSearching) {
                    SearchTopBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        onToggleSearch = onToggleSearch,
                        scrollBehavior = scrollBehavior,
                    )
                } else {
                    DefaultTopBar(
                        sort = sort,
                        onSetSort = onSetSort,
                        onToggleSearch = onToggleSearch,
                        onSettings = onSettings,
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onEditSelected: () -> Unit,
    onExportSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSelectAll: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
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
                IconButton(onClick = onExportSelected) {
                    Icon(
                        Icons.Default.QrCode2,
                        contentDescription = stringResource(R.string.export_qr),
                    )
                }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultTopBar(
    sort: AccountSort,
    onSetSort: (AccountSort) -> Unit,
    onToggleSearch: () -> Unit,
    onSettings: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
            IconButton(onClick = onToggleSearch) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.search),
                )
            }
            SortMenu(sort = sort, onSetSort = onSetSort)
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

@Composable
private fun SortMenu(
    sort: AccountSort,
    onSetSort: (AccountSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.cd_sort),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        AccountSort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(option.labelRes())) },
                leadingIcon = {
                    if (option == sort) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        // Keeps every row's leading slot the same width so the
                        // active row doesn't shift its label sideways.
                        Spacer(modifier = Modifier.size(24.dp))
                    }
                },
                onClick = {
                    expanded = false
                    onSetSort(option)
                },
            )
        }
    }
}

private fun AccountSort.labelRes(): Int = when (this) {
    AccountSort.CUSTOM -> R.string.sort_custom
    AccountSort.ISSUER_ASC -> R.string.sort_issuer_asc
    AccountSort.ISSUER_DESC -> R.string.sort_issuer_desc
    AccountSort.NAME_ASC -> R.string.sort_name_asc
    AccountSort.NAME_DESC -> R.string.sort_name_desc
    AccountSort.USAGE_COUNT -> R.string.sort_most_used
    AccountSort.LAST_USED -> R.string.sort_recently_used
}
