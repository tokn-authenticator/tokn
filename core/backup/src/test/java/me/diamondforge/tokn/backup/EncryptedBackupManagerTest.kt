package me.diamondforge.tokn.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import me.diamondforge.tokn.security.EncryptionManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.AEADBadTagException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EncryptedBackupManagerTest {

    private lateinit var context: Context
    private lateinit var tmpDir: File
    private val manager = EncryptedBackupManager(EncryptionManager())

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tmpDir = File(context.cacheDir, "backup-test").apply { mkdirs() }
    }

    private fun tmpUri(name: String): Uri = Uri.fromFile(File(tmpDir, name))

    @Test
    fun `export then import yields original JSON`() {
        val plain = """{"accounts":[{"secret":"AAAA"}],"version":1}"""
        val uri = tmpUri("ok.bak")
        manager.exportToUri(context, uri, plain, password = "swordfish")
        val restored = manager.importFromUri(context, uri, password = "swordfish")
        assertEquals(plain, restored)
    }

    @Test
    fun `exported file is a wrapper JSON with ciphertext iv salt`() {
        val uri = tmpUri("inspect.bak")
        manager.exportToUri(context, uri, "secret-data", password = "pw")
        val raw = File(uri.path!!).readText()
        val wrapper = JSONObject(raw)
        assertTrue(wrapper.has("ciphertext"))
        assertTrue(wrapper.has("iv"))
        assertTrue(wrapper.has("salt"))
    }

    @Test
    fun `wrong password fails import`() {
        val uri = tmpUri("wrong.bak")
        manager.exportToUri(context, uri, "secret-data", password = "right")
        assertThrows(AEADBadTagException::class.java) {
            manager.importFromUri(context, uri, password = "wrong")
        }
    }

    @Test
    fun `corrupt wrapper JSON throws`() {
        val uri = tmpUri("corrupt.bak")
        File(uri.path!!).writeText("{not json")
        assertThrows(Exception::class.java) {
            manager.importFromUri(context, uri, password = "any")
        }
    }

    @Test
    fun `wrapper missing required field throws`() {
        val uri = tmpUri("missing.bak")
        File(uri.path!!).writeText("""{"ciphertext":"x","iv":"y"}""") // no salt
        assertThrows(Exception::class.java) {
            manager.importFromUri(context, uri, password = "pw")
        }
    }

    @Test
    fun `two exports of same data produce different ciphertexts`() {
        val a = tmpUri("a.bak").also { manager.exportToUri(context, it, "same", "pw") }
        val b = tmpUri("b.bak").also { manager.exportToUri(context, it, "same", "pw") }
        val ja = JSONObject(File(a.path!!).readText())
        val jb = JSONObject(File(b.path!!).readText())
        // GCM with random IV + PBKDF2 with random salt => both ciphertext and salt differ.
        assertTrue(ja.getString("ciphertext") != jb.getString("ciphertext"))
        assertTrue(ja.getString("salt") != jb.getString("salt"))
    }
}
