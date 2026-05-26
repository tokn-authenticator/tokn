package me.diamondforge.tokn.data.icon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

object IconImageUtil {
    private const val MAX_DIMENSION = 256
    private const val PNG_QUALITY = 100

    fun loadAndResize(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null

        val (srcW, srcH) = bounds.outWidth to bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        val sampleSize = computeSampleSize(srcW, srcH, MAX_DIMENSION)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        val scaled = scaleToMax(decoded, MAX_DIMENSION)
        if (scaled !== decoded) decoded.recycle()

        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
            scaled.recycle()
            out.toByteArray()
        }
    }

    private fun computeSampleSize(width: Int, height: Int, target: Int): Int {
        var sampleSize = 1
        var w = width
        var h = height
        while (w / 2 >= target && h / 2 >= target) {
            w /= 2; h /= 2; sampleSize *= 2
        }
        return sampleSize
    }

    private fun scaleToMax(src: Bitmap, max: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= max && h <= max) return src
        val ratio = minOf(max.toFloat() / w, max.toFloat() / h)
        return Bitmap.createScaledBitmap(src, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }
}
