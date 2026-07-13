package me.diamondforge.tokn.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onBehavior: () -> Unit,
    onSecurity: () -> Unit,
    onGroups: () -> Unit,
    onBackup: () -> Unit,
    onSync: () -> Unit,
    onRecycleBin: () -> Unit,
    onAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
    ) {
        item { SettingsSectionHeader(stringResource(R.string.settings_section_general)) }
        item {
            SettingsGroup(
                items = buildList {
                    add {
                        SettingsRow(
                            title = stringResource(R.string.appearance),
                            subtitle = stringResource(R.string.appearance_desc),
                            icon = Icons.Default.Palette,
                            onClick = onAppearance,
                            trailing = { Chevron() },
                        )
                    }
                    add {
                        SettingsRow(
                            title = stringResource(R.string.app_behavior),
                            subtitle = stringResource(R.string.app_behavior_desc),
                            icon = Icons.Default.Tune,
                            onClick = onBehavior,
                            trailing = { Chevron() },
                        )
                    }
                    add {
                        SettingsRow(
                            title = stringResource(R.string.security),
                            subtitle = stringResource(R.string.security_desc),
                            icon = Icons.Default.Security,
                            onClick = onSecurity,
                            trailing = { Chevron() },
                        )
                    }
                    add {
                        SettingsRow(
                            title = stringResource(R.string.settings_groups_title),
                            subtitle = stringResource(R.string.settings_groups_desc),
                            icon = Icons.AutoMirrored.Filled.Label,
                            onClick = onGroups,
                            trailing = { Chevron() },
                        )
                    }
                    if (uiState.recycleBinEnabled) {
                        add {
                            SettingsRow(
                                title = stringResource(R.string.recycle_bin_title),
                                subtitle = stringResource(R.string.recycle_bin_desc),
                                icon = Icons.Default.Delete,
                                onClick = onRecycleBin,
                                trailing = { Chevron() },
                            )
                        }
                    }
                },
            )
        }

        item { SettingsSectionHeader(stringResource(R.string.settings_section_data)) }
        item {
            SettingsGroup(
                items = listOf(
                    {
                        SettingsRow(
                            title = stringResource(R.string.backup_restore),
                            subtitle = stringResource(R.string.backup_desc),
                            icon = Icons.Default.Backup,
                            onClick = onBackup,
                            trailing = { Chevron() },
                        )
                    },
                    {
                        SettingsRow(
                            title = stringResource(R.string.sync_menu_title),
                            subtitle = stringResource(R.string.sync_menu_desc),
                            icon = Icons.Default.Sync,
                            onClick = onSync,
                            trailing = { Chevron() },
                        )
                    },
                ),
            )
        }

        item { SettingsSectionHeader(stringResource(R.string.about_title)) }
        item {
            SettingsGroup(
                items = listOf(
                    {
                        SettingsRow(
                            title = stringResource(R.string.about_title),
                            subtitle = stringResource(R.string.about_desc),
                            icon = Icons.Default.Info,
                            onClick = onAbout,
                            trailing = { Chevron() },
                        )
                    },
                ),
            )
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.ArrowForwardIos,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
