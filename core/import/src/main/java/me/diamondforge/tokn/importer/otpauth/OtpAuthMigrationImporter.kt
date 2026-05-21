package me.diamondforge.tokn.importer.otpauth

import android.net.Uri
import android.util.Base64
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.importer.ExternalImporter
import me.diamondforge.tokn.importer.ImportOutcome
import me.diamondforge.tokn.importer.R
import javax.inject.Inject

/**
 * Google Authenticator's "Export accounts" feature produces one or more
 * `otpauth-migration://offline?data=<base64 protobuf>` URIs. A multi-URI export can be
 * dropped into a text file (one URI per line) and imported in one shot.
 *
 * The protobuf schema (`MigrationPayload`) is documented in Google Authenticator's
 * source; we only implement the fields we need.
 */
class OtpAuthMigrationImporter @Inject constructor() : ExternalImporter {
    override val id: String = "otpauth_migration"
    override val displayName: String = "Google Authenticator export"
    override val noteRes: Int = 0
    override val acceptedMimeTypes: Array<String> = arrayOf("text/plain", "*/*")

    override fun canHandle(raw: ByteArray): Boolean {
        val text = raw.toString(Charsets.UTF_8)
        return text.contains("otpauth-migration://")
    }

    override fun parse(raw: ByteArray, password: String?): ImportOutcome {
        val accounts = ArrayList<OtpAccount>()
        val text = raw.toString(Charsets.UTF_8)
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("otpauth-migration://", ignoreCase = true) }
            .toList()
        if (lines.isEmpty()) return ImportOutcome.Malformed()

        for (line in lines) {
            val data = runCatching { Uri.parse(line).getQueryParameter("data") }.getOrNull()
                ?: return ImportOutcome.Malformed()
            val decoded = runCatching { Base64.decode(data, Base64.DEFAULT) }
                .getOrElse { return ImportOutcome.Malformed(it) }
            val batch = runCatching { decodeMigrationPayload(decoded) }
                .getOrElse { return ImportOutcome.Malformed(it) }
            accounts.addAll(batch)
        }
        return ImportOutcome.Success(accounts)
    }

    private fun decodeMigrationPayload(bytes: ByteArray): List<OtpAccount> {
        val reader = ProtoReader(bytes)
        val accounts = ArrayList<OtpAccount>()
        while (reader.hasMore()) {
            val tag = reader.readTag()
            val field = ProtoReader.fieldNumber(tag)
            when (field) {
                FIELD_OTP_PARAMETERS -> {
                    val sub = reader.readBytes()
                    decodeOtpParameters(sub)?.let(accounts::add)
                }
                else -> reader.skipValue(tag)
            }
        }
        return accounts
    }

    private fun decodeOtpParameters(bytes: ByteArray): OtpAccount? {
        val reader = ProtoReader(bytes)
        var secret: ByteArray? = null
        var name = ""
        var issuer = ""
        var algorithm = OtpAlgorithm.SHA1
        var digits = 6
        var type = OtpType.TOTP
        var counter = 0L

        while (reader.hasMore()) {
            val tag = reader.readTag()
            when (ProtoReader.fieldNumber(tag)) {
                1 -> secret = reader.readBytes()
                2 -> name = reader.readString()
                3 -> issuer = reader.readString()
                4 -> algorithm = when (reader.readVarint().toInt()) {
                    2 -> OtpAlgorithm.SHA256
                    3 -> OtpAlgorithm.SHA512
                    else -> OtpAlgorithm.SHA1
                }
                5 -> digits = when (reader.readVarint().toInt()) {
                    2 -> 8
                    else -> 6
                }
                6 -> type = when (reader.readVarint().toInt()) {
                    1 -> OtpType.HOTP
                    else -> OtpType.TOTP
                }
                7 -> counter = reader.readVarint()
                else -> reader.skipValue(tag)
            }
        }

        val rawSecret = secret ?: return null
        return OtpAccount(
            issuer = issuer,
            accountName = name,
            secret = Base32.encode(rawSecret),
            algorithm = algorithm,
            digits = digits,
            period = 30,
            counter = counter,
            type = type,
        )
    }

    private companion object {
        const val FIELD_OTP_PARAMETERS = 1
    }
}
