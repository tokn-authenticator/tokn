package me.diamondforge.tokn.data.icon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IconImageUtilTest {

    private lateinit var context: Context
    private lateinit var workDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        workDir = File(context.cacheDir, "icon-util-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun `small image is returned without upscaling`() {
        val src = solidBitmap(64, 64, Color.RED)
        val uri = writePng(src)
        val bytes = IconImageUtil.loadAndResize(context, uri)
        assertNotNull(bytes)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes!!.size)
        assertEquals(64, decoded.width)
        assertEquals(64, decoded.height)
    }

    @Test
    fun `large image is downscaled to fit the 256px bound`() {
        val src = solidBitmap(1024, 1024, Color.BLUE)
        val uri = writePng(src)
        val bytes = IconImageUtil.loadAndResize(context, uri)
        assertNotNull(bytes)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes!!.size)
        assertTrue("expected max dimension <= 256, got ${decoded.width}x${decoded.height}",
            decoded.width <= 256 && decoded.height <= 256)
        assertTrue("expected close to 256 on the long side, got ${decoded.width}x${decoded.height}",
            decoded.width >= 128 && decoded.height >= 128)
    }

    @Test
    fun `non-square downscale preserves aspect ratio within 1 pixel`() {
        val src = solidBitmap(1000, 500, Color.GREEN)
        val uri = writePng(src)
        val bytes = IconImageUtil.loadAndResize(context, uri)!!
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertTrue("width should be <= 256: ${decoded.width}", decoded.width <= 256)
        assertTrue("height should be <= 256: ${decoded.height}", decoded.height <= 256)
        val ratio = decoded.width.toFloat() / decoded.height.toFloat()
        assertTrue("aspect ratio should be ~2.0, was $ratio", ratio > 1.8f && ratio < 2.2f)
    }

    private fun solidBitmap(w: Int, h: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }

    private fun writePng(bmp: Bitmap): Uri {
        val file = File.createTempFile("icon-", ".png", workDir)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }
}
