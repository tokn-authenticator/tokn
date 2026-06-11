package me.diamondforge.tokn.importer.stratum

import android.util.Base64
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import me.diamondforge.tokn.importer.ExternalImporter
import me.diamondforge.tokn.importer.ImportOutcome
import me.diamondforge.tokn.importer.R
import me.diamondforge.tokn.importer.crypto.StratumCrypto
import org.json.JSONObject
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.inject.Inject

/**
 * Imports Stratum / Authenticator Pro backups (.json or .statum).
 *
 * Unencrypted: plain JSON with top-level "Authenticators" key.
 * Strong encrypted: 16-byte "AUTHENTICATORPRO" magic + Argon2id key derivation + AES-256-GCM.
 * Legacy encrypted: 16-byte "AuthenticatorPro" magic + PBKDF2-SHA1 key derivation + AES-256-CBC.
 *
 * Only TOTP (type 2) and HOTP (type 1) accounts are imported. MobileOTP, SteamOTP, and
 * YandexOTP entries are counted and reported via [ImportOutcome.Success.unsupportedCount].
 */
class StratumImporter @Inject constructor() : ExternalImporter {

    override val id: String = "stratum"
    override val displayName: String = "Stratum / Authenticator Pro"
    override val noteRes: Int = R.string.importer_stratum_note
    override val acceptedMimeTypes: Array<String> =
        arrayOf("application/octet-stream", "application/json", "*/*")

    override fun canHandle(raw: ByteArray): Boolean {
        if (raw.size >= 16) {
            val header = raw.copyOfRange(0, 16).toString(Charsets.US_ASCII)
            if (header == HEADER_STRONG || header == HEADER_LEGACY) return true
        }
        return runCatching {
            JSONObject(raw.toString(Charsets.UTF_8)).has("Authenticators")
        }.getOrDefault(false)
    }

    override fun parse(raw: ByteArray, password: String?): ImportOutcome {
        if (raw.size >= 16) {
            val header = raw.copyOfRange(0, 16).toString(Charsets.US_ASCII)
            if (header == HEADER_STRONG || header == HEADER_LEGACY) {
                if (password.isNullOrEmpty()) return ImportOutcome.NeedsPassword
                val decrypted = runCatching {
                    if (header == HEADER_STRONG) StratumCrypto.decryptStrong(raw, password)
                    else StratumCrypto.decryptLegacy(raw, password)
                }.getOrElse { e ->
                    return if (e is AEADBadTagException || e is BadPaddingException)
                        ImportOutcome.WrongPassword(e)
                    else
                        ImportOutcome.Malformed(e)
                }
                return runCatching {
                    parseJson(decrypted.toString(Charsets.UTF_8))
                }.getOrElse { ImportOutcome.Malformed(it) }
            }
        }
        return runCatching {
            parseJson(raw.toString(Charsets.UTF_8))
        }.getOrElse { ImportOutcome.Malformed(it) }
    }

    private fun parseJson(json: String): ImportOutcome {
        val root = JSONObject(json)
        val authenticatorsArray = root.optJSONArray("Authenticators")
            ?: return ImportOutcome.Malformed()

        val categoryNames = buildCategoryNames(root)
        val secretGroups = buildSecretGroups(root, categoryNames)
        val customIcons = buildCustomIcons(root)

        val accounts = mutableListOf<OtpAccount>()
        var unsupportedCount = 0

        for (i in 0 until authenticatorsArray.length()) {
            val entry = authenticatorsArray.getJSONObject(i)
            val type = when (entry.optInt("Type", -1)) {
                1 -> OtpType.HOTP
                2 -> OtpType.TOTP
                else -> {
                    unsupportedCount++
                    continue
                }
            }
            val secret = entry.optString("Secret").takeIf { it.isNotBlank() } ?: continue
            val algorithm = when (entry.optInt("Algorithm", 0)) {
                1 -> OtpAlgorithm.SHA256
                2 -> OtpAlgorithm.SHA512
                else -> OtpAlgorithm.SHA1
            }
            val iconField = entry.optString("Icon", "")
            val customIconBytes =
                if (iconField.startsWith("@")) customIcons[iconField.removePrefix("@")]
                else null

            accounts.add(
                OtpAccount(
                    issuer = entry.optString("Issuer", ""),
                    accountName = entry.optString("Username", ""),
                    secret = secret,
                    type = type,
                    algorithm = algorithm,
                    digits = entry.optInt("Digits", 6),
                    period = entry.optInt("Period", 30),
                    counter = entry.optLong("Counter", 0),
                    groups = secretGroups[secret] ?: emptyList(),
                    customIconBytes = customIconBytes,
                ),
            )
        }

        return ImportOutcome.Success(accounts, unsupportedCount)
    }

    private fun buildCategoryNames(root: JSONObject): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val cats = root.optJSONArray("Categories") ?: return map
        for (i in 0 until cats.length()) {
            val cat = cats.getJSONObject(i)
            val id = cat.optString("Id").takeIf { it.isNotBlank() } ?: continue
            val name = cat.optString("Name").takeIf { it.isNotBlank() } ?: continue
            map[id] = name
        }
        return map
    }

    private fun buildSecretGroups(
        root: JSONObject,
        categoryNames: Map<String, String>,
    ): Map<String, List<String>> {
        val map = mutableMapOf<String, MutableList<String>>()
        val ac = root.optJSONArray("AuthenticatorCategories") ?: return map
        for (i in 0 until ac.length()) {
            val entry = ac.getJSONObject(i)
            val secret =
                entry.optString("AuthenticatorSecret").takeIf { it.isNotBlank() } ?: continue
            val catId = entry.optString("CategoryId").takeIf { it.isNotBlank() } ?: continue
            val name = categoryNames[catId] ?: continue
            map.getOrPut(secret) { mutableListOf() }.add(name)
        }
        return map
    }

    private fun buildCustomIcons(root: JSONObject): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        val icons = root.optJSONArray("CustomIcons") ?: return map
        for (i in 0 until icons.length()) {
            val icon = icons.getJSONObject(i)
            val id = icon.optString("Id").takeIf { it.isNotBlank() } ?: continue
            val data = icon.optString("Data").takeIf { it.isNotBlank() } ?: continue
            runCatching { Base64.decode(data, Base64.DEFAULT) }.onSuccess { map[id] = it }
        }
        return map
    }

    private companion object {
        const val HEADER_STRONG = "AUTHENTICATORPRO"
        const val HEADER_LEGACY = "AuthenticatorPro"
    }
}
