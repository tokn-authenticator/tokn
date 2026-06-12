package me.diamondforge.tokn.settings

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.diamondforge.tokn.data.preferences.ThemeMode

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
                .substringBefore(",").trim()
        )
    }
    var showLangDialog by remember { mutableStateOf(false) }
    var showIconPrivacyDialog by remember { mutableStateOf(false) }

    val systemDefaultLabel = stringResource(R.string.language_system_default)
    val languages = remember(systemDefaultLabel) {
        val translated = listOf(
            "en" to "English",
            "de" to "Deutsch",
            "ru" to "Русский",
            "zh-CN" to "简体中文",
        ).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second })
        listOf("" to systemDefaultLabel) + translated
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

    SettingsScaffold(
        title = stringResource(R.string.appearance),
        onBack = onBack,
    ) {
        item { SettingsSectionHeader(stringResource(R.string.settings_section_general)) }
        item {
            val generalItems = buildList<@Composable () -> Unit> {
                add {
                    SettingsToggleRow(
                        title = stringResource(R.string.theme),
                        icon = Icons.Default.Palette,
                        options = ThemeMode.entries.map { mode ->
                            mode to when (mode) {
                                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                            }
                        },
                        selected = uiState.themeMode,
                        onSelect = { viewModel.setThemeMode(it) },
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add {
                        SettingsRow(
                            title = stringResource(R.string.material_you),
                            subtitle = stringResource(R.string.material_you_desc),
                            icon = Icons.Default.ColorLens,
                            trailing = {
                                SettingsSwitch(
                                    checked = uiState.dynamicColorEnabled,
                                    onCheckedChange = { viewModel.setDynamicColorEnabled(it) },
                                )
                            },
                        )
                    }
                }
                add {
                    SettingsRow(
                        title = stringResource(R.string.language),
                        subtitle = currentLangLabel,
                        icon = Icons.Default.Language,
                        onClick = { showLangDialog = true },
                    )
                }
            }
            SettingsGroup(items = generalItems)
        }

        item { SettingsSectionHeader(stringResource(R.string.settings_section_icons)) }
        item {
            SettingsGroup(
                items = listOf(
                    {
                        SettingsRow(
                            title = stringResource(R.string.account_icons),
                            subtitle = stringResource(R.string.account_icons_desc),
                            icon = Icons.Default.Image,
                            trailing = {
                                SettingsSwitch(
                                    checked = uiState.iconFetchEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled) showIconPrivacyDialog = true
                                        else viewModel.setIconFetchEnabled(false)
                                    },
                                )
                            },
                        )
                    },
                    {
                        SettingsRow(
                            title = stringResource(R.string.icon_packs_section),
                            subtitle = stringResource(R.string.icon_packs_section_desc),
                            icon = Icons.Default.Image,
                            onClick = onIconPacks,
                        )
                    },
                ),
            )
        }
    }
}
