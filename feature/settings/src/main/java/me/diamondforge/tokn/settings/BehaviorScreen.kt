package me.diamondforge.tokn.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
                ),
            )
        }
    }
}
