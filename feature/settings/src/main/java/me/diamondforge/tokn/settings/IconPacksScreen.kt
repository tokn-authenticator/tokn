package me.diamondforge.tokn.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPacksScreen(
    onBack: () -> Unit,
    viewModel: IconPacksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.importError) {
        uiState.importError?.let {
            snackbar.showSnackbar(it)
            viewModel.clearImportError()
        }
    }

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importPack(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.icon_packs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
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
                supportingContent = { Text(stringResource(R.string.icon_packs_import_desc)) },
                leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                trailingContent = {
                    if (uiState.isImporting) CircularProgressIndicator()
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

            if (uiState.packs.isEmpty()) {
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
                LazyColumn {
                    items(uiState.packs, key = { it.pack.uuid }) { installed ->
                        val usedBy = uiState.usageByUuid[installed.pack.uuid] ?: 0
                        ListItem(
                            headlineContent = { Text(installed.pack.name) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        R.string.icon_packs_count_v_used,
                                        installed.iconCount,
                                        installed.pack.version,
                                        usedBy,
                                    )
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { pendingDelete = installed.pack.uuid }) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        uiState.autoMatch?.let { proposal ->
            AlertDialog(
                onDismissRequest = viewModel::dismissAutoMatch,
                title = { Text(stringResource(R.string.icon_packs_automatch_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.icon_packs_automatch_body,
                            proposal.assignments.size,
                            proposal.packName,
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::applyAutoMatch) {
                        Text(stringResource(R.string.icon_packs_automatch_apply))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissAutoMatch) {
                        Text(stringResource(R.string.icon_packs_automatch_skip))
                    }
                },
            )
        }

        pendingDelete?.let { uuid ->
            val usedBy = uiState.usageByUuid[uuid] ?: 0
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
