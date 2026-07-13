@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RecycleBinScreen(
    onBack: () -> Unit,
    viewModel: RecycleBinViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var restoreTarget by remember { mutableStateOf<TrashedRow?>(null) }
    var deleteTarget by remember { mutableStateOf<TrashedRow?>(null) }
    var showEmptyConfirm by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.recycle_bin_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (uiState.items.isNotEmpty()) {
                        IconButton(onClick = { showEmptyConfirm = true }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.recycle_bin_empty),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
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
                uiState.items.isEmpty() -> RecycleBinEmpty()
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.items, key = { it.id }) { row ->
                        TrashedListRow(
                            row = row,
                            onRestore = { restoreTarget = row },
                            onDelete = { deleteTarget = row },
                        )
                    }
                }
            }
        }
    }

    restoreTarget?.let { row ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text(stringResource(R.string.recycle_bin_restore_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.recycle_bin_restore_body,
                        if (row.issuer.isNotBlank()) row.issuer else row.accountName,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restore(row.id)
                    restoreTarget = null
                }) {
                    Text(stringResource(R.string.restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    deleteTarget?.let { row ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.recycle_bin_delete_title)) },
            text = { Text(stringResource(R.string.recycle_bin_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteForever(row.id)
                    deleteTarget = null
                }) {
                    Text(
                        text = stringResource(R.string.delete_forever),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text(stringResource(R.string.recycle_bin_empty_confirm_title)) },
            text = { Text(stringResource(R.string.recycle_bin_empty_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.emptyBin()
                    showEmptyConfirm = false
                }) {
                    Text(
                        text = stringResource(R.string.empty_bin),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun TrashedListRow(
    row: TrashedRow,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(if (row.issuer.isNotBlank()) row.issuer else row.accountName)
        },
        supportingContent = {
            val label = if (row.issuer.isNotBlank() && row.accountName.isNotBlank()) {
                "${row.accountName} · "
            } else {
                ""
            }
            Text(label + timeLeftLabel(row.remainingMillis))
        },
        trailingContent = {
            Row {
                IconButton(onClick = onRestore) {
                    Icon(
                        Icons.Default.RestoreFromTrash,
                        contentDescription = stringResource(R.string.restore),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = stringResource(R.string.delete_forever),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

@Composable
private fun timeLeftLabel(remainingMillis: Long): String {
    val minutes = remainingMillis / 60_000L
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days >= 1 -> pluralStringResource(R.plurals.recycle_bin_days_left, days.toInt(), days.toInt())
        hours >= 1 -> pluralStringResource(R.plurals.recycle_bin_hours_left, hours.toInt(), hours.toInt())
        else -> {
            val m = minutes.coerceAtLeast(1).toInt()
            pluralStringResource(R.plurals.recycle_bin_minutes_left, m, m)
        }
    }
}

@Composable
private fun RecycleBinEmpty() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.recycle_bin_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.recycle_bin_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
