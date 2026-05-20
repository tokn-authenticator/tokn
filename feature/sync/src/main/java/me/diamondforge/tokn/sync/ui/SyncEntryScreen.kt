package me.diamondforge.tokn.sync.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.diamondforge.tokn.sync.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncEntryScreen(
    onBack: () -> Unit,
    onSend: () -> Unit,
    onReceive: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = stringResource(R.string.sync_desc),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.sync_send)) },
                        supportingContent = { Text(stringResource(R.string.sync_send_intro)) },
                        leadingContent = { Icon(Icons.Default.Wifi, contentDescription = null) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null)
                        },
                        modifier = Modifier.clickable(onClick = onSend),
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.sync_receive)) },
                        supportingContent = { Text(stringResource(R.string.sync_receive_intro)) },
                        leadingContent = { Icon(Icons.Default.QrCode2, contentDescription = null) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null)
                        },
                        modifier = Modifier.clickable(onClick = onReceive),
                    )
                }
            }
        }
    }
}
