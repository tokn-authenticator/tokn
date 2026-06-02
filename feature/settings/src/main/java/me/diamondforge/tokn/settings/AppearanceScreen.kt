package me.diamondforge.tokn.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.os.Build
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.diamondforge.tokn.data.preferences.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    onIconPacks: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var currentLangTag by remember {
        mutableStateOf(
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
                .substringBefore(",").substringBefore("-").trim()
        )
    }
    var showLangDialog by remember { mutableStateOf(false) }
    var showIconPrivacyDialog by remember { mutableStateOf(false) }

    val systemDefaultLabel = stringResource(R.string.language_system_default)
    val languages = remember(systemDefaultLabel) {
        listOf(
            "" to systemDefaultLabel,
            "en" to "English",
            "de" to "Deutsch",
        )
    }
    val currentLangLabel =
        languages.firstOrNull { it.first == currentLangTag }?.second ?: systemDefaultLabel

    if (showLangDialog) {
        AlertDialog(
            onDismissRequest = { showLangDialog = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    languages.forEach { (tag, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showLangDialog = false
                                    currentLangTag = tag
                                    val locales = if (tag.isEmpty())
                                        LocaleListCompat.getEmptyLocaleList()
                                    else
                                        LocaleListCompat.forLanguageTags(tag)
                                    AppCompatDelegate.setApplicationLocales(locales)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = currentLangTag == tag, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLangDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showIconPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showIconPrivacyDialog = false },
            title = { Text(stringResource(R.string.account_icons_privacy_title)) },
            text = { Text(stringResource(R.string.account_icons_privacy_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showIconPrivacyDialog = false
                    viewModel.setIconFetchEnabled(true)
                }) {
                    Text(stringResource(R.string.enable_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { showIconPrivacyDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance)) },
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
                    headlineContent = { Text(stringResource(R.string.theme)) },
                    leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                    supportingContent = {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = uiState.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = {
                                        Text(
                                            when (mode) {
                                                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                            },
                                        )
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                    },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.material_you)) },
                        supportingContent = { Text(stringResource(R.string.material_you_desc)) },
                        leadingContent = {
                            Icon(Icons.Default.ColorLens, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.dynamicColorEnabled,
                                onCheckedChange = { viewModel.setDynamicColorEnabled(it) },
                            )
                        },
                    )
                }
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.language)) },
                    supportingContent = { Text(currentLangLabel) },
                    leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                    modifier = Modifier.clickable { showLangDialog = true },
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.account_icons)) },
                    supportingContent = { Text(stringResource(R.string.account_icons_desc)) },
                    leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = uiState.iconFetchEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) showIconPrivacyDialog = true
                                else viewModel.setIconFetchEnabled(false)
                            },
                        )
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.icon_packs_section)) },
                    supportingContent = { Text(stringResource(R.string.icon_packs_section_desc)) },
                    leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                    modifier = Modifier.clickable { onIconPacks() },
                )
            }
        }
    }
}
