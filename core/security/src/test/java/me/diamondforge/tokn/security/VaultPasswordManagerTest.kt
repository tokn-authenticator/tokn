package me.diamondforge.tokn.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

/**
 * Uses a deterministic in-memory [KeystoreManager] subclass to avoid AndroidKeyStore,
 * which Robolectric's stock provider does not support.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultPasswordManagerTest {

    private lateinit var context: Context
    private lateinit var fakeKeystore: FakeKeystoreManager
    private lateinit var manager: VaultPasswordManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        clearPrefs()
        fakeKeystore = FakeKeystoreManager(context)
        manager = VaultPasswordManager(fakeKeystore, context)
    }

    @Test
    fun `hasPassword is false before setup and true after`() {
        assertFalse(manager.hasPassword())
        manager.setup("hunter2")
        assertTrue(manager.hasPassword())
    }

    @Test
    fun `verify accepts the password that was set up`() {
        manager.setup("correct horse battery staple")
        assertTrue(manager.verify("correct horse battery staple"))
    }

    @Test
    fun `verify rejects a wrong password without throwing`() {
        manager.setup("right")
        assertFalse(manager.verify("wrong"))
    }

    @Test
    fun `verify returns false when no password is set`() {
        assertFalse(manager.verify("anything"))
    }

    @Test
    fun `clear removes the password slot`() {
        manager.setup("pw")
        assertTrue(manager.hasPassword())
        manager.clear()
        assertFalse(manager.hasPassword())
        assertFalse(manager.verify("pw"))
    }

    @Test
    fun `re-setup overwrites the prior password`() {
        manager.setup("first")
        manager.setup("second")
        assertFalse(manager.verify("first"))
        assertTrue(manager.verify("second"))
    }

    @Test
    fun `setup nulls the keystore-supplied passphrase after use`() {
        // Mirrors the post-1.4.0 hardening: the plaintext passphrase fetched from
        // KeystoreManager must be zeroed once setup() returns, so it doesn't linger
        // in the heap between vault unlocks.
        manager.setup("pw")
        val handed = fakeKeystore.lastIssued
            ?: error("FakeKeystoreManager never issued a passphrase — was setup called?")
        assertArrayEquals(
            "expected post-setup buffer to be zeroed",
            ByteArray(handed.size),
            handed,
        )
    }

    @Test
    fun `setup produces a different stored payload each time (fresh salt and iv)`() {
        manager.setup("same")
        val first = currentStoredSlot()
        manager.setup("same")
        val second = currentStoredSlot()
        assertNotEquals(first, second)
    }

    private fun clearPrefs() {
        context.getSharedPreferences("vault_password_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun currentStoredSlot(): String =
        context.getSharedPreferences("vault_password_prefs", Context.MODE_PRIVATE)
            .getString("password_slot", null)
            ?: error("password slot missing")

    /**
     * In-memory replacement for [KeystoreManager]. We don't go through AndroidKeyStore,
     * but we keep a stable test passphrase so we can assert that VaultPasswordManager
     * zeroes the buffer after use.
     */
    private class FakeKeystoreManager(context: Context) : KeystoreManager(context) {
        private val fixedPassphrase = ByteArray(32) { (it + 1).toByte() }
        var lastIssued: ByteArray? = null
            private set

        override fun getDatabasePassphrase(): ByteArray {
            // Hand out a fresh copy so the manager can zero it without harming us.
            val copy = fixedPassphrase.copyOf()
            lastIssued = copy
            return copy
        }

        override fun encrypt(data: ByteArray): String =
            "v0:" + Base64.getEncoder().encodeToString(data)

        override fun decrypt(encoded: String): ByteArray {
            require(encoded.startsWith("v0:")) { "unexpected envelope: $encoded" }
            return Base64.getDecoder().decode(encoded.removePrefix("v0:"))
        }
    }
}
