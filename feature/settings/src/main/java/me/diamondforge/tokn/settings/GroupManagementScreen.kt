@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package me.diamondforge.tokn.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.diamondforge.tokn.ui.GroupColorDot
import me.diamondforge.tokn.ui.GroupColorPicker
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManagementScreen(
    onBack: () -> Unit,
    viewModel: GroupManagementViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var renameTarget by remember { mutableStateOf<GroupRow?>(null) }
    var deleteTarget by remember { mutableStateOf<GroupRow?>(null) }
    var colorTarget by remember { mutableStateOf<GroupRow?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    var groups by remember(uiState.groups) { mutableStateOf(uiState.groups) }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            groups = groups.toMutableList().apply { add(to.index, removeAt(from.index)) }
            viewModel.reorder(groups.map { it.name })
        },
    )

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_groups_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.groups_add_cd))
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                uiState.isLoading -> LoadingIndicator()
                groups.isEmpty() -> GroupsEmpty()
                else -> LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                    items(groups, key = { it.name.lowercase() }) { row ->
                        ReorderableItem(reorderableState, key = row.name.lowercase()) {
                            GroupListRow(
                                row = row,
                                onColorTap = { colorTarget = row },
                                onRename = { renameTarget = row },
                                onDelete = { deleteTarget = row },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, colorArgb ->
                viewModel.create(name, colorArgb)
                showCreateDialog = false
            },
        )
    }

    renameTarget?.let { row ->
        RenameGroupDialog(
            row = row,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.rename(row.name, newName)
                renameTarget = null
            },
        )
    }

    colorTarget?.let { row ->
        EditGroupColorDialog(
            row = row,
            onDismiss = { colorTarget = null },
            onConfirm = { colorArgb ->
                viewModel.setColor(row.name, colorArgb)
                colorTarget = null
            },
        )
    }

    deleteTarget?.let { row ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.groups_delete_title)) },
            text = { Text(stringResource(R.string.groups_delete_body, row.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(row.name)
                    deleteTarget = null
                }) {
                    Text(stringResource(R.string.groups_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ReorderableCollectionItemScope.GroupListRow(
    row: GroupRow,
    onColorTap: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(row.name) },
        supportingContent = {
            Text(
                pluralStringResource(
                    R.plurals.groups_account_count,
                    row.accountCount,
                    row.accountCount,
                ),
            )
        },
        leadingContent = {
            IconButton(onClick = onColorTap) {
                GroupColorDot(colorArgb = row.colorArgb)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.groups_overflow_cd),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.groups_action_rename)) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.groups_action_delete)) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.cd_drag_handle),
                    modifier = Modifier.draggableHandle(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorArgb: Int?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var colorArgb by remember { mutableStateOf<Int?>(null) }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.groups_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.groups_rename_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                )
                GroupColorPicker(selected = colorArgb, onSelect = { colorArgb = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed, colorArgb) },
                enabled = trimmed.isNotEmpty(),
            ) {
                Text(stringResource(R.string.groups_create_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun EditGroupColorDialog(
    row: GroupRow,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
) {
    var colorArgb by remember(row.name) { mutableStateOf(row.colorArgb) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.groups_color_title, row.name)) },
        text = { GroupColorPicker(selected = colorArgb, onSelect = { colorArgb = it }) },
        confirmButton = {
            TextButton(onClick = { onConfirm(colorArgb) }) {
                Text(stringResource(R.string.groups_rename_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun RenameGroupDialog(
    row: GroupRow,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(row.name) { mutableStateOf(row.name) }
    val trimmed = value.trim()
    val canSave = trimmed.isNotEmpty() && trimmed != row.name

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.groups_rename_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.groups_rename_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = canSave,
            ) {
                Text(stringResource(R.string.groups_rename_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun GroupsEmpty() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.groups_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.groups_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
