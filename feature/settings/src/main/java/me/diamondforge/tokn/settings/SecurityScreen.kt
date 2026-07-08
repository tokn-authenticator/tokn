package me.diamondforge.tokn.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

    SettingsScaffold(
        title = stringResource(R.string.security),
        onBack = onBack,
    ) {
        item { SettingsSectionHeader(stringResource(R.string.section_vault)) }
        item {
            SettingsGroup(
                items = listOf(
                    {
                        SettingsRow(
                            title = stringResource(R.string.encrypt_vault),
                            subtitle = stringResource(R.string.encrypt_vault_desc),
                            icon = Icons.Default.Shield,
                            trailing = {
                                SettingsSwitch(
                                    checked = uiState.encryptionEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled) showSetupEncryptionDialog = true
                                        else showDisableEncryptionDialog = true
                                    },
                                )
                            },
                        )
                    },
                ),
            )
        }

        item {
            AnimatedVisibility(
                visible = uiState.encryptionEnabled,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    SettingsSectionHeader(stringResource(R.string.section_authentication))
                    SettingsGroup(
                        items = listOf(
                            {
                                SettingsRow(
                                    title = stringResource(R.string.biometric_unlock),
                                    subtitle = stringResource(R.string.biometric_unlock_desc),
                                    icon = Icons.Default.Fingerprint,
                                    trailing = {
                                        SettingsSwitch(
                                            checked = uiState.biometricEnabled,
                                            onCheckedChange = viewModel::setBiometricEnabled,
                                        )
                                    },
                                )
                            },
                            {
                                SettingsToggleRow(
                                    title = stringResource(R.string.auto_lock),
                                    icon = Icons.Default.Lock,
                                    options = listOf(
                                        0 to stringResource(R.string.lock_immediately),
                                        30 to stringResource(R.string.lock_30s),
                                        60 to stringResource(R.string.lock_1m),
                                        300 to stringResource(R.string.lock_5m),
                                    ),
                                    selected = uiState.autoLockTimeoutSeconds,
                                    onSelect = { viewModel.setAutoLockTimeout(it) },
                                )
                            },
                            {
                                SettingsRow(
                                    title = stringResource(R.string.password_reminder),
                                    subtitle = when {
                                        !uiState.passwordReminderEnabled ->
                                            stringResource(R.string.password_reminder_desc)

                                        uiState.passwordReminderNextDays <= 0 ->
                                            stringResource(R.string.password_reminder_due)

                                        else -> pluralStringResource(
                                            R.plurals.password_reminder_next,
                                            uiState.passwordReminderNextDays,
                                            uiState.passwordReminderNextDays,
                                        )
                                    },
                                    icon = Icons.Default.Password,
                                    trailing = {
                                        SettingsSwitch(
                                            checked = uiState.passwordReminderEnabled,
                                            onCheckedChange = viewModel::setPasswordReminderEnabled,
                                        )
                                    },
                                )
                            },
                        ),
                    )
                }
            }
        }

        item { SettingsSectionHeader(stringResource(R.string.section_privacy)) }
        item {
            SettingsGroup(
                items = buildList {
                    add {
                        SettingsRow(
                            title = stringResource(R.string.screenshot_protection),
                            subtitle = stringResource(R.string.screenshot_protection_desc),
                            icon = Icons.Default.Screenshot,
                            trailing = {
                                SettingsSwitch(
                                    checked = !uiState.screenshotsEnabled,
                                    onCheckedChange = { protectionOn ->
                                        if (!protectionOn) showScreenshotWarning = true
                                        else viewModel.setScreenshotsEnabled(false)
                                    },
                                )
                            },
                        )
                    }
                    add {
                        SettingsRow(
                            title = stringResource(R.string.tap_to_reveal),
                            subtitle = stringResource(R.string.tap_to_reveal_desc),
                            icon = if (uiState.tapToRevealEnabled) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            trailing = {
                                SettingsSwitch(
                                    checked = uiState.tapToRevealEnabled,
                                    onCheckedChange = viewModel::setTapToRevealEnabled,
                                )
                            },
                        )
                    }
                    if (uiState.tapToRevealEnabled) {
                        add {
                            SettingsRow(
                                title = stringResource(R.string.stay_revealed),
                                subtitle = stringResource(R.string.stay_revealed_desc),
                                icon = Icons.Default.PushPin,
                                trailing = {
                                    SettingsSwitch(
                                        checked = uiState.stayRevealedEnabled,
                                        onCheckedChange = viewModel::setStayRevealedEnabled,
                                    )
                                },
                            )
                        }
                    }
                },
            )
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
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    keyboardOptions = KeyboardOptions(
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
                    keyboardOptions = KeyboardOptions(
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
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
