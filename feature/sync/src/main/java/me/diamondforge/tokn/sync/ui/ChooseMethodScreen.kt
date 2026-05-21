package me.diamondforge.tokn.sync.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.diamondforge.tokn.data.preferences.SyncMethod
import me.diamondforge.tokn.sync.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseMethodScreen(
    isSender: Boolean,
    onBack: () -> Unit,
    onContinue: (SyncMethod) -> Unit,
    viewModel: ChooseMethodViewModel = hiltViewModel(),
) {
    val persisted by viewModel.lastMethod.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<SyncMethod?>(null) }
    LaunchedEffect(persisted) {
        if (selected == null) selected = persisted
    }
    val active = selected ?: persisted

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_choose_method_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider()
                Button(
                    onClick = {
                        viewModel.commit(active)
                        onContinue(active)
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(stringResource(R.string.sync_continue))
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(
                        if (isSender) R.string.sync_choose_method_intro_send
                        else R.string.sync_choose_method_intro_receive,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                MethodOption(
                    icon = Icons.Default.Wifi,
                    title = stringResource(R.string.sync_method_lan_title),
                    description = stringResource(R.string.sync_method_lan_desc),
                    selected = active == SyncMethod.LAN,
                    onClick = { selected = SyncMethod.LAN },
                )
                Spacer(Modifier.height(12.dp))
                MethodOption(
                    icon = Icons.Default.Sync,
                    title = stringResource(R.string.sync_method_wfd_title),
                    description = stringResource(R.string.sync_method_wfd_desc),
                    selected = active == SyncMethod.WFD,
                    onClick = { selected = SyncMethod.WFD },
                )
                Spacer(Modifier.height(12.dp))
                MethodOption(
                    icon = Icons.Default.QrCode2,
                    title = stringResource(R.string.sync_method_qr_title),
                    description = stringResource(R.string.sync_method_qr_desc),
                    selected = active == SyncMethod.QR,
                    onClick = { selected = SyncMethod.QR },
                )
            }
        }
    }
}

@Composable
private fun MethodOption(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        color = container,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.size(12.dp))
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

