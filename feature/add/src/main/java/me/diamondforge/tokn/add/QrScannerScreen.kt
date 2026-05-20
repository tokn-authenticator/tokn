package me.diamondforge.tokn.add

import android.Manifest
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import me.diamondforge.tokn.add.qr.ZxingQrAnalyzer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun QrScannerScreen(
    onScanned: (String) -> Unit,
    onManualEntry: () -> Unit = {},
    onBack: () -> Unit,
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            if (cameraPermissionState.status.isGranted) {
                CameraPreview(
                    onScanned = onScanned,
                    modifier = Modifier.fillMaxSize(),
                )
                ScannerOverlay(modifier = Modifier.fillMaxSize())
                TextButton(
                    onClick = onManualEntry,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                ) {
                    Text(
                        text = stringResource(R.string.enter_manually),
                        color = Color.White,
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (cameraPermissionState.status.shouldShowRationale)
                            stringResource(R.string.camera_permission_rationale)
                        else
                            stringResource(R.string.camera_permission_required),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text(stringResource(R.string.grant_permission))
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onManualEntry) {
                        Text(
                            text = stringResource(R.string.enter_manually),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    onScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { ZxingQrAnalyzer() }
    val scanned = remember { AtomicBoolean(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            executor.shutdown()
        }
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
                            if (scanned.get()) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            try {
                                val value = analyzer.decode(imageProxy)
                                if (value != null &&
                                    value.startsWith("otpauth://") &&
                                    scanned.compareAndSet(false, true)
                                ) {
                                    mainHandler.post { onScanned(value) }
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
