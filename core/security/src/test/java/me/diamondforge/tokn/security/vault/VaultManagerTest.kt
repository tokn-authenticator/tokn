package me.diamondforge.tokn.security.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import me.diamondforge.tokn.security.KeystoreManager
import me.diamondforge.tokn.security.VaultPasswordManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultManagerTest {

    private lateinit var context: Context
    private lateinit var fakeKeystore: FakeKeystoreManager
    private lateinit var session: VaultSession
    private lateinit var legacyPassword: VaultPasswordManager
    private lateinit var manager: VaultManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        clearPrefs()
        fakeKeystore = FakeKeystoreManager(context)
        session = VaultSession()
        legacyPassword = VaultPasswordManager(fakeKeystore, context)
        manager = VaultManager(context, fakeKeystore, session, legacyPassword)
    }

    @Test
    fun `migration without password creates a no-auth slot and auto-unlocks to the same key`() {
        val masterKey = fixedKey(7)
        fakeKeystore.legacyPassphrase = masterKey.copyOf()

        manager.initIfNeeded()

        assertFalse(manager.requiresUnlock())
        assertTrue(manager.tryAutoUnlock())
        assertArrayEquals(masterKey, session.requireKey())
    }

    @Test
    fun `fresh install generates a master key and can auto-unlock`() {
        fakeKeystore.legacyPassphrase = null

        manager.initIfNeeded()

        assertFalse(manager.requiresUnlock())
        assertTrue(manager.tryAutoUnlock())
        // 32-byte AES key present
        assertTrue(session.requireKey().size == 32)
    }

    @Test
    fun `setting a password removes the no-auth slot but keeps the same master key`() {
        val masterKey = fixedKey(11)
        fakeKeystore.legacyPassphrase = masterKey.copyOf()
        manager.initIfNeeded()
        manager.tryAutoUnlock()

        manager.setPassword("hunter2")

        assertTrue(manager.hasPassword())
        assertTrue(manager.requiresUnlock())

        // Re-unlock from a cold session via the Argon2 password slot.
        session.lock()
        assertFalse(manager.unlockWithPassword("wrong"))
        assertTrue(manager.unlockWithPassword("hunter2"))
        assertArrayEquals(masterKey, session.requireKey())
    }

    @Test
    fun `removing the password restores transparent auto-unlock with the same key`() {
        val masterKey = fixedKey(13)
        fakeKeystore.legacyPassphrase = masterKey.copyOf()
        manager.initIfNeeded()
        manager.tryAutoUnlock()
        manager.setPassword("pw")

        manager.removePassword()

        assertFalse(manager.hasPassword())
        assertFalse(manager.requiresUnlock())
        session.lock()
        assertTrue(manager.tryAutoUnlock())
        assertArrayEquals(masterKey, session.requireKey())
    }

    @Test
    fun `legacy PBKDF2 password is lazily upgraded to an Argon2 slot without losing data`() {
        val masterKey = fixedKey(23)
        fakeKeystore.legacyPassphrase = masterKey.copyOf()
        // Simulate the pre-slot world: an old PBKDF2 vault password exists.
        legacyPassword.setup("oldpw")

        manager.initIfNeeded()

        // Behaves like the old overlay until the user types the password.
        assertTrue(manager.requiresUnlock())
        assertTrue(manager.legacyFlagForTest())

        assertFalse(manager.unlockWithPassword("nope"))
        assertTrue(manager.unlockWithPassword("oldpw"))
        assertArrayEquals(masterKey, session.requireKey())

        // After the upgrade: a real Argon2 slot, no no-auth slot, legacy cleared.
        assertTrue(manager.hasPassword())
        assertFalse(manager.legacyFlagForTest())
        assertFalse(legacyPassword.hasPassword())

        // Subsequent cold unlocks go through the Argon2 slot.
        session.lock()
        assertTrue(manager.unlockWithPassword("oldpw"))
        assertArrayEquals(masterKey, session.requireKey())
    }

    @Test
    fun `needsRootOfTrustUpgrade is true after legacy migration and false once password confirmed`() {
        fakeKeystore.legacyPassphrase = fixedKey(41)
        legacyPassword.setup("pw")
        manager.initIfNeeded()

        assertTrue(manager.needsRootOfTrustUpgrade())

        assertTrue(manager.unlockWithPassword("pw"))
        assertFalse(manager.needsRootOfTrustUpgrade())
    }

    @Test
    fun `needsRootOfTrustUpgrade is false for password-only and no-password users`() {
        fakeKeystore.legacyPassphrase = fixedKey(43)
        manager.initIfNeeded() // no-password migration: no-auth slot, but no password
        assertFalse(manager.needsRootOfTrustUpgrade())

        manager.tryAutoUnlock()
        manager.setPassword("pw") // proper Argon2 password, no-auth slot removed
        assertFalse(manager.needsRootOfTrustUpgrade())
    }

    @Test
    fun `initIfNeeded is idempotent`() {
        fakeKeystore.legacyPassphrase = fixedKey(31)
        manager.initIfNeeded()
        manager.tryAutoUnlock()
        val key = session.requireKey()

        manager.initIfNeeded() // must not regenerate or overwrite

        session.lock()
        manager.tryAutoUnlock()
        assertArrayEquals(key, session.requireKey())
    }

    private fun clearPrefs() {
        listOf("vault_slots_prefs", "vault_password_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun fixedKey(seed: Int) = ByteArray(32) { (it + seed).toByte() }

    private class FakeKeystoreManager(context: Context) : KeystoreManager(context) {
        var legacyPassphrase: ByteArray? = null

        override fun getDatabasePassphrase(): ByteArray =
            legacyPassphrase?.copyOf() ?: ByteArray(32) { 1 }.also {
                legacyPassphrase = it.copyOf()
            }

        override fun readLegacyPassphraseOrNull(): ByteArray? = legacyPassphrase?.copyOf()

        override fun clearLegacyPassphrase() {
            legacyPassphrase = null
        }

        // No biometric slot in these tests; keep off the real AndroidKeyStore.
        override fun hasBiometricKey(): Boolean = false
        override fun deleteBiometricKey() = Unit

        override fun encrypt(data: ByteArray): String =
            "kc:" + Base64.getEncoder().encodeToString(data)

        override fun decrypt(encoded: String): ByteArray {
            require(encoded.startsWith("kc:")) { "unexpected envelope: $encoded" }
            return Base64.getDecoder().decode(encoded.removePrefix("kc:"))
        }
    }
}

private fun VaultManager.legacyFlagForTest(): Boolean = requiresUnlock() && !hasPassword()
