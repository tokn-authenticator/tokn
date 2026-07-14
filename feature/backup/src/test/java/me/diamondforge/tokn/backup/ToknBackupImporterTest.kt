package me.diamondforge.tokn.backup

import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.importer.ImportOutcome
import me.diamondforge.tokn.security.EncryptionManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ToknBackupImporterTest {

    private lateinit var encryption: EncryptionManager
    private lateinit var importer: ToknBackupImporter

    @Before
    fun setUp() {
        encryption = EncryptionManager()
        importer = ToknBackupImporter(EncryptedBackupManager(encryption))
    }

    private val accounts = listOf(
        OtpAccount(issuer = "ACME", accountName = "alice", secret = "JBSWY3DPEHPK3PXP"),
    )

    private fun plainBytes() = serializeAccountsToJson(accounts).toByteArray()

    private fun encryptedBytes(password: String): ByteArray {
        val payload = encryption.encrypt(serializeAccountsToJson(accounts).toByteArray(), password)
        return JSONObject().apply { payload.writeTo(this) }.toString().toByteArray()
    }

    @Test
    fun `canHandle accepts plain and encrypted vaults and rejects others`() {
        assertTrue(importer.canHandle(plainBytes()))
        assertTrue(importer.canHandle(encryptedBytes("pw")))
        assertFalse(importer.canHandle("{\"foo\":1}".toByteArray()))
        assertFalse(importer.canHandle("not json".toByteArray()))
    }

    @Test
    fun `a plain vault parses straight to accounts`() {
        val outcome = importer.parse(plainBytes(), password = null)
        assertTrue(outcome is ImportOutcome.Success)
        assertEquals("alice", (outcome as ImportOutcome.Success).accounts.single().accountName)
    }

    @Test
    fun `an encrypted vault without a password asks for one`() {
        assertEquals(
            ImportOutcome.NeedsPassword,
            importer.parse(encryptedBytes("pw"), password = null)
        )
    }

    @Test
    fun `an encrypted vault decrypts with the right password`() {
        val outcome = importer.parse(encryptedBytes("pw"), password = "pw")
        assertTrue(outcome is ImportOutcome.Success)
        assertEquals(
            "JBSWY3DPEHPK3PXP",
            (outcome as ImportOutcome.Success).accounts.single().secret
        )
    }

    @Test
    fun `a wrong password reports WrongPassword`() {
        val outcome = importer.parse(encryptedBytes("pw"), password = "nope")
        assertTrue(outcome is ImportOutcome.WrongPassword)
    }

    @Test
    fun `a plain vault with a broken accounts field is malformed`() {
        val raw = "{\"accounts\":\"oops\",\"version\":1}".toByteArray()
        assertTrue(importer.parse(raw, password = null) is ImportOutcome.Malformed)
    }

    @Test
    fun `json that is neither shape is unsupported`() {
        assertEquals(ImportOutcome.Unsupported, importer.parse("{\"foo\":1}".toByteArray(), null))
    }
}
