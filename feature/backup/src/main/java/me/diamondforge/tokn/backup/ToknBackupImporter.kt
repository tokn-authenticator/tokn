package me.diamondforge.tokn.backup

import me.diamondforge.tokn.importer.ExternalImporter
import me.diamondforge.tokn.importer.ImportOutcome
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.inject.Inject

/**
 * Handles Tokn's own backup format inside the same picker that lists external sources.
 * Both plain JSON and AES-GCM-encrypted variants are detected by [classifyToknBackup];
 * encrypted payloads surface as [ImportOutcome.NeedsPassword] so the screen reuses its
 * existing password dialog without special casing Tokn.
 */
class ToknBackupImporter @Inject constructor(
    private val encryptedBackupManager: EncryptedBackupManager,
) : ExternalImporter {

    override val id: String = "tokn"
    override val displayName: String = "Tokn vault"
    override val noteRes: Int = R.string.import_picker_tokn_note
    override val pickerOrder: Int = 0
    override val acceptedMimeTypes: Array<String> =
        arrayOf("application/octet-stream", "application/json", "*/*")

    override fun canHandle(raw: ByteArray): Boolean = classifyToknBackup(raw) != null

    override fun parse(raw: ByteArray, password: String?): ImportOutcome =
        when (classifyToknBackup(raw)) {
            ToknBackupShape.Plain -> parsePlain(raw)
            ToknBackupShape.Encrypted -> parseEncrypted(raw, password)
            null -> ImportOutcome.Unsupported
        }

    private fun parsePlain(raw: ByteArray): ImportOutcome = runCatching {
        ImportOutcome.Success(deserializeAccountsFromJson(raw.toString(Charsets.UTF_8)))
    }.getOrElse { ImportOutcome.Malformed(it) }

    private fun parseEncrypted(raw: ByteArray, password: String?): ImportOutcome {
        if (password.isNullOrEmpty()) return ImportOutcome.NeedsPassword
        val decrypted = runCatching { encryptedBackupManager.decryptBytes(raw, password) }
            .getOrElse { e ->
                return if (e is AEADBadTagException || e is BadPaddingException)
                    ImportOutcome.WrongPassword(e)
                else ImportOutcome.Malformed(e)
            }
        return runCatching { ImportOutcome.Success(deserializeAccountsFromJson(decrypted)) }
            .getOrElse { ImportOutcome.Malformed(it) }
    }
}
