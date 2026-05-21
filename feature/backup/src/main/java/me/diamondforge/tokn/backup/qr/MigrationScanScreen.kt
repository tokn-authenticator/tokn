package me.diamondforge.tokn.backup.qr

import android.Manifest
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import me.diamondforge.tokn.backup.R
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MigrationScanScreen(
    onBack: () -> Unit,
    viewModel: MigrationScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val decoded = decodeQrFromUri(context, uri)
            if (decoded != null) viewModel.onScanned(decoded)
            else viewModel.onScanned("")  // triggers invalid bump in state
        }
    }

    LaunchedEffect(uiState.justAcceptedAt) {
        if (uiState.justAcceptedAt > 0) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(uiState.justDuplicate) {
        if (uiState.justDuplicate > 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    LaunchedEffect(uiState.invalidScan) {
        if (uiState.invalidScan > 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    LaunchedEffect(uiState.result) {
        if (uiState.result != null) viewModel.suppressLock()
    }

    uiState.crossVaultPending?.let {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCrossVault() },
            title = { Text(stringResource(R.string.migration_cross_vault_title)) },
            text = { Text(stringResource(R.string.migration_cross_vault_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmCrossVault() }) {
                    Text(stringResource(R.string.migration_cross_vault_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCrossVault() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    uiState.result?.let { result ->
        AlertDialog(
            onDismissRequest = {
                viewModel.clearResult()
                onBack()
            },
            title = { Text(stringResource(R.string.import_result_title)) },
            text = {
                Text(
                    when {
                        result.found == 0 -> stringResource(R.string.import_result_empty)
                        result.imported == 0 -> stringResource(R.string.import_result_all_duplicates, result.found)
                        result.skipped == 0 -> stringResource(R.string.import_result_all_imported, result.imported)
                        else -> stringResource(R.string.import_result_partial, result.imported, result.skipped)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearResult()
                    onBack()
                }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    if (uiState.errorMalformed) {
        AlertDialog(
            onDismissRequest = { viewModel.clearResult() },
            title = { Text(stringResource(R.string.import_error_title)) },
            text = { Text(stringResource(R.string.error_external_malformed)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearResult() }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.migration_scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.suppressLock()
                        imageLauncher.launch("image/*")
                    }) {
                        Icon(Icons.Default.Image, contentDescription = stringResource(R.string.migration_from_image))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    titleContentColor = Color.White,
                ),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!cameraPermissionState.status.isGranted) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (cameraPermissionState.status.shouldShowRationale)
                            stringResource(R.string.camera_permission_rationale_migration)
                        else
                            stringResource(R.string.camera_permission_required_migration),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text(stringResource(R.string.grant_permission))
                    }
                }
            } else {
                CameraPreview(
                    onDecoded = viewModel::onScanned,
                    modifier = Modifier.fillMaxSize(),
                )
                ScannerOverlay(modifier = Modifier.fillMaxSize())
                ProgressChip(
                    scanned = uiState.scanned,
                    total = uiState.expectedTotal,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 96.dp),
                )
                CommitBar(
                    isComplete = uiState.isComplete,
                    isLoading = uiState.isLoading,
                    enabled = uiState.scanned > 0 && !uiState.isLoading,
                    onClick = viewModel::commit,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    onDecoded: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { MigrationQrAnalyzer() }

    DisposableEffect(lifecycleOwner) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(executor) { imageProxy ->
                            try {
                                val value = analyzer.decode(imageProxy)
                                if (value != null) {
                                    androidx.core.os.HandlerCompat.createAsync(
                                        ctx.mainLooper,
                                    ).post { onDecoded(value) }
                                }
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }
                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier,
    )
}

@Composable
private fun ScannerOverlay(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val overlayColor = Color.Black.copy(alpha = 0.6f)
        val cutoutSize = minOf(size.width, size.height) * 0.7f
        val cutoutLeft = (size.width - cutoutSize) / 2
        val cutoutTop = (size.height - cutoutSize) / 2

        drawRect(overlayColor)
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(cutoutLeft, cutoutTop),
            size = Size(cutoutSize, cutoutSize),
            cornerRadius = CornerRadius(16.dp.toPx()),
            blendMode = BlendMode.Clear,
        )
        drawRoundRect(
            color = primary,
            topLeft = Offset(cutoutLeft, cutoutTop),
            size = Size(cutoutSize, cutoutSize),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun ProgressChip(scanned: Int, total: Int, modifier: Modifier = Modifier) {
    val label = when {
        scanned == 0 -> stringResource(R.string.migration_progress_idle)
        total <= 0 -> stringResource(R.string.migration_progress_started, scanned)
        else -> stringResource(R.string.migration_progress_with_total, scanned, total)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.6f),
    ) {
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CommitBar(
    isComplete: Boolean,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.6f),
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isComplete) {
                Text(
                    stringResource(R.string.migration_all_codes_scanned),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.height(24.dp))
                else Text(stringResource(R.string.migration_import_now))
            }
        }
    }
}
