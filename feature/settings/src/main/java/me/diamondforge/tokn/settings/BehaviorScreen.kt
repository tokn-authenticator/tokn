package me.diamondforge.tokn.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.diamondforge.tokn.domain.model.TapBehavior

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BehaviorScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_behavior)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.tap_to_copy)) },
                    leadingContent = {
                        Icon(Icons.Default.TouchApp, contentDescription = null)
                    },
                    supportingContent = {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf(
                                TapBehavior.NONE to stringResource(R.string.tap_none),
                                TapBehavior.SINGLE to stringResource(R.string.tap_single),
                                TapBehavior.DOUBLE to stringResource(R.string.tap_double),
                            ).forEach { (behavior, label) ->
                                FilterChip(
                                    selected = uiState.tapBehavior == behavior,
                                    onClick = { viewModel.setTapBehavior(behavior) },
                                    label = { Text(label) },
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.show_next_code)) },
                    supportingContent = { Text(stringResource(R.string.show_next_code_desc)) },
                    leadingContent = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = uiState.showNextCodeEnabled,
                            onCheckedChange = { viewModel.setShowNextCodeEnabled(it) },
                        )
                    },
                )
            }
        }
    }
}
