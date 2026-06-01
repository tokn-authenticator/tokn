package me.diamondforge.tokn.importer.aegis

import android.util.Base64
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.importer.ExternalImporter
import me.diamondforge.tokn.importer.ImportOutcome
import me.diamondforge.tokn.importer.R
import me.diamondforge.tokn.importer.crypto.HexCodec
import me.diamondforge.tokn.importer.crypto.ScryptAesGcm
import org.json.JSONObject
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.inject.Inject

/**
 * Reads the Aegis Authenticator vault file. Format reverse-engineered from public sample
 * files; no source code derived from the Aegis Android app (GPL-3.0).
 *
 * Plain layout:
 *   { "version": 1, "header": { "slots": null, "params": null }, "db": { ... } }
 * Encrypted layout:
 *   { "version": 1, "header": { "slots": [...], "params": {...} }, "db": "<base64>" }
 *
 * Password slots have type=1 and carry the scrypt parameters and an AES-GCM-wrapped master key.
 * The db field is the master-key-encrypted JSON of the vault.
 */
class AegisImporter @Inject constructor() : ExternalImporter {
    override val id: String = "aegis"
    override val displayName: String = "Aegis Authenticator"
    override val noteRes: Int = R.string.importer_aegis_note
    override val acceptedMimeTypes: Array<String> = arrayOf("application/json", "*/*")

    override fun canHandle(raw: ByteArray): Boolean = runCatching {
        val root = JSONObject(raw.toString(Charsets.UTF_8))
        root.has("db") && root.has("header") && root.has("version")
    }.getOrDefault(false)

    override fun parse(raw: ByteArray, password: String?): ImportOutcome {
        val root = runCatching { JSONObject(raw.toString(Charsets.UTF_8)) }
            .getOrElse { return ImportOutcome.Malformed(it) }

        val dbField = root.opt("db") ?: return ImportOutcome.Malformed()
        return when (dbField) {
            is JSONObject -> readEntries(dbField)
            is String -> {
                if (password.isNullOrEmpty()) ImportOutcome.NeedsPassword
                else decryptAndRead(root, password)
            }

            else -> ImportOutcome.Malformed()
        }
    }

    private fun decryptAndRead(root: JSONObject, password: String): ImportOutcome {
        val header = runCatching { root.getJSONObject("header") }
            .getOrElse { return ImportOutcome.Malformed(it) }
        val slots = runCatching { header.getJSONArray("slots") }
            .getOrElse { return ImportOutcome.Malformed(it) }
        val params = runCatching { header.getJSONObject("params") }
            .getOrElse { return ImportOutcome.Malformed(it) }

        var sawPasswordSlot = false
        var masterKey: ByteArray? = null
        var lastFailure: Throwable? = null
        for (i in 0 until slots.length()) {
            val slot = slots.getJSONObject(i)
            if (slot.optInt("type", -1) != PASSWORD_SLOT_TYPE) continue
            sawPasswordSlot = true
            runCatching { unwrapMasterKey(slot, password) }
                .onSuccess { masterKey = it }
                .onFailure { lastFailure = it }
            if (masterKey != null) break
        }

        val key = masterKey ?: return when {
            !sawPasswordSlot -> ImportOutcome.Unsupported
            else -> ImportOutcome.WrongPassword(lastFailure)
        }

        val ciphertext = runCatching { Base64.decode(root.getString("db"), Base64.DEFAULT) }
            .getOrElse { return ImportOutcome.Malformed(it) }
        val nonce = runCatching { HexCodec.decode(params.getString("nonce")) }
            .getOrElse { return ImportOutcome.Malformed(it) }
        val tag = runCatching { HexCodec.decode(params.getString("tag")) }
            .getOrElse { return ImportOutcome.Malformed(it) }

        val dbJson = runCatching {
            ScryptAesGcm.decrypt(key, nonce, tag, ciphertext).toString(Charsets.UTF_8)
        }.getOrElse { e ->
            return if (e is AEADBadTagException || e is BadPaddingException)
                ImportOutcome.WrongPassword(e)
            else ImportOutcome.Malformed(e)
        }

        val db = runCatching { JSONObject(dbJson) }.getOrElse { return ImportOutcome.Malformed(it) }
        return readEntries(db)
    }

    private fun unwrapMasterKey(slot: JSONObject, password: String): ByteArray {
        val salt = HexCodec.decode(slot.getString("salt"))
        val n = slot.getInt("n")
        val r = slot.getInt("r")
        val p = slot.getInt("p")
        val wrappedKey = HexCodec.decode(slot.getString("key"))
        val keyParams = slot.getJSONObject("key_params")
        val keyNonce = HexCodec.decode(keyParams.getString("nonce"))
        val keyTag = HexCodec.decode(keyParams.getString("tag"))

        val derived = ScryptAesGcm.deriveKey(password.toByteArray(Charsets.UTF_8), salt, n, r, p)
        return ScryptAesGcm.decrypt(derived, keyNonce, keyTag, wrappedKey)
    }

    private fun readEntries(db: JSONObject): ImportOutcome {
        val entries = runCatching { db.getJSONArray("entries") }
            .getOrElse { return ImportOutcome.Malformed(it) }

        val accounts = ArrayList<OtpAccount>(entries.length())
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val type = when (entry.optString("type").lowercase()) {
                "totp" -> OtpType.TOTP
                "hotp" -> OtpType.HOTP
                else -> continue
            }
            val info = entry.optJSONObject("info") ?: continue
            val secret = info.optString("secret").takeIf { it.isNotBlank() } ?: continue
            val algo = when (info.optString("algo").uppercase()) {
                "SHA256" -> OtpAlgorithm.SHA256
                "SHA512" -> OtpAlgorithm.SHA512
                else -> OtpAlgorithm.SHA1
            }
            accounts.add(
                OtpAccount(
                    issuer = entry.optString("issuer", ""),
                    accountName = entry.optString("name", ""),
                    secret = secret,
                    algorithm = algo,
                    digits = info.optInt("digits", 6),
                    period = info.optInt("period", 30),
                    counter = info.optLong("counter", 0),
                    type = type,
                    groups = entry.optString("group").ifBlank { null }
                        ?.let { listOf(it) }
                        ?: emptyList(),
                ),
            )
        }
        return ImportOutcome.Success(accounts)
    }

    private companion object {
        const val PASSWORD_SLOT_TYPE = 1
    }
}
