package me.diamondforge.tokn.sync.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import me.diamondforge.tokn.sync.R
import me.diamondforge.tokn.sync.qr.QrRenderer

internal fun wfdPermissionName(): String = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.NEARBY_WIFI_DEVICES
    else -> Manifest.permission.ACCESS_FINE_LOCATION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanSendScreen(
    onBack: () -> Unit,
    viewModel: SendViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SendScaffold(
        titleRes = R.string.sync_method_lan_title,
        onBack = {
            viewModel.cancelLan()
            onBack()
        },
        state = state,
        onDismissError = viewModel::clearError,
        onDismissVersionMismatch = viewModel::clearVersionMismatch,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.status) {
                SendUiState.Status.Idle -> {
                    Text(
                        text = stringResource(R.string.sync_lan_intro),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = viewModel::startLanSend, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sync_start))
                    }
                }

                SendUiState.Status.Waiting,
                SendUiState.Status.Transferring -> PairingCodeView(
                    state,
                    onCancel = viewModel::cancelLan
                )

                SendUiState.Status.Done -> DoneView(onBack = viewModel::cancelLan)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun WfdSendScreen(
    onBack: () -> Unit,
    viewModel: SendViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionState = rememberPermissionState(wfdPermissionName())

    SendScaffold(
        titleRes = R.string.sync_method_wfd_title,
        onBack = {
            viewModel.cancelWfd()
            onBack()
        },
        state = state,
        onDismissError = viewModel::clearError,
        onDismissVersionMismatch = viewModel::clearVersionMismatch,
    ) {
        if (!viewModel.wfdSupported) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text(stringResource(R.string.sync_wfd_unsupported)) }
            return@SendScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.status) {
                SendUiState.Status.Idle -> {
                    Text(
                        stringResource(R.string.sync_wfd_intro),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(24.dp))
                    if (!permissionState.status.isGranted) {
                        Text(
                            stringResource(R.string.sync_wfd_permission),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { permissionState.launchPermissionRequest() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.sync_wfd_grant)) }
                    } else {
                        Button(
                            onClick = viewModel::startWfdSend,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.sync_wfd_start))
                        }
                    }
                }

                SendUiState.Status.Waiting,
                SendUiState.Status.Transferring -> PairingCodeView(
                    state,
                    onCancel = viewModel::cancelWfd
                )

                SendUiState.Status.Done -> DoneView(onBack = viewModel::cancelWfd)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrSendScreen(
    onBack: () -> Unit,
    viewModel: SendViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var passphrase by rememberSaveable { mutableStateOf("") }

    SendScaffold(
        titleRes = R.string.sync_method_qr_title,
        onBack = {
            viewModel.resetQr()
            onBack()
        },
        state = state,
        onDismissError = viewModel::clearError,
        onDismissVersionMismatch = viewModel::clearVersionMismatch,
    ) {
        if (state.qrFrames.isEmpty()) {
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)) {
                Text(
                    stringResource(R.string.sync_qr_passphrase_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
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
                    onClick = { viewModel.prepareQr(passphrase) },
                    enabled = passphrase.length >= 8 && state.status != SendUiState.Status.Transferring,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.status == SendUiState.Status.Transferring) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text(stringResource(R.string.sync_qr_show))
                    }
                }
            }
        } else {
            AnimatedQr(
                state = state,
                onAdvanceFrame = viewModel::advanceQrFrame,
                onReset = viewModel::resetQr,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendScaffold(
    titleRes: Int,
    onBack: () -> Unit,
    state: SendUiState,
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
private fun PairingCodeView(state: SendUiState, onCancel: () -> Unit) {
    if (state.deviceName.isNotBlank()) {
        Text(
            text = stringResource(R.string.sync_this_device, state.deviceName),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
    Text(stringResource(R.string.sync_pairing_code), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    Text(
        text = state.pairingCode,
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.sync_pairing_code_hint),
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(32.dp))
    CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text(
        if (state.status == SendUiState.Status.Waiting) stringResource(R.string.sync_waiting_for_peer)
        else stringResource(R.string.sync_transferring),
    )
    Spacer(Modifier.height(24.dp))
    TextButton(onClick = onCancel) {
        Text(stringResource(R.string.sync_back))
    }
}

@Composable
private fun DoneView(onBack: () -> Unit) {
    Text(stringResource(R.string.sync_done_sent), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onBack) { Text(stringResource(R.string.sync_back)) }
}

@Composable
private fun AnimatedQr(
    state: SendUiState,
    onAdvanceFrame: () -> Unit,
    onReset: () -> Unit,
) {
    val frames = state.qrFrames
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val sizePx = remember(containerSize) {
        (minOf(containerSize.width, containerSize.height) * 0.85f)
            .toInt().coerceAtLeast(320)
    }
    val current = state.currentFrame.coerceIn(0, frames.lastIndex)
    val bitmap = remember(current, frames) { QrRenderer.render(frames[current], sizePx) }

    LaunchedEffect(frames) {
        if (frames.size > 1) {
            while (true) {
                delay(450)
                onAdvanceFrame()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(with(density) { sizePx.toDp() })
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) { Image(bitmap = bitmap.asImageBitmap(), contentDescription = null) }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.sync_qr_frame_progress, current + 1, frames.size),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onReset) { Text(stringResource(R.string.sync_back)) }
    }
}
