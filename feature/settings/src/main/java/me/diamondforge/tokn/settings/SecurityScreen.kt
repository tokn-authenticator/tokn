package me.diamondforge.tokn.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showScreenshotWarning by remember { mutableStateOf(false) }
    var showSetupEncryptionDialog by remember { mutableStateOf(false) }
    var showDisableEncryptionDialog by remember { mutableStateOf(false) }

    if (showScreenshotWarning) {
        AlertDialog(
            onDismissRequest = { showScreenshotWarning = false },
            title = { Text(stringResource(R.string.screenshot_warning_title)) },
            text = { Text(stringResource(R.string.screenshot_warning_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setScreenshotsEnabled(true)
                    showScreenshotWarning = false
                }) {
                    Text(
                        text = stringResource(R.string.enable_anyway),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showScreenshotWarning = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showSetupEncryptionDialog) {
        SetupEncryptionDialog(
            onConfirm = { password ->
                viewModel.setupEncryption(password)
                showSetupEncryptionDialog = false
            },
            onDismiss = { showSetupEncryptionDialog = false },
        )
    }

    if (showDisableEncryptionDialog) {
        DisableEncryptionDialog(
            passwordVerificationFailed = uiState.passwordVerificationFailed,
            onPasswordChanged = { viewModel.clearPasswordError() },
            onConfirm = { password ->
                viewModel.disableEncryption(password)
            },
            onDismiss = {
                viewModel.clearPasswordError()
                showDisableEncryptionDialog = false
            },
        )
        LaunchedEffect(uiState.encryptionEnabled) {
            if (!uiState.encryptionEnabled) showDisableEncryptionDialog = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.security)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                SectionLabel(stringResource(R.string.section_vault))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.encrypt_vault)) },
                    supportingContent = { Text(stringResource(R.string.encrypt_vault_desc)) },
                    leadingContent = { Icon(Icons.Default.Shield, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = uiState.encryptionEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) showSetupEncryptionDialog = true
                                else showDisableEncryptionDialog = true
                            },
                        )
                    },
                )
            }

            item {
                AnimatedVisibility(
                    visible = uiState.encryptionEnabled,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    HorizontalDivider()
                }
            }
            item {
                AnimatedVisibility(
                    visible = uiState.encryptionEnabled,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    SectionLabel(stringResource(R.string.section_authentication))
                }
            }
            item {
                AnimatedVisibility(
                    visible = uiState.encryptionEnabled,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.biometric_unlock)) },
                        supportingContent = { Text(stringResource(R.string.biometric_unlock_desc)) },
                        leadingContent = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = uiState.biometricEnabled,
                                onCheckedChange = viewModel::setBiometricEnabled,
                            )
                        },
                    )
                }
            }
            item {
                AnimatedVisibility(
                    visible = uiState.encryptionEnabled,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.auto_lock)) },
                        leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                        supportingContent = {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                listOf(
                                    0 to stringResource(R.string.lock_immediately),
                                    30 to stringResource(R.string.lock_30s),
                                    60 to stringResource(R.string.lock_1m),
                                    300 to stringResource(R.string.lock_5m),
                                ).forEach { (seconds, label) ->
                                    FilterChip(
                                        selected = uiState.autoLockTimeoutSeconds == seconds,
                                        onClick = { viewModel.setAutoLockTimeout(seconds) },
                                        label = { Text(label) },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                            }
                        },
                    )
                }
            }

            item { HorizontalDivider() }
            item {
                SectionLabel(stringResource(R.string.section_privacy))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.screenshot_protection)) },
                    supportingContent = { Text(stringResource(R.string.screenshot_protection_desc)) },
                    leadingContent = { Icon(Icons.Default.Screenshot, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = !uiState.screenshotsEnabled,
                            onCheckedChange = { protectionOn ->
                                if (!protectionOn) showScreenshotWarning = true
                                else viewModel.setScreenshotsEnabled(false)
                            },
                        )
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.tap_to_reveal)) },
                    supportingContent = { Text(stringResource(R.string.tap_to_reveal_desc)) },
                    leadingContent = {
                        Icon(
                            if (uiState.tapToRevealEnabled) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = null,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.tapToRevealEnabled,
                            onCheckedChange = viewModel::setTapToRevealEnabled,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SetupEncryptionDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    val tooShort = password.isNotEmpty() && password.length < 8
    val mismatch = confirm.isNotEmpty() && password != confirm
    val valid = password.length >= 8 && password == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_setup_title)) },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.vault_setup_desc),
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
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                    ),
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
                    visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { confirmVisible = !confirmVisible }) {
                            Icon(
                                if (confirmVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = valid) {
                Text(stringResource(R.string.enable_encryption))
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
private fun DisableEncryptionDialog(
    passwordVerificationFailed: Boolean,
    onPasswordChanged: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.disable_encryption_title)) },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.disable_encryption_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onPasswordChanged()
                    },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = passwordVerificationFailed,
                    supportingText = if (passwordVerificationFailed) {
                        { Text(stringResource(R.string.wrong_password)) }
                    } else null,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty(),
            ) {
                Text(
                    stringResource(R.string.disable_encryption_confirm),
                    color = if (password.isNotEmpty()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
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
