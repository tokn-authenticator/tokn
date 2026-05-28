package me.diamondforge.tokn.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

private const val MIN_EXPORT_PASSWORD_LENGTH = 8

internal sealed interface ExportRequest {
    data class ToknEncrypted(val password: String) : ExportRequest
    data object ToknPlain : ExportRequest
    data object OtpAuth : ExportRequest
    data object PlainText : ExportRequest
}

private enum class ExportFormat(val labelRes: Int) {
    Tokn(R.string.export_format_tokn),
    OtpAuth(R.string.export_format_otpauth),
    PlainText(R.string.export_format_plaintext),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportVaultDialog(
    onDismiss: () -> Unit,
    onConfirm: (ExportRequest) -> Unit,
) {
    var format by rememberSaveable { mutableStateOf(ExportFormat.Tokn) }
    var encrypt by rememberSaveable { mutableStateOf(true) }
    var riskAcknowledged by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var formatMenuOpen by remember { mutableStateOf(false) }

    // otpauth URI lists are plaintext by design: re-importing depends on the
    // bytes being valid otpauth://, which encryption would defeat.
    val encryptionAvailable = format == ExportFormat.Tokn
    val effectivelyEncrypted = encryptionAvailable && encrypt

    val canConfirm = when {
        effectivelyEncrypted -> password.length >= MIN_EXPORT_PASSWORD_LENGTH
        else -> riskAcknowledged
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_vault)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.export_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                ExposedDropdownMenuBox(
                    expanded = formatMenuOpen,
                    onExpandedChange = { formatMenuOpen = !formatMenuOpen },
                ) {
                    OutlinedTextField(
                        value = stringResource(format.labelRes),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.export_format_label)) },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    DropdownMenu(
                        expanded = formatMenuOpen,
                        onDismissRequest = { formatMenuOpen = false },
                    ) {
                        ExportFormat.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes)) },
                                onClick = {
                                    format = option
                                    formatMenuOpen = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = effectivelyEncrypted,
                        enabled = encryptionAvailable,
                        onCheckedChange = { encrypt = it },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.export_encrypt_checkbox),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (encryptionAvailable) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
                if (effectivelyEncrypted) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.backup_password)) },
                        supportingText = { Text(stringResource(R.string.export_password_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
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
                            imeAction = ImeAction.Done,
                        ),
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.export_risk_warning),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = riskAcknowledged,
                            onCheckedChange = { riskAcknowledged = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.export_risk_acknowledge),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    onConfirm(
                        when {
                            effectivelyEncrypted -> ExportRequest.ToknEncrypted(password)
                            format == ExportFormat.Tokn -> ExportRequest.ToknPlain
                            format == ExportFormat.OtpAuth -> ExportRequest.OtpAuth
                            else -> ExportRequest.PlainText
                        },
                    )
                },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
