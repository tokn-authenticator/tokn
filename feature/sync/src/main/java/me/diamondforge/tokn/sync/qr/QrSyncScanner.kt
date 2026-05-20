package me.diamondforge.tokn.sync.qr

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * Camera preview that forwards every QR text it sees to [onRawDetected].
 * The caller is expected to dedupe by content (e.g. by parsing into
 * [QrChunkCodec.Chunk] and tracking seen seq values).
 */
@Composable
fun QrSyncScannerPreview(
    onRawDetected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember {
        MultiFormatReader().apply {
            setHints(
                mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)),
            )
        }
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val currentCallback by rememberUpdatedState(onRawDetected)

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
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val cameraProvider = future.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { a ->
                        a.setAnalyzer(executor) { imageProxy ->
                            try {
                                val text = decodeQr(reader, imageProxy)
                                if (text != null) {
                                    mainHandler.post { currentCallback(text) }
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
                        analysis,
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier,
    )
}

private fun decodeQr(reader: MultiFormatReader, imageProxy: ImageProxy): String? {
    val yPlane = imageProxy.planes[0]
    val buffer = yPlane.buffer
    val rowStride = yPlane.rowStride
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val source = PlanarYUVLuminanceSource(
        bytes,
        rowStride,
        imageProxy.height,
        0,
        0,
        imageProxy.width,
        imageProxy.height,
        false,
    )
    val binary = BinaryBitmap(HybridBinarizer(source))
    return try {
        reader.decodeWithState(binary).text
    } catch (_: NotFoundException) {
        null
    } catch (_: Throwable) {
        null
    } finally {
        reader.reset()
    }
}
