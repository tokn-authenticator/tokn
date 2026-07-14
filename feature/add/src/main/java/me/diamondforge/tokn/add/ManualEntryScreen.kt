package me.diamondforge.tokn.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import me.diamondforge.tokn.ui.GroupsField
import me.diamondforge.tokn.ui.IconPickerSheet
import me.diamondforge.tokn.ui.IconPickerStrings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ManualEntryScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddAccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val installedPacks by viewModel.installedPacks.collectAsStateWithLifecycle()
    val availableGroups by viewModel.availableGroups.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var advancedExpanded by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.parsedAccount) {
        if (uiState.parsedAccount != null) {
            viewModel.applyParsedAccount()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.manual_entry_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AddAvatar(
                    customIconBytes = uiState.customIconBytes,
                    packIconPath = uiState.packIconPath,
                    issuer = uiState.issuer.ifBlank { uiState.accountName },
                    onClick = { pickerOpen = true },
                )
                if (uiState.hasSuggestedIcon) {
                    Text(
                        text = stringResource(R.string.icon_picker_suggestions),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = uiState.issuer,
                onValueChange = viewModel::updateIssuer,
                label = { Text(stringResource(R.string.issuer)) },
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
                label = { Text(stringResource(R.string.account_name)) },
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
                label = { Text(stringResource(R.string.secret_key)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next,
                ),
                isError = uiState.secret.isBlank() && uiState.error != null,
            )
            GroupsField(
                values = uiState.groups,
                onValuesChange = viewModel::updateGroups,
                label = stringResource(R.string.group_optional),
                modifier = Modifier.fillMaxWidth(),
                suggestions = availableGroups,
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
                    text = stringResource(R.string.advanced_settings),
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
                        text = stringResource(R.string.type),
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

                    AlgorithmDropdown(
                        selected = uiState.algorithm,
                        onSelect = viewModel::updateAlgorithm,
                    )

                    Text(
                        text = stringResource(R.string.digits),
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
                            text = stringResource(R.string.period_seconds),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 60).forEach { p ->
                                FilterChip(
                                    selected = uiState.period == p,
                                    onClick = { viewModel.updatePeriod(p) },
                                    label = { Text(stringResource(R.string.period_value, p)) },
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.saveAccount(onSaved) },
                enabled = !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Text(stringResource(R.string.save))
            }
        }

        if (pickerOpen) {
            IconPickerSheet(
                issuer = uiState.issuer,
                installedPacks = installedPacks,
                hasIcon = uiState.hasIcon,
                strings = iconPickerStrings(),
                onPickedFromGallery = viewModel::pickCustomIcon,
                onPickedFromPack = viewModel::pickPackIcon,
                onRemove = viewModel::clearIcon,
                onDismiss = { pickerOpen = false },
            )
        }
    }
}

@Composable
private fun AddAvatar(
    customIconBytes: ByteArray?,
    packIconPath: String?,
    issuer: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(96.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            when {
                customIconBytes != null -> AsyncImage(
                    model = customIconBytes,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )

                packIconPath != null -> AsyncImage(
                    model = java.io.File(packIconPath),
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
        AvatarEditBadge(modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun AvatarEditBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlgorithmDropdown(
    selected: OtpAlgorithm,
    onSelect: (OtpAlgorithm) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.algorithm)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OtpAlgorithm.entries.forEach { algo ->
                DropdownMenuItem(
                    text = { Text(algo.name) },
                    onClick = {
                        onSelect(algo)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun iconPickerStrings() = IconPickerStrings(
    title = stringResource(R.string.icon_picker_title),
    gallery = stringResource(R.string.icon_picker_gallery),
    remove = stringResource(R.string.icon_picker_remove),
    searchPlaceholder = stringResource(R.string.icon_picker_search_placeholder),
    noPacks = stringResource(R.string.icon_picker_no_packs),
    noSearchResults = stringResource(R.string.icon_picker_no_search_results),
    suggestions = stringResource(R.string.icon_picker_suggestions),
    cancel = stringResource(R.string.icon_picker_cancel),
)
