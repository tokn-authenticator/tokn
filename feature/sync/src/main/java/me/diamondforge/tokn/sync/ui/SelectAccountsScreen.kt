@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package me.diamondforge.tokn.sync.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.diamondforge.tokn.sync.R
import me.diamondforge.tokn.ui.auth.VaultAuthGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectAccountsScreen(
    viewModel: SendViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onAuthenticate: suspend () -> Boolean = { true },
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var initialized by remember { mutableStateOf(false) }

    // True once the user taps Continue and the transfer is awaiting the auth gate
    // (biometric or vault password) before the method picker opens.
    var pendingContinue by remember { mutableStateOf(false) }

    LaunchedEffect(accounts) {
        if (!initialized && accounts.isNotEmpty()) {
            selectedIds = accounts.map { it.id }.toSet()
            initialized = true
        }
    }

    val allSelected = accounts.isNotEmpty() && selectedIds.size == accounts.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_select_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        selectedIds =
                            if (allSelected) emptySet() else accounts.map { it.id }.toSet()
                    }) {
                        Text(
                            if (allSelected) stringResource(R.string.sync_select_none)
                            else stringResource(R.string.sync_select_all),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.sync_select_count,
                            selectedIds.size,
                            accounts.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        enabled = selectedIds.isNotEmpty(),
                        onClick = { pendingContinue = true },
                    ) { Text(stringResource(R.string.sync_continue)) }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!initialized && accounts.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    LoadingIndicator()
                }
            } else if (accounts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.sync_select_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn {
                    items(accounts, key = { it.id }) { account ->
                        val checked = account.id in selectedIds
                        ListItem(
                            headlineContent = { Text(account.issuer.ifBlank { account.accountName }) },
                            supportingContent = {
                                Text(account.accountName.takeIf { it.isNotBlank() } ?: " ")
                            },
                            leadingContent = {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null,
                                )
                            },
                            modifier = Modifier.clickable {
                                selectedIds = if (checked) {
                                    selectedIds - account.id
                                } else {
                                    selectedIds + account.id
                                }
                            },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }

    VaultAuthGate(
        active = pendingContinue,
        resolveMode = viewModel::authMode,
        authenticateBiometric = onAuthenticate,
        verifyPassword = viewModel::verifyVaultPassword,
        onAuthorized = {
            pendingContinue = false
            viewModel.setSelection(selectedIds)
            onContinue()
        },
        onCancelled = { pendingContinue = false },
    )
}
