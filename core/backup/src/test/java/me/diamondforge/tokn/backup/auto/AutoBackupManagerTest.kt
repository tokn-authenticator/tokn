package me.diamondforge.tokn.backup.auto

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import me.diamondforge.tokn.backup.EncryptedBackupManager
import me.diamondforge.tokn.backup.serializeAccountsToJson
import me.diamondforge.tokn.data.preferences.FakePreferencesDataStore
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.security.EncryptionManager
import me.diamondforge.tokn.security.KeystoreManager
import me.diamondforge.tokn.security.vault.VaultSession
import javax.inject.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoBackupManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val treeUri = "content://tree/backups"
    private val accounts = listOf(
        OtpAccount(issuer = "GitHub", accountName = "alice", secret = "JBSWY3DPEHPK3PXP"),
    )

    private fun prefs() = AutoBackupPreferencesRepository(FakePreferencesDataStore())
    private fun unlockedSession() = VaultSession().apply { unlock(ByteArray(32)) }

    private class RecordingWriter(context: Context) : AutoBackupWriter(context) {
        var calls = 0
        var lastBytes: ByteArray? = null
        var lastEncrypted: Boolean? = null
        override fun write(
            uri: Uri,
            bytes: ByteArray,
            encrypted: Boolean,
            versionsToKeep: Int,
            timestamp: Long,
        ) {
            calls++
            lastBytes = bytes
            lastEncrypted = encrypted
        }
    }

    private class FakeKeystore(context: Context) : KeystoreManager(context) {
        override fun encrypt(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)
        override fun decrypt(encoded: String): ByteArray = Base64.decode(encoded, Base64.NO_WRAP)
    }

    private fun manager(
        prefs: AutoBackupPreferencesRepository,
        writer: AutoBackupWriter = RecordingWriter(context),
        session: VaultSession = unlockedSession(),
    ) = AutoBackupManager(
        prefs = prefs,
        writer = writer,
        getAccounts = Provider { GetAccountsUseCase(FakeAccountRepository(accounts)) },
        encryptedBackupManager = EncryptedBackupManager(EncryptionManager()),
        keystoreManager = FakeKeystore(context),
        session = session,
    )

    @Test
    fun `disabled is skipped`() = runTest {
        val writer = RecordingWriter(context)
        val result = manager(prefs(), writer).backupNow()
        assertEquals(AutoBackupResult.Skipped("disabled"), result)
        assertEquals(0, writer.calls)
    }

    @Test
    fun `enabled without location is skipped`() = runTest {
        val prefs = prefs().apply { setEnabled(true) }
        val result = manager(prefs).backupNow()
        assertEquals(AutoBackupResult.Skipped("no_location"), result)
    }

    @Test
    fun `locked vault is skipped`() = runTest {
        val prefs = prefs().apply { setEnabled(true); setLocation(treeUri); setEncrypt(false) }
        val result = manager(prefs, session = VaultSession()).backupNow()
        assertEquals(AutoBackupResult.Skipped("locked"), result)
    }

    @Test
    fun `plaintext backup writes the serialized json`() = runTest {
        val prefs = prefs().apply { setEnabled(true); setLocation(treeUri); setEncrypt(false) }
        val writer = RecordingWriter(context)
        val result = manager(prefs, writer).backupNow()

        assertEquals(AutoBackupResult.Success, result)
        assertEquals(1, writer.calls)
        assertFalse(writer.lastEncrypted!!)
        assertEquals(serializeAccountsToJson(accounts), writer.lastBytes!!.toString(Charsets.UTF_8))
    }

    @Test
    fun `unchanged content is deduped`() = runTest {
        val prefs = prefs().apply { setEnabled(true); setLocation(treeUri); setEncrypt(false) }
        val writer = RecordingWriter(context)
        val manager = manager(prefs, writer)

        assertEquals(AutoBackupResult.Success, manager.backupNow())
        assertEquals(AutoBackupResult.Skipped("unchanged"), manager.backupNow())
        assertEquals(1, writer.calls)
    }

    @Test
    fun `force overrides the dedup`() = runTest {
        val prefs = prefs().apply { setEnabled(true); setLocation(treeUri); setEncrypt(false) }
        val writer = RecordingWriter(context)
        val manager = manager(prefs, writer)

        manager.backupNow()
        assertEquals(AutoBackupResult.Success, manager.backupNow(force = true))
        assertEquals(2, writer.calls)
    }

    @Test
    fun `encrypted backup is decryptable with the stored password`() = runTest {
        val prefs = prefs().apply { setEnabled(true); setLocation(treeUri); setEncrypt(true) }
        val writer = RecordingWriter(context)
        val manager = manager(prefs, writer)
        manager.setPassword("swordfish")

        val result = manager.backupNow()

        assertEquals(AutoBackupResult.Success, result)
        assertTrue(writer.lastEncrypted!!)
        val decrypted = EncryptedBackupManager(EncryptionManager())
            .decryptBytes(writer.lastBytes!!, "swordfish")
        assertEquals(serializeAccountsToJson(accounts), decrypted)
    }

    @Test
    fun `encrypt enabled without a password fails`() = runTest {
        val prefs = prefs().apply { setEnabled(true); setLocation(treeUri); setEncrypt(true) }
        val writer = RecordingWriter(context)
        val result = manager(prefs, writer).backupNow()

        assertEquals(AutoBackupResult.Failure("no_password"), result)
        assertEquals(0, writer.calls)
    }
}
