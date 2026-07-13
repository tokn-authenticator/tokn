package me.diamondforge.tokn.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.diamondforge.tokn.domain.model.TapBehavior

@Composable
fun BehaviorScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDisableRecycleBin by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.app_behavior),
        onBack = onBack,
    ) {
        item { SettingsSectionHeader(stringResource(R.string.settings_section_general)) }
        item {
            SettingsGroup(
                items = listOf(
                    {
                        SettingsToggleRow(
                            title = stringResource(R.string.tap_to_copy),
                            icon = Icons.Default.TouchApp,
                            options = listOf(
                                TapBehavior.NONE to stringResource(R.string.tap_none),
                                TapBehavior.SINGLE to stringResource(R.string.tap_single),
                                TapBehavior.DOUBLE to stringResource(R.string.tap_double),
                            ),
                            selected = uiState.tapBehavior,
                            onSelect = { viewModel.setTapBehavior(it) },
                        )
                    },
                    {
                        SettingsRow(
                            title = stringResource(R.string.show_next_code),
                            subtitle = stringResource(R.string.show_next_code_desc),
                            icon = Icons.Default.Schedule,
                            trailing = {
                                SettingsSwitch(
                                    checked = uiState.showNextCodeEnabled,
                                    onCheckedChange = { viewModel.setShowNextCodeEnabled(it) },
                                )
                            },
                        )
                    },
                    {
                        SettingsRow(
                            title = stringResource(R.string.recycle_bin_setting_title),
                            subtitle = stringResource(R.string.recycle_bin_setting_desc),
                            icon = Icons.Default.Delete,
                            trailing = {
                                SettingsSwitch(
                                    checked = uiState.recycleBinEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled) {
                                            viewModel.setRecycleBinEnabled(true)
                                        } else if (uiState.trashedCount > 0) {
                                            showDisableRecycleBin = true
                                        } else {
                                            viewModel.setRecycleBinEnabled(false)
                                        }
                                    },
                                )
                            },
                        )
                    },
                ),
            )
        }
    }

    if (showDisableRecycleBin) {
        AlertDialog(
            onDismissRequest = { showDisableRecycleBin = false },
            title = { Text(stringResource(R.string.recycle_bin_disable_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.recycle_bin_disable_body,
                        uiState.trashedCount,
                        uiState.trashedCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.disableRecycleBin()
                    showDisableRecycleBin = false
                }) {
                    Text(
                        text = stringResource(R.string.disable),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableRecycleBin = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
