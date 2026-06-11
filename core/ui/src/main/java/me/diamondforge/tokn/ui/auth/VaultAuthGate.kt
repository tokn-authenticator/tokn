package me.diamondforge.tokn.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.diamondforge.tokn.data.security.VaultAuthMode
import me.diamondforge.tokn.ui.R

/**
 * Gates a sensitive action behind the vault's biometric or password. Flip
 * [active] to true to start; the gate resolves the required mode, runs the
 * biometric prompt (falling back to the password dialog on cancel/failure) and
 * calls [onAuthorized] once confirmed, or [onCancelled] if the user backs out.
 * With no app lock ([VaultAuthMode.NONE]) it authorizes immediately.
 *
 * The caller owns [active]: reset it to false from both callbacks.
 */
@Composable
fun VaultAuthGate(
    active: Boolean,
    resolveMode: suspend () -> VaultAuthMode,
    authenticateBiometric: suspend () -> Boolean,
    verifyPassword: suspend (String) -> Boolean,
    onAuthorized: () -> Unit,
    onCancelled: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }

    LaunchedEffect(active) {
        if (!active) {
            showPassword = false
            password = ""
            passwordVisible = false
            error = false
            verifying = false
            return@LaunchedEffect
        }
        when (resolveMode()) {
            VaultAuthMode.NONE -> onAuthorized()
            VaultAuthMode.BIOMETRIC ->
                if (authenticateBiometric()) onAuthorized() else showPassword = true

            VaultAuthMode.PASSWORD -> showPassword = true
        }
    }

    if (active && showPassword) {
        AlertDialog(
            onDismissRequest = onCancelled,
            title = { Text(stringResource(R.string.vault_auth_password_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.vault_auth_password_body))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            error = false
                        },
                        label = { Text(stringResource(R.string.vault_auth_password_label)) },
                        isError = error,
                        supportingText = if (error) {
                            { Text(stringResource(R.string.vault_auth_wrong_password)) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
                }
            },
            confirmButton = {
                TextButton(
                    enabled = password.isNotEmpty() && !verifying,
                    onClick = {
                        scope.launch {
                            verifying = true
                            if (verifyPassword(password)) {
                                onAuthorized()
                            } else {
                                error = true
                                verifying = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.vault_auth_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelled) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
