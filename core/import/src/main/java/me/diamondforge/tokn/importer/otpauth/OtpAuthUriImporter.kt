package me.diamondforge.tokn.importer.otpauth

import me.diamondforge.tokn.importer.ExternalImporter
import me.diamondforge.tokn.importer.ImportOutcome
import me.diamondforge.tokn.importer.R
import javax.inject.Inject

/**
 * Plain text file with one otpauth://… URI per line. Comments (lines starting with `#`)
 * and blank lines are skipped. Always unencrypted.
 */
class OtpAuthUriImporter @Inject constructor() : ExternalImporter {
    override val id: String = "otpauth_uri"
    override val displayName: String = "otpauth:// URI list"
    override val noteRes: Int = R.string.importer_otpauth_uri_note
    override val acceptedMimeTypes: Array<String> = arrayOf("text/plain", "*/*")

    override fun canHandle(raw: ByteArray): Boolean {
        val text = raw.toString(Charsets.UTF_8)
        if (text.contains("otpauth-migration://")) return false
        return text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?.startsWith("otpauth://") == true
    }

    override fun parse(raw: ByteArray, password: String?): ImportOutcome {
        val accounts = raw.toString(Charsets.UTF_8).lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull(OtpAuthParser::parse)
            .toList()
        return ImportOutcome.Success(accounts)
    }
}
