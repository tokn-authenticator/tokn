package me.diamondforge.tokn.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAccountScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditAccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val installedPacks by viewModel.installedPacks.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var advancedExpanded by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_account_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!uiState.isLoaded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .padding(top = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditAvatar(
                customIconBytes = uiState.customIconBytes,
                packIconPath = uiState.packIconPath,
                issuer = uiState.issuer.ifBlank { uiState.accountName },
                onClick = { pickerOpen = true },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
            )

            OutlinedTextField(
                value = uiState.issuer,
                onValueChange = viewModel::updateIssuer,
                label = { Text(stringResource(R.string.edit_issuer)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = uiState.accountName,
                onValueChange = viewModel::updateAccountName,
                label = { Text(stringResource(R.string.edit_account_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                isError = uiState.accountName.isBlank() && uiState.error != null,
            )
            OutlinedTextField(
                value = uiState.secret,
                onValueChange = viewModel::updateSecret,
                label = { Text(stringResource(R.string.edit_secret)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next,
                ),
                isError = uiState.secret.isBlank() && uiState.error != null,
            )
            OutlinedTextField(
                value = uiState.group,
                onValueChange = viewModel::updateGroup,
                label = { Text(stringResource(R.string.edit_group)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
            )

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { advancedExpanded = !advancedExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.edit_advanced),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = advancedExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.edit_type),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OtpType.entries.forEach { type ->
                            FilterChip(
                                selected = uiState.type == type,
                                onClick = { viewModel.updateType(type) },
                                label = { Text(type.name) },
                            )
                        }
                    }

                    EditAlgorithmDropdown(
                        selected = uiState.algorithm,
                        onSelect = viewModel::updateAlgorithm,
                    )

                    Text(
                        text = stringResource(R.string.edit_digits),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(6, 8).forEach { d ->
                            FilterChip(
                                selected = uiState.digits == d,
                                onClick = { viewModel.updateDigits(d) },
                                label = { Text(d.toString()) },
                            )
                        }
                    }

                    if (uiState.type == OtpType.TOTP) {
                        Text(
                            text = stringResource(R.string.edit_period),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 60).forEach { p ->
                                FilterChip(
                                    selected = uiState.period == p,
                                    onClick = { viewModel.updatePeriod(p) },
                                    label = { Text("${p}s") },
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = uiState.counter,
                            onValueChange = viewModel::updateCounter,
                            label = { Text(stringResource(R.string.edit_counter)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                        )
                    }
                }
            }

            Button(
                onClick = { viewModel.saveChanges(onSaved) },
                enabled = !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Text(stringResource(R.string.edit_save))
            }
        }

        if (pickerOpen) {
            IconPickerSheet(
                issuer = uiState.issuer,
                installedPacks = installedPacks,
                hasIcon = uiState.hasIcon,
                onPickedFromGallery = viewModel::pickCustomIcon,
                onPickedFromPack = viewModel::pickPackIcon,
                onRemove = viewModel::clearIcon,
                onDismiss = { pickerOpen = false },
            )
        }
    }
}

@Composable
private fun EditAvatar(
    customIconBytes: ByteArray?,
    packIconPath: String?,
    issuer: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            customIconBytes != null -> AsyncImage(
                model = customIconBytes,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )

            packIconPath != null -> AsyncImage(
                model = File(packIconPath),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )

            else -> Text(
                text = issuer.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAlgorithmDropdown(
    selected: OtpAlgorithm,
    onSelect: (OtpAlgorithm) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.edit_algorithm)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OtpAlgorithm.entries.forEach { algo ->
                DropdownMenuItem(
                    text = { Text(algo.name) },
                    onClick = { onSelect(algo); expanded = false },
                )
            }
        }
    }
}
