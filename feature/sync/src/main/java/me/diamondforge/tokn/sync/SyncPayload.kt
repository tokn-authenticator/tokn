package me.diamondforge.tokn.sync

import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire format for transferring accounts between devices. Matches the
 * unencrypted backup JSON shape so the same payload could be reused
 * elsewhere in the future.
 */
object SyncPayload {
    const val VERSION = 1

    fun serialize(accounts: List<OtpAccount>): String {
        val arr = JSONArray()
        accounts.forEach { account ->
            arr.put(
                JSONObject().apply {
                    put("issuer", account.issuer)
                    put("accountName", account.accountName)
                    put("secret", account.secret)
                    put("algorithm", account.algorithm.name)
                    put("digits", account.digits)
                    put("period", account.period)
                    put("counter", account.counter)
                    put("type", account.type.name)
                    put("sortOrder", account.sortOrder)
                    account.group?.let { put("group", it) }
                    account.customIconBytes?.let {
                        put(
                            "customIconPng",
                            android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
                        )
                    }
                    account.iconPackId?.let { put("iconPackId", it) }
                    account.iconPackFile?.let { put("iconPackFile", it) }
                    // usageCount and lastUsedAt are intentionally not
                    // transferred: they reflect per-device behaviour, not
                    // account identity, and rebuild themselves on the
                    // receiving device as the user works there.
                },
            )
        }
        return JSONObject().apply {
            put("accounts", arr)
            put("version", VERSION)
        }.toString()
    }

    fun deserialize(json: String): List<OtpAccount> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("accounts")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val customIcon = if (o.has("customIconPng") && !o.isNull("customIconPng")) {
                runCatching {
                    android.util.Base64.decode(
                        o.getString("customIconPng"),
                        android.util.Base64.DEFAULT
                    )
                }.getOrNull()
            } else null
            OtpAccount(
                issuer = o.optString("issuer", ""),
                accountName = o.optString("accountName", ""),
                secret = o.getString("secret"),
                algorithm = OtpAlgorithm.valueOf(o.optString("algorithm", "SHA1")),
                digits = o.optInt("digits", 6),
                period = o.optInt("period", 30),
                counter = o.optLong("counter", 0),
                type = OtpType.valueOf(o.optString("type", "TOTP")),
                sortOrder = o.optInt("sortOrder", 0),
                group = o.optString("group").ifBlank { null },
                customIconBytes = customIcon,
                iconPackId = o.optString("iconPackId").ifBlank { null },
                iconPackFile = o.optString("iconPackFile").ifBlank { null },
            )
        }
    }
}
