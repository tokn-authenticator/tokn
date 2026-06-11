package me.diamondforge.tokn.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.diamondforge.tokn.R

@Composable
fun RootOfTrustUpgradeDialog(
    onConfirm: suspend (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var wrongPassword by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.rot_upgrade_title)) },
        text = {
            Column {
                Text(stringResource(R.string.rot_upgrade_body))
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        wrongPassword = false
                    },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = wrongPassword,
                    supportingText = if (wrongPassword) {
                        { Text(stringResource(R.string.wrong_password)) }
                    } else null,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
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
                enabled = password.isNotEmpty() && !isVerifying,
                onClick = {
                    scope.launch {
                        isVerifying = true
                        val ok = onConfirm(password)
                        isVerifying = false
                        if (!ok) wrongPassword = true
                    }
                },
            ) {
                Text(stringResource(R.string.rot_upgrade_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.rot_upgrade_later))
            }
        },
    )
}
