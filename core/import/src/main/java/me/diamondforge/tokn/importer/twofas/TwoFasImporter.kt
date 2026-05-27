package me.diamondforge.tokn.importer.twofas

import android.util.Base64
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.importer.ExternalImporter
import me.diamondforge.tokn.importer.ImportOutcome
import me.diamondforge.tokn.importer.R
import me.diamondforge.tokn.importer.crypto.Pbkdf2AesGcm
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.inject.Inject

/**
 * Reads the 2FAS Authenticator backup (.2fas / .json). Format derived from public
 * sample files; not copied from any GPL source.
 *
 * Top-level shape: `{ "schemaVersion": <int>, "services": [...], "servicesEncrypted": "<b64>:<b64>:<b64>"? }`.
 * Encrypted backups omit `services` (or have an empty array) and store the encrypted body in
 * `servicesEncrypted` formatted as `ciphertext:salt:iv`, all base64. The `ciphertext`
 * portion has the 128-bit GCM auth tag appended. KDF is PBKDF2-HMAC-SHA256 with 10 000
 * iterations producing a 256-bit AES key used in GCM mode.
 */
class TwoFasImporter @Inject constructor() : ExternalImporter {
    override val id: String = "twofas"
    override val displayName: String = "2FAS Authenticator"
    override val noteRes: Int = R.string.importer_twofas_note
    override val acceptedMimeTypes: Array<String> = arrayOf("application/json", "*/*")

    override fun canHandle(raw: ByteArray): Boolean = runCatching {
        val root = JSONObject(raw.toString(Charsets.UTF_8))
        root.has("schemaVersion") && (root.has("services") || root.has("servicesEncrypted"))
    }.getOrDefault(false)

    override fun parse(raw: ByteArray, password: String?): ImportOutcome {
        val root = runCatching { JSONObject(raw.toString(Charsets.UTF_8)) }
            .getOrElse { return ImportOutcome.Malformed(it) }

        val encryptedBlob = root.optString("servicesEncrypted").takeIf { it.isNotBlank() }
        if (encryptedBlob != null) {
            if (password.isNullOrEmpty()) return ImportOutcome.NeedsPassword
            return decryptAndRead(encryptedBlob, password)
        }

        val services = runCatching { root.getJSONArray("services") }
            .getOrElse { return ImportOutcome.Malformed(it) }
        return readServices(services)
    }

    private fun decryptAndRead(blob: String, password: String): ImportOutcome {
        val parts = blob.split(':')
        if (parts.size < 3) return ImportOutcome.Malformed()
        val ciphertext = runCatching { Base64.decode(parts[0], Base64.DEFAULT) }
            .getOrElse { return ImportOutcome.Malformed(it) }
        val salt = runCatching { Base64.decode(parts[1], Base64.DEFAULT) }
            .getOrElse { return ImportOutcome.Malformed(it) }
        val iv = runCatching { Base64.decode(parts[2], Base64.DEFAULT) }
            .getOrElse { return ImportOutcome.Malformed(it) }

        val key =
            Pbkdf2AesGcm.deriveKey(password.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_BITS)
        val plaintext = runCatching { Pbkdf2AesGcm.decrypt(key, iv, ciphertext) }
            .getOrElse { e ->
                return if (e is AEADBadTagException || e is BadPaddingException)
                    ImportOutcome.WrongPassword(e)
                else ImportOutcome.Malformed(e)
            }

        val services = runCatching { JSONArray(plaintext.toString(Charsets.UTF_8)) }
            .getOrElse { return ImportOutcome.Malformed(it) }
        return readServices(services)
    }

    private fun readServices(services: JSONArray): ImportOutcome {
        val accounts = ArrayList<OtpAccount>(services.length())
        for (i in 0 until services.length()) {
            val svc = services.optJSONObject(i) ?: continue
            val secret = svc.optString("secret").takeIf { it.isNotBlank() } ?: continue
            val otp = svc.optJSONObject("otp") ?: JSONObject()

            val tokenType = otp.optString("tokenType", "TOTP").uppercase()
            val type = when (tokenType) {
                "TOTP", "STEAM", "" -> OtpType.TOTP
                "HOTP" -> OtpType.HOTP
                else -> continue
            }

            val algo = when (otp.optString("algorithm").uppercase()) {
                "SHA256" -> OtpAlgorithm.SHA256
                "SHA512" -> OtpAlgorithm.SHA512
                else -> OtpAlgorithm.SHA1
            }

            val issuer = svc.optString("name").ifBlank { otp.optString("issuer") }
            val account = otp.optString("account")

            accounts.add(
                OtpAccount(
                    issuer = issuer,
                    accountName = account,
                    secret = secret,
                    algorithm = algo,
                    digits = otp.optInt("digits", 6),
                    period = otp.optInt("period", 30),
                    counter = otp.optLong("counter", 0),
                    type = type,
                ),
            )
        }
        return ImportOutcome.Success(accounts)
    }

    private companion object {
        const val PBKDF2_ITERATIONS = 10_000
        const val AES_KEY_BITS = 256
    }
}
