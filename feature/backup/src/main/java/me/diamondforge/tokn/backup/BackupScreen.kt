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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    onScanMigration: () -> Unit = {},
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var exportPassword by rememberSaveable { mutableStateOf("") }
    var importPassword by rememberSaveable { mutableStateOf("") }
    var exportPasswordVisible by remember { mutableStateOf(false) }
    var importPasswordVisible by remember { mutableStateOf(false) }
    var exportUnencrypted by remember { mutableStateOf(false) }
    var showUnencryptedWarning by remember { mutableStateOf(false) }
    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingExternalUri by remember { mutableStateOf<Uri?>(null) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var pendingError by remember { mutableStateOf<BackupError?>(null) }
    var showSourcePicker by remember { mutableStateOf(false) }
    val migrationPickerLabel = stringResource(R.string.migration_picker_label)
    val migrationNoteRes = R.string.migration_picker_note
    val pickerOptions = remember(viewModel) {
        viewModel.externalImporters.map { importer ->
            ImportPickerOption.File(importer.id, importer.displayName, importer.noteRes)
        } + ImportPickerOption.MigrationQr(migrationPickerLabel, migrationNoteRes)
    }
    var selectedImporterId by rememberSaveable {
        mutableStateOf(pickerOptions.firstOrNull()?.id.orEmpty())
    }
    var externalPassword by rememberSaveable { mutableStateOf("") }
    var externalPasswordVisible by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> pendingExportUri = uri }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImportUri = uri }

    val externalLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingExternalUri = uri }

    LaunchedEffect(pendingExportUri) {
        pendingExportUri?.let { uri ->
            if (exportUnencrypted) viewModel.exportUnencryptedBackup(uri)
            else viewModel.exportBackup(uri, exportPassword)
            pendingExportUri = null
        }
    }

    LaunchedEffect(pendingImportUri) {
        pendingImportUri?.let { uri ->
            viewModel.importBackup(uri, importPassword)
            pendingImportUri = null
        }
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

    importResult?.let { result ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text(stringResource(R.string.import_result_title)) },
            text = {
                Text(
                    when {
                        result.found == 0 -> stringResource(R.string.import_result_empty)
                        result.imported == 0 -> stringResource(R.string.import_result_all_duplicates, result.found)
                        result.skipped == 0 -> stringResource(R.string.import_result_all_imported, result.imported)
                        else -> stringResource(R.string.import_result_partial, result.imported, result.skipped)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { importResult = null }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    if (showSourcePicker) {
        val selectedOption = pickerOptions.firstOrNull { it.id == selectedImporterId }
        val confirmLabelRes = when (selectedOption) {
            is ImportPickerOption.MigrationQr -> R.string.other_import_action_scan_qr
            is ImportPickerOption.File, null -> R.string.other_import_action_select_file
        }
        AlertDialog(
            onDismissRequest = { showSourcePicker = false },
            title = { Text(stringResource(R.string.other_import_select_app)) },
            text = {
                Column {
                    pickerOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedImporterId = option.id }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedImporterId == option.id,
                                onClick = { selectedImporterId = option.id },
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(option.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(option.noteRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSourcePicker = false
                        viewModel.suppressLock()
                        when (selectedOption) {
                            is ImportPickerOption.File ->
                                externalLauncher.launch(
                                    viewModel.externalImporters
                                        .first { it.id == selectedOption.id }
                                        .acceptedMimeTypes,
                                )
                            is ImportPickerOption.MigrationQr -> onScanMigration()
                            null -> Unit
                        }
                    },
                    enabled = selectedImporterId.isNotEmpty(),
                ) {
                    Text(stringResource(confirmLabelRes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSourcePicker = false }) {
                    Text(stringResource(R.string.cancel))
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
                        IconButton(onClick = { externalPasswordVisible = !externalPasswordVisible }) {
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
                    Text(stringResource(R.string.other_import_button))
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

    if (showUnencryptedWarning) {
        AlertDialog(
            onDismissRequest = { showUnencryptedWarning = false },
            title = { Text(stringResource(R.string.export_unencrypted_warning_title)) },
            text = { Text(stringResource(R.string.export_unencrypted_warning_body)) },
            confirmButton = {
                TextButton(onClick = {
                    exportUnencrypted = true
                    exportPassword = ""
                    showUnencryptedWarning = false
                }) {
                    Text(
                        text = stringResource(R.string.export_unencrypted_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnencryptedWarning = false }) {
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
                    else stringResource(err.messageRes)
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
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.export_section),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.export_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.export_unencrypted),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = exportUnencrypted,
                        onCheckedChange = { on ->
                            if (on) showUnencryptedWarning = true
                            else exportUnencrypted = false
                        },
                    )
                }
                if (exportUnencrypted) {
                    Text(
                        text = stringResource(R.string.export_unencrypted_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it },
                        label = { Text(stringResource(R.string.backup_password)) },
                        supportingText = { Text(stringResource(R.string.export_password_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (exportPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { exportPasswordVisible = !exportPasswordVisible }) {
                                Icon(
                                    if (exportPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }
                Button(
                    onClick = {
                        viewModel.suppressLock()
                        val ts = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
                        val filename = if (exportUnencrypted) "tokn_backup_$ts.kv" else "tokn_backup_$ts.enc.kv"
                        exportLauncher.launch(filename)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = exportUnencrypted || exportPassword.length >= 8,
                ) {
                    Text(stringResource(R.string.export_backup))
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.import_section),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.import_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = importPassword,
                    onValueChange = { importPassword = it },
                    label = { Text(stringResource(R.string.backup_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (importPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { importPasswordVisible = !importPasswordVisible }) {
                            Icon(
                                if (importPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )
                OutlinedButton(
                    onClick = {
                        viewModel.suppressLock()
                        importLauncher.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_backup))
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.other_import_section),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.other_import_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { showSourcePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = pickerOptions.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.other_import_select_app))
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private sealed class ImportPickerOption(
    val id: String,
    val displayName: String,
    val noteRes: Int,
) {
    class File(id: String, displayName: String, noteRes: Int) : ImportPickerOption(id, displayName, noteRes)
    class MigrationQr(displayName: String, noteRes: Int) : ImportPickerOption("migration_qr", displayName, noteRes)
}

