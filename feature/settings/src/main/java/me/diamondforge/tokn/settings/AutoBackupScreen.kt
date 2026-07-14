package me.diamondforge.tokn.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.diamondforge.tokn.backup.auto.AutoBackupStrategy
import java.text.DateFormat
import java.util.Date

private const val MIN_BACKUP_PASSWORD_LENGTH = 8
private const val PERSIST_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

@Composable
fun AutoBackupScreen(
    onBack: () -> Unit,
    viewModel: AutoBackupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDisableEncryptionDialog by remember { mutableStateOf(false) }

    val successMsg = stringResource(R.string.auto_backup_msg_success)
    val unchangedMsg = stringResource(R.string.auto_backup_msg_unchanged)
    val failedMsg = stringResource(R.string.auto_backup_msg_failed)
    val noPasswordMsg = stringResource(R.string.auto_backup_msg_no_password)

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                when (message) {
                    AutoBackupMessage.SUCCESS -> successMsg
                    AutoBackupMessage.UNCHANGED -> unchangedMsg
                    AutoBackupMessage.FAILED -> failedMsg
                    AutoBackupMessage.NO_PASSWORD -> noPasswordMsg
                },
            )
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, PERSIST_FLAGS)
            viewModel.setLocation(uri.toString())
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, PERSIST_FLAGS)
            viewModel.setLocation(uri.toString())
        }
    }

    val rotating = remember(uiState.location, uiState.mode) {
        uiState.location?.let { runCatching { DocumentsContract.isTreeUri(Uri.parse(it)) }.getOrNull() }
            ?: (uiState.mode == AutoBackupStrategy.ROTATING)
    }
    val locationLabel = remember(uiState.location, rotating) {
        uiState.location?.let { loc ->
            runCatching {
                val uri = Uri.parse(loc)
                val file = if (rotating) DocumentFile.fromTreeUri(context, uri)
                else DocumentFile.fromSingleUri(context, uri)
                file?.name
            }.getOrNull()
        }
    }

    if (showPasswordDialog) {
        BackupPasswordDialog(
            onConfirm = {
                viewModel.setPassword(it)
                showPasswordDialog = false
            },
            onDismiss = { showPasswordDialog = false },
        )
    }

    if (showDisableEncryptionDialog) {
        AlertDialog(
            onDismissRequest = { showDisableEncryptionDialog = false },
            title = { Text(stringResource(R.string.auto_backup_unencrypted_warning_title)) },
            text = { Text(stringResource(R.string.auto_backup_disable_encryption_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setEncrypt(false)
                    showDisableEncryptionDialog = false
                }) {
                    Text(
                        stringResource(R.string.auto_backup_disable_encryption_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableEncryptionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val issues = buildList {
        if (uiState.location == null) add(stringResource(R.string.auto_backup_issue_no_location))
        if (uiState.encrypt && !uiState.hasPassword) {
            add(stringResource(R.string.auto_backup_issue_no_password))
        }
    }
    val issuesTitle = stringResource(R.string.auto_backup_issues_title)

    SettingsScaffold(
        title = stringResource(R.string.auto_backup_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        if (uiState.enabled && issues.isNotEmpty()) {
            item { AutoBackupWarningBanner(title = issuesTitle, issues = issues) }
        }
        item {
            SettingsGroup(
                items = listOf {
                    SettingsRow(
                        title = stringResource(R.string.auto_backup_enable),
                        subtitle = stringResource(R.string.auto_backup_enable_desc),
                        icon = Icons.Default.Backup,
                        trailing = {
                            SettingsSwitch(
                                checked = uiState.enabled,
                                onCheckedChange = viewModel::setEnabled,
                            )
                        },
                    )
                },
            )
        }

        item {
            AnimatedVisibility(
                visible = uiState.enabled,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    SettingsSectionHeader(stringResource(R.string.auto_backup_location_section))
                    SettingsGroup(
                        items = buildList {
                            add {
                                SettingsToggleRow(
                                    title = stringResource(R.string.auto_backup_mode),
                                    icon = Icons.Default.Backup,
                                    options = listOf(
                                        AutoBackupStrategy.ROTATING to
                                                stringResource(R.string.auto_backup_mode_multiple),
                                        AutoBackupStrategy.SINGLE to
                                                stringResource(R.string.auto_backup_mode_single),
                                    ),
                                    selected = if (rotating) AutoBackupStrategy.ROTATING
                                    else AutoBackupStrategy.SINGLE,
                                    onSelect = viewModel::setMode,
                                )
                            }
                            add {
                                SettingsRow(
                                    title = if (rotating) {
                                        stringResource(R.string.auto_backup_choose_folder)
                                    } else {
                                        stringResource(R.string.auto_backup_choose_file)
                                    },
                                    subtitle = locationLabel
                                        ?: stringResource(R.string.auto_backup_location_none),
                                    icon = if (rotating) Icons.Default.Folder
                                    else Icons.Default.Description,
                                    onClick = {
                                        if (rotating) folderLauncher.launch(null)
                                        else fileLauncher.launch(defaultSingleFileName(uiState.encrypt))
                                    },
                                )
                            }
                            if (rotating) {
                                add {
                                    SettingsToggleRow(
                                        title = stringResource(R.string.auto_backup_versions),
                                        icon = Icons.Default.History,
                                        options = listOf(
                                            3 to "3",
                                            5 to "5",
                                            10 to "10",
                                            20 to "20",
                                        ),
                                        selected = uiState.versionsToKeep,
                                        onSelect = viewModel::setVersionsToKeep,
                                    )
                                }
                            }
                        },
                    )

                    SettingsSectionHeader(stringResource(R.string.auto_backup_encryption_section))
                    SettingsGroup(
                        items = buildList {
                            add {
                                SettingsRow(
                                    title = stringResource(R.string.auto_backup_encrypt),
                                    subtitle = stringResource(R.string.auto_backup_encrypt_desc),
                                    icon = Icons.Default.Lock,
                                    trailing = {
                                        SettingsSwitch(
                                            checked = uiState.encrypt,
                                            onCheckedChange = { on ->
                                                if (on) viewModel.setEncrypt(true)
                                                else showDisableEncryptionDialog = true
                                            },
                                        )
                                    },
                                )
                            }
                            if (uiState.encrypt) {
                                add {
                                    SettingsRow(
                                        title = stringResource(R.string.auto_backup_password),
                                        subtitle = if (uiState.hasPassword) {
                                            stringResource(R.string.auto_backup_password_set)
                                        } else {
                                            stringResource(R.string.auto_backup_password_not_set)
                                        },
                                        icon = Icons.Default.Key,
                                        onClick = { showPasswordDialog = true },
                                    )
                                }
                            } else {
                                add {
                                    SettingsRow(
                                        title = stringResource(R.string.auto_backup_unencrypted_warning_title),
                                        subtitle = stringResource(R.string.auto_backup_unencrypted_warning),
                                        icon = Icons.Default.Warning,
                                    )
                                }
                            }
                        },
                    )

                    SettingsSectionHeader(stringResource(R.string.auto_backup_status_section))
                    SettingsGroup(
                        items = listOf(
                            {
                                SettingsRow(
                                    title = stringResource(R.string.auto_backup_last_run),
                                    subtitle = lastRunSubtitle(uiState),
                                    icon = Icons.Default.History,
                                )
                            },
                            {
                                SettingsRow(
                                    title = stringResource(R.string.auto_backup_run_now),
                                    icon = Icons.Default.PlayArrow,
                                    onClick = { viewModel.backupNow() },
                                )
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun lastRunSubtitle(uiState: AutoBackupUiState): String = when {
    uiState.lastResultAt == 0L -> stringResource(R.string.auto_backup_last_never)
    uiState.lastError != null -> stringResource(R.string.auto_backup_last_error, uiState.lastError)
    else -> stringResource(
        R.string.auto_backup_last_success,
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(uiState.lastResultAt)),
    )
}

@Composable
private fun AutoBackupWarningBanner(title: String, issues: List<String>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                issues.forEach { issue ->
                    Text(
                        text = "•  $issue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

private fun defaultSingleFileName(encrypt: Boolean): String =
    if (encrypt) "tokn-auto-backup.enc.kv" else "tokn-auto-backup.json"

@Composable
private fun BackupPasswordDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val tooShort = password.isNotEmpty() && password.length < MIN_BACKUP_PASSWORD_LENGTH
    val mismatch = confirm.isNotEmpty() && password != confirm
    val valid = password.length >= MIN_BACKUP_PASSWORD_LENGTH && password == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_backup_password_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.auto_backup_password_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = tooShort,
                    supportingText = if (tooShort) {
                        { Text(stringResource(R.string.password_too_short)) }
                    } else null,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text(stringResource(R.string.confirm_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text(stringResource(R.string.passwords_dont_match)) }
                    } else null,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = valid) {
                Text(stringResource(R.string.auto_backup_password_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
