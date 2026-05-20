package me.diamondforge.tokn.add.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

internal class ZxingQrAnalyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)),
        )
    }

    fun decode(imageProxy: ImageProxy): String? {
        val yPlane = imageProxy.planes[0]
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val bytes = ByteArray(yBuffer.remaining())
        yBuffer.get(bytes)
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
}

internal fun decodeQrFromUri(context: Context, uri: Uri): String? {
    val bitmap = loadBitmap(context, uri) ?: return null
    return try {
        decodeQrFromBitmap(bitmap)
    } finally {
        bitmap.recycle()
    }
}

private fun loadBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }
}.getOrNull()

private fun decodeQrFromBitmap(bitmap: Bitmap): String? {
    val workable = if (bitmap.config == Bitmap.Config.ARGB_8888) {
        bitmap
    } else {
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    }
    val width = workable.width
    val height = workable.height
    val pixels = IntArray(width * height)
    workable.getPixels(pixels, 0, width, 0, 0, width, height)
    if (workable !== bitmap) workable.recycle()
    val source = RGBLuminanceSource(width, height, pixels)
    val binary = BinaryBitmap(HybridBinarizer(source))
    val reader = MultiFormatReader().apply {
        setHints(
            mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)),
        )
    }
    return try {
        reader.decode(binary).text
    } catch (_: NotFoundException) {
        null
    } catch (_: Throwable) {
        null
    }
}
