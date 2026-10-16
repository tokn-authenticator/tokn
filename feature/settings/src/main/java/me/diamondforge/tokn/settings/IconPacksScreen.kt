@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package me.diamondforge.tokn.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPacksScreen(
    onBack: () -> Unit,
    viewModel: IconPacksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.importSummary) {
        val summary = uiState.importSummary ?: return@LaunchedEffect
        val quiet = summary.failures.isEmpty() && uiState.autoMatch != null
        if (!quiet) importSummaryMessage(context, summary)?.let { snackbar.showSnackbar(it) }
        viewModel.clearImportSummary()
    }

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.importPacks(uris) }

    var rows by remember(uiState.rows) { mutableStateOf(uiState.rows) }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            rows = rows.toMutableList().apply { add(to.index, removeAt(from.index)) }
            viewModel.reorder(rows.map { it.uuid })
        },
    )

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.icon_packs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.icon_packs_import)) },
                supportingContent = {
                    val progress = uiState.importProgress
                    Text(
                        if (progress == null) {
                            stringResource(R.string.icon_packs_import_desc)
                        } else {
                            stringResource(
                                R.string.icon_packs_import_progress,
                                progress.current,
                                progress.total,
                            )
                        },
                    )
                },
                leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                trailingContent = {
                    if (uiState.isImporting) LoadingIndicator()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    zipLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/octet-stream"
                        )
                    )
                },
                enabled = !uiState.isImporting,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth(),
            ) {
                Text(stringResource(R.string.icon_packs_pick_zip))
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            if (rows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.icon_packs_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(state = lazyListState) {
                    items(rows, key = { it.uuid }) { row ->
                        ReorderableItem(reorderableState, key = row.uuid) {
                            IconPackListRow(
                                row = row,
                                onToggle = { viewModel.setPackEnabled(row.uuid, it) },
                                onDelete = { pendingDelete = row.uuid },
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }

        uiState.autoMatch?.let { proposal ->
            AutoMatchDialog(
                proposal = proposal,
                onApply = viewModel::applyAutoMatch,
                onDismiss = viewModel::dismissAutoMatch,
            )
        }

        pendingDelete?.let { uuid ->
            val usedBy = rows.firstOrNull { it.uuid == uuid }?.usedBy ?: 0
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.icon_packs_delete_title)) },
                text = {
                    Text(
                        if (usedBy > 0)
                            stringResource(R.string.icon_packs_delete_body_used, usedBy)
                        else
                            stringResource(R.string.icon_packs_delete_body)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.uninstall(uuid)
                        pendingDelete = null
                    }) {
                        Text(stringResource(R.string.icon_packs_delete_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.IconPackListRow(
    row: IconPackRow,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (row.enabled) 1f else 0.5f)
    ListItem(
        headlineContent = {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Text(
                    if (row.enabled) row.pack.pack.name
                    else "${row.pack.pack.name} · ${stringResource(R.string.icon_packs_disabled_badge)}",
                )
            }
        },
        supportingContent = {
            Text(
                stringResource(
                    R.string.icon_packs_count_v_used,
                    row.pack.iconCount,
                    row.pack.pack.version,
                    row.usedBy,
                )
            )
        },
        leadingContent = {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.cd_drag_handle),
                modifier = Modifier.draggableHandle(),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            val switchLabel = stringResource(R.string.icon_packs_enable_cd)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = row.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = switchLabel },
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.icon_packs_delete_cd),
                    )
                }
            }
        },
    )
}

@Composable
private fun AutoMatchDialog(
    proposal: AutoMatchProposal,
    onApply: (includeReplacements: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var includeReplacements by remember(proposal) { mutableStateOf(false) }
    val source = proposal.packNames.joinToString(", ")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.icon_packs_automatch_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (proposal.fresh.isNotEmpty()) {
                        pluralStringResource(
                            R.plurals.icon_packs_automatch_fresh,
                            proposal.fresh.size,
                            proposal.fresh.size,
                            source,
                        )
                    } else {
                        stringResource(R.string.icon_packs_automatch_replace_only, source)
                    },
                )
                if (proposal.replacements.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = includeReplacements,
                                role = Role.Checkbox,
                                onValueChange = { includeReplacements = it },
                            ),
                    ) {
                        Checkbox(checked = includeReplacements, onCheckedChange = null)
                        Text(
                            text = pluralStringResource(
                                R.plurals.icon_packs_automatch_replace,
                                proposal.replacements.size,
                                proposal.replacements.size,
                            ),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(includeReplacements) },
                enabled = proposal.fresh.isNotEmpty() || includeReplacements,
            ) {
                Text(stringResource(R.string.icon_packs_automatch_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.icon_packs_automatch_skip))
            }
        },
    )
}

private fun importSummaryMessage(context: Context, summary: ImportSummary): String? {
    val res = context.resources
    val failed = summary.failures.size
    return when {
        summary.imported == 0 && failed == 1 -> summary.failures.single()
        summary.imported == 0 && failed > 1 ->
            context.getString(R.string.icon_packs_import_none) + " " +
                    res.getQuantityString(R.plurals.icon_packs_import_failed, failed, failed)

        summary.imported == 0 -> null
        failed == 0 -> res.getQuantityString(
            R.plurals.icon_packs_import_result,
            summary.imported,
            summary.imported,
        )

        else -> res.getQuantityString(
            R.plurals.icon_packs_import_result,
            summary.imported,
            summary.imported,
        ) + " " + res.getQuantityString(R.plurals.icon_packs_import_failed, failed, failed)
    }
}
