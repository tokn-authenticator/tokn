package me.diamondforge.tokn.onboarding.slides

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.diamondforge.tokn.onboarding.ImportError
import me.diamondforge.tokn.onboarding.OnboardingUiState
import me.diamondforge.tokn.onboarding.R
import me.diamondforge.tokn.onboarding.components.AppLogo

@Composable
fun WelcomeSlide(
    state: OnboardingUiState,
    onImport: (android.net.Uri, String?) -> Unit,
    onCancelPendingImport: () -> Unit,
    onSuppressLock: () -> Unit,
    onClearImportFeedback: () -> Unit,
) {
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { onImport(it, null) } }

    state.pendingTokn?.let { pending ->
        VaultPasswordPrompt(
            wrongPassword = state.importError == ImportError.WrongPassword,
            onSubmit = { password -> onImport(pending.uri, password) },
            onDismiss = onCancelPendingImport,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        AppLogo(size = 160.dp)
        Spacer(Modifier.weight(1f))

        val feedback = when {
            state.pendingTokn != null -> null
            state.importedCount != null ->
                stringResource(R.string.onboarding_import_success, state.importedCount)

            state.importError == ImportError.Redirect ->
                stringResource(R.string.onboarding_import_redirect_hint)

            state.importError == ImportError.Invalid ->
                stringResource(R.string.onboarding_import_invalid)

            else -> null
        }
        if (feedback != null) {
            val color = when (state.importError) {
                ImportError.Invalid, ImportError.WrongPassword -> MaterialTheme.colorScheme.error
                ImportError.Redirect -> MaterialTheme.colorScheme.onSurfaceVariant
                null -> MaterialTheme.colorScheme.primary
            }
            Text(
                text = feedback,
                style = MaterialTheme.typography.bodySmall,
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = {
                onClearImportFeedback()
                onSuppressLock()
                importLauncher.launch(
                    arrayOf(
                        "application/json",
                        "application/octet-stream",
                        "*/*"
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_import_button))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.onboarding_import_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VaultPasswordPrompt(
    wrongPassword: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onboarding_import_password_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.onboarding_import_password_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = wrongPassword,
                    supportingText = if (wrongPassword) {
                        { Text(stringResource(R.string.onboarding_import_wrong_password)) }
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
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotEmpty(),
                onClick = { onSubmit(password) },
            ) {
                Text(stringResource(R.string.onboarding_import_password_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
