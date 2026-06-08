package me.diamondforge.tokn.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onBehavior: () -> Unit,
    onSecurity: () -> Unit,
    onGroups: () -> Unit,
    onBackup: () -> Unit,
    onSync: () -> Unit,
    onAbout: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                    headlineContent = { Text(stringResource(R.string.appearance)) },
                    supportingContent = { Text(stringResource(R.string.appearance_desc)) },
                    leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onAppearance),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_behavior)) },
                    supportingContent = { Text(stringResource(R.string.app_behavior_desc)) },
                    leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onBehavior),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.security)) },
                    supportingContent = { Text(stringResource(R.string.security_desc)) },
                    leadingContent = { Icon(Icons.Default.Security, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onSecurity),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_groups_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_groups_desc)) },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onGroups),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.backup_restore)) },
                    supportingContent = { Text(stringResource(R.string.backup_desc)) },
                    leadingContent = { Icon(Icons.Default.Backup, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onBackup),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.sync_menu_title)) },
                    supportingContent = { Text(stringResource(R.string.sync_menu_desc)) },
                    leadingContent = { Icon(Icons.Default.Sync, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onSync),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_title)) },
                    supportingContent = { Text(stringResource(R.string.about_desc)) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onAbout),
                )
            }
        }
    }
}
