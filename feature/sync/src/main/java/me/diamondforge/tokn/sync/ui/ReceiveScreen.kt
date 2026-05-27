package me.diamondforge.tokn.sync.ui

import android.Manifest
import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import me.diamondforge.tokn.sync.R
import me.diamondforge.tokn.sync.lan.DiscoveredPeer
import me.diamondforge.tokn.sync.qr.QrSyncScannerPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanReceiveScreen(
    onBack: () -> Unit,
    viewModel: ReceiveViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    var pendingPeer by remember { mutableStateOf<DiscoveredPeer?>(null) }
    var code by rememberSaveable { mutableStateOf("") }

    ReceiveScaffold(
        titleRes = R.string.sync_method_lan_title,
        onBack = { viewModel.reset(); onBack() },
        state = state,
        onDismissError = viewModel::clearError,
        onDismissVersionMismatch = viewModel::clearVersionMismatch,
    ) {
        when (state.status) {
            ReceiveUiState.Status.Connecting,
            ReceiveUiState.Status.Importing -> ConnectingView(state)

            ReceiveUiState.Status.Done -> ImportSuccessView(state) { viewModel.reset() }
            ReceiveUiState.Status.Idle -> {
                if (peers.isEmpty()) {
                    LookingView(stringResource(R.string.sync_looking_for_senders))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(peers, key = { it.name }) { peer ->
                            ListItem(
                                headlineContent = { Text(peer.name) },
                                supportingContent = { Text("${peer.host.hostAddress}:${peer.port}") },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.PhoneAndroid,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.clickable {
                                    pendingPeer = peer
                                    code = ""
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingPeer?.let { peer ->
        CodeDialog(
            title = peer.name,
            code = code,
            onCodeChange = { code = it },
            onConfirm = {
                viewModel.connectAndReceive(peer, code)
                pendingPeer = null
            },
            onDismiss = { pendingPeer = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun WfdReceiveScreen(
    onBack: () -> Unit,
    viewModel: ReceiveViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wfdPeers by viewModel.wfdPeers.collectAsStateWithLifecycle()
    val permissionState = rememberPermissionState(wfdPermissionName())
    var pending by remember { mutableStateOf<WifiP2pDevice?>(null) }
    var code by rememberSaveable { mutableStateOf("") }

    ReceiveScaffold(
        titleRes = R.string.sync_method_wfd_title,
        onBack = { viewModel.reset(); onBack() },
        state = state,
        onDismissError = viewModel::clearError,
        onDismissVersionMismatch = viewModel::clearVersionMismatch,
    ) {
        when {
            !viewModel.wfdSupported -> CenteredText(stringResource(R.string.sync_wfd_unsupported))
            !permissionState.status.isGranted -> PermissionRequestView(
                explanation = stringResource(R.string.sync_wfd_permission),
                button = stringResource(R.string.sync_wfd_grant),
                onGrant = { permissionState.launchPermissionRequest() },
            )

            state.status == ReceiveUiState.Status.Connecting ||
                    state.status == ReceiveUiState.Status.Importing -> ConnectingView(state)

            state.status == ReceiveUiState.Status.Done -> ImportSuccessView(state) { viewModel.reset() }
            else -> {
                LaunchedEffect(Unit) { viewModel.startWfdDiscovery() }
                DisposableEffect(Unit) { onDispose { viewModel.stopWfdDiscovery() } }
                Column(modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)) {
                    Text(
                        stringResource(R.string.sync_wfd_intro),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    if (wfdPeers.isEmpty()) {
                        LookingView(stringResource(R.string.sync_wfd_no_peers))
                    } else {
                        LazyColumn {
                            items(wfdPeers, key = { it.deviceAddress }) { device ->
                                ListItem(
                                    headlineContent = {
                                        Text(device.deviceName.ifBlank { device.deviceAddress })
                                    },
                                    supportingContent = { Text(device.deviceAddress) },
                                    leadingContent = {
                                        Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                                    },
                                    modifier = Modifier.clickable {
                                        pending = device
                                        code = ""
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pending?.let { device ->
        CodeDialog(
            title = device.deviceName.ifBlank { device.deviceAddress },
            code = code,
            onCodeChange = { code = it },
            onConfirm = {
                viewModel.connectViaWfd(device, code)
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun QrReceiveScreen(
    onBack: () -> Unit,
    viewModel: ReceiveViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    ReceiveScaffold(
        titleRes = R.string.sync_method_qr_title,
        onBack = { viewModel.reset(); onBack() },
        state = state,
        onDismissError = viewModel::clearError,
        onDismissVersionMismatch = viewModel::clearVersionMismatch,
    ) {
        when {
            !cameraPermission.status.isGranted -> PermissionRequestView(
                explanation = stringResource(R.string.sync_camera_required),
                button = stringResource(R.string.sync_wfd_grant),
                onGrant = { cameraPermission.launchPermissionRequest() },
            )

            state.status == ReceiveUiState.Status.Done -> ImportSuccessView(state) { viewModel.reset() }
            state.qrComplete -> {
                var passphrase by rememberSaveable { mutableStateOf("") }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                    Text(
                        text = stringResource(R.string.sync_qr_scan_complete),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.sync_qr_passphrase_receiver_hint))
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.sync_qr_passphrase)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.decryptAndImport(passphrase) },
                        enabled = passphrase.isNotEmpty() &&
                                state.status != ReceiveUiState.Status.Importing,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.sync_qr_decrypt)) }
                }
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)) {
                        QrSyncScannerPreview(
                            onRawDetected = viewModel::onQrFrameScanned,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val label = if (state.qrTotal == 0) {
                            stringResource(R.string.sync_qr_aim_hint)
                        } else {
                            stringResource(
                                R.string.sync_qr_scan_progress,
                                state.qrSeen,
                                state.qrTotal
                            )
                        }
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiveScaffold(
    titleRes: Int,
    onBack: () -> Unit,
    state: ReceiveUiState,
    onDismissError: () -> Unit,
    onDismissVersionMismatch: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            state.errorMessage?.let { msg ->
                SyncErrorCard(message = msg, onDismiss = onDismissError)
            }
            Box(modifier = Modifier.weight(1f)) { content() }
        }
        state.versionMismatch?.let { info ->
            SyncVersionMismatchDialog(info = info, onDismiss = onDismissVersionMismatch)
        }
    }
}

@Composable
private fun ConnectingView(state: ReceiveUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        val label = state.activePeer?.name?.let {
            stringResource(R.string.sync_connecting_to, it)
        } ?: stringResource(R.string.sync_transferring)
        Text(label)
    }
}

@Composable
private fun LookingView(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(message)
        }
    }
}

@Composable
private fun PermissionRequestView(
    explanation: String,
    button: String,
    onGrant: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(explanation, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrant) { Text(button) }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text) }
}

@Composable
private fun CodeDialog(
    title: String,
    code: String,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(stringResource(R.string.sync_enter_code))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { onCodeChange(it.filter(Char::isDigit).take(6)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = code.length == 6, onClick = onConfirm) {
                Text(stringResource(R.string.sync_connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sync_back)) }
        },
    )
}

@Composable
private fun ImportSuccessView(state: ReceiveUiState, onDone: () -> Unit) {
    val summary = state.importSummary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (summary != null) {
                stringResource(R.string.sync_done_imported, summary.imported, summary.skipped)
            } else {
                stringResource(R.string.sync_done_sent)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone) { Text(stringResource(R.string.sync_back)) }
    }
}
