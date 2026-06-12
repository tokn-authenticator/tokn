@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package me.diamondforge.tokn.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.diamondforge.tokn.ui.auth.VaultAuthGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    onScanMigration: () -> Unit = {},
    onAuthenticate: suspend () -> Boolean = { true },
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showExportDialog by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }

    // Non-null once an export format is chosen and awaiting the auth gate
    // (biometric or vault password) before the file picker opens.
    var pendingAuthRequest by remember { mutableStateOf<ExportRequest?>(null) }

    // Captured at OK-time and replayed once the file picker returns the target Uri.
    var pendingExportRequest by remember { mutableStateOf<ExportRequest?>(null) }
    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingExternalUri by remember { mutableStateOf<Uri?>(null) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var pendingError by remember { mutableStateOf<BackupError?>(null) }

    val pickerOptions = remember(viewModel) {
        viewModel.externalImporters.map { ImportPickerOption(it.id, it.displayName, it.noteRes) }
    }
    var selectedImporterId by rememberSaveable {
        mutableStateOf(pickerOptions.firstOrNull()?.id.orEmpty())
    }
    var externalPassword by rememberSaveable { mutableStateOf("") }
    var externalPasswordVisible by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> pendingExportUri = uri }

    val externalLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingExternalUri = uri }

    val proceedWithExport: (ExportRequest) -> Unit = { request ->
        pendingExportRequest = request
        viewModel.suppressLock()
        exportLauncher.launch(filenameFor(request))
    }

    LaunchedEffect(pendingExportUri) {
        val uri = pendingExportUri ?: return@LaunchedEffect
        when (val request = pendingExportRequest) {
            is ExportRequest.ToknEncrypted -> viewModel.exportBackup(uri, request.password)
            ExportRequest.ToknPlain -> viewModel.exportUnencryptedBackup(uri)
            ExportRequest.OtpAuth -> viewModel.exportOtpAuthUriList(uri)
            ExportRequest.PlainText -> viewModel.exportPlainText(uri)
            null -> Unit
        }
        pendingExportUri = null
        pendingExportRequest = null
    }

    LaunchedEffect(pendingExternalUri) {
        pendingExternalUri?.let { uri ->
            viewModel.importExternal(uri, selectedImporterId)
            pendingExternalUri = null
        }
    }

    val exportSuccessMsg = stringResource(R.string.export_success)

    LaunchedEffect(uiState.exportSuccess) {
        if (uiState.exportSuccess) {
            snackbarHostState.showSnackbar(exportSuccessMsg)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.importResult) {
        uiState.importResult?.let { result ->
            importResult = result
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            pendingError = it
            viewModel.clearMessages()
        }
    }

    if (showExportDialog) {
        ExportVaultDialog(
            onDismiss = { showExportDialog = false },
            onConfirm = { request ->
                showExportDialog = false
                pendingAuthRequest = request
            },
        )
    }

    VaultAuthGate(
        active = pendingAuthRequest != null,
        resolveMode = viewModel::authMode,
        authenticateBiometric = onAuthenticate,
        verifyPassword = viewModel::verifyVaultPassword,
        onAuthorized = {
            val request = pendingAuthRequest
            pendingAuthRequest = null
            if (request != null) proceedWithExport(request)
        },
        onCancelled = { pendingAuthRequest = null },
    )

    if (showSourcePicker) {
        SourcePickerDialog(
            options = pickerOptions,
            selectedId = selectedImporterId,
            onSelect = { selectedImporterId = it },
            onDismiss = { showSourcePicker = false },
            onConfirm = {
                showSourcePicker = false
                viewModel.suppressLock()
                val mimes = viewModel.externalImporters
                    .first { it.id == selectedImporterId }
                    .acceptedMimeTypes
                externalLauncher.launch(mimes)
            },
        )
    }

    importResult?.let { result ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text(stringResource(R.string.import_result_title)) },
            text = {
                val mainText = when {
                    result.found == 0 -> stringResource(R.string.import_result_empty)
                    result.imported == 0 -> stringResource(
                        R.string.import_result_all_duplicates,
                        result.found,
                    )

                    result.skipped == 0 -> stringResource(
                        R.string.import_result_all_imported,
                        result.imported,
                    )

                    else -> stringResource(
                        R.string.import_result_partial,
                        result.imported,
                        result.skipped,
                    )
                }
                val unsupportedText = if (result.unsupportedCount > 0) {
                    "\n\n" + stringResource(
                        R.string.import_result_unsupported_types,
                        result.unsupportedCount
                    )
                } else ""
                Text(mainText + unsupportedText)
            },
            confirmButton = {
                TextButton(onClick = { importResult = null }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    uiState.pendingExternal?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                viewModel.cancelExternalImport()
                externalPassword = ""
                externalPasswordVisible = false
            },
            title = {
                Text(stringResource(R.string.external_password_title, pending.displayName))
            },
            text = {
                OutlinedTextField(
                    value = externalPassword,
                    onValueChange = { externalPassword = it },
                    label = { Text(stringResource(R.string.backup_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (externalPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = {
                            externalPasswordVisible = !externalPasswordVisible
                        }) {
                            Icon(
                                if (externalPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.importExternal(pending.uri, pending.importerId, externalPassword)
                        externalPassword = ""
                        externalPasswordVisible = false
                    },
                    enabled = externalPassword.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.external_password_import))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.cancelExternalImport()
                    externalPassword = ""
                    externalPasswordVisible = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingError?.let { err ->
        AlertDialog(
            onDismissRequest = { pendingError = null },
            title = { Text(stringResource(err.titleRes)) },
            text = {
                Text(
                    if (err.messageArg != null) stringResource(err.messageRes, err.messageArg)
                    else stringResource(err.messageRes),
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingError = null }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LoadingIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                item {
                    SectionLabel(stringResource(R.string.section_import))
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.import_from_file)) },
                        supportingContent = { Text(stringResource(R.string.import_from_file_desc)) },
                        leadingContent = {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                        },
                        modifier = Modifier.clickable(enabled = pickerOptions.isNotEmpty()) {
                            showSourcePicker = true
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.import_from_google_auth)) },
                        supportingContent = { Text(stringResource(R.string.import_from_google_auth_desc)) },
                        leadingContent = {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            viewModel.suppressLock()
                            onScanMigration()
                        },
                    )
                }
                item { HorizontalDivider() }
                item {
                    SectionLabel(stringResource(R.string.section_export))
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.export_vault)) },
                        supportingContent = { Text(stringResource(R.string.export_vault_desc)) },
                        leadingContent = {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                        },
                        modifier = Modifier.clickable { showExportDialog = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourcePickerDialog(
    options: List<ImportPickerOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_source_picker_title)) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.id) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedId == option.id,
                            onClick = { onSelect(option.id) },
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(option.displayName, style = MaterialTheme.typography.bodyLarge)
                            option.noteRes?.let { res ->
                                Text(
                                    stringResource(res),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = selectedId.isNotEmpty(),
            ) {
                Text(stringResource(R.string.other_import_action_select_file))
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
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun filenameFor(request: ExportRequest): String {
    val ts = java.time.LocalDateTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
    return when (request) {
        is ExportRequest.ToknEncrypted -> "tokn_backup_$ts.enc.kv"
        ExportRequest.ToknPlain -> "tokn_backup_$ts.kv"
        ExportRequest.OtpAuth -> "tokn_otpauth_$ts.txt"
        ExportRequest.PlainText -> "tokn_export_$ts.txt"
    }
}

private data class ImportPickerOption(
    val id: String,
    val displayName: String,
    val noteRes: Int?,
)
