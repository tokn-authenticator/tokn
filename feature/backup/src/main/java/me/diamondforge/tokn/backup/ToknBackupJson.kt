package me.diamondforge.tokn.backup

import android.net.Uri
import android.util.Base64
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import org.json.JSONArray
import org.json.JSONObject

internal fun serializeAccountsToJson(accounts: List<OtpAccount>): String {
    val array = JSONArray()
    accounts.forEach { account ->
        array.put(
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
                if (account.groups.isNotEmpty()) {
                    put("groups", JSONArray().apply { account.groups.forEach { put(it) } })
                }
                account.customIconBytes?.let {
                    put("customIconPng", Base64.encodeToString(it, Base64.NO_WRAP))
                }
                account.iconPackId?.let { put("iconPackId", it) }
                account.iconPackFile?.let { put("iconPackFile", it) }
                // usageCount / lastUsedAt deliberately omitted: those
                // are per-device behavioural signals, not part of the
                // account identity, and shouldn't roam with a backup.
            },
        )
    }
    return JSONObject().apply { put("accounts", array); put("version", 1) }.toString()
}

internal fun deserializeAccountsFromJson(json: String): List<OtpAccount> {
    val root = JSONObject(json)
    val array = root.getJSONArray("accounts")
    return (0 until array.length()).map { i ->
        val obj = array.getJSONObject(i)
        val customIcon = if (obj.has("customIconPng") && !obj.isNull("customIconPng")) {
            runCatching {
                Base64.decode(obj.getString("customIconPng"), Base64.DEFAULT)
            }.getOrNull()
        } else null
        OtpAccount(
            issuer = obj.getString("issuer"),
            accountName = obj.getString("accountName"),
            secret = obj.getString("secret"),
            algorithm = OtpAlgorithm.valueOf(obj.optString("algorithm", "SHA1")),
            digits = obj.optInt("digits", 6),
            period = obj.optInt("period", 30),
            counter = obj.optLong("counter", 0),
            type = OtpType.valueOf(obj.optString("type", "TOTP")),
            sortOrder = obj.optInt("sortOrder", 0),
            groups = readBackupGroups(obj),
            customIconBytes = customIcon,
            iconPackId = obj.optString("iconPackId").ifBlank { null },
            iconPackFile = obj.optString("iconPackFile").ifBlank { null },
        )
    }
}

// New backups write `groups` (array); pre-multi-group backups wrote a
// single `group` (string). Read either so legacy files restore intact.
private fun readBackupGroups(obj: JSONObject): List<String> {
    if (obj.has("groups") && !obj.isNull("groups")) {
        val arr = obj.getJSONArray("groups")
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotBlank()) add(s)
            }
        }
    }
    val legacy = obj.optString("group")
    return if (legacy.isBlank()) emptyList() else listOf(legacy)
}

/**
 * Plain Tokn backups are JSON with both `accounts` (array) and `version` keys;
 * encrypted ones are a wrapper with `ciphertext` + `iv` + `salt`. Both are JSON
 * at the top level; the key set decides the path.
 */
internal fun classifyToknBackup(raw: ByteArray): ToknBackupShape? = runCatching {
    val obj = JSONObject(raw.toString(Charsets.UTF_8))
    when {
        obj.has("ciphertext") && obj.has("iv") && obj.has("salt") -> ToknBackupShape.Encrypted
        obj.has("accounts") && obj.has("version") -> ToknBackupShape.Plain
        else -> null
    }
}.getOrNull()

internal enum class ToknBackupShape { Plain, Encrypted }

/**
 * Each account becomes one `otpauth://{type}/{issuer}:{name}?...` line; the
 * order matches the input. Issuer + accountName are encoded per the Key URI
 * spec so labels containing `:`, `/`, `?` or `#` round-trip back through
 * `OtpAuthParser`.
 */
internal fun serializeAccountsAsOtpAuthUriList(accounts: List<OtpAccount>): String =
    accounts.joinToString(separator = "\n", postfix = "\n") { it.toOtpAuthUri() }

/**
 * Human-readable dump: one key/value block per account, blocks separated by a
 * blank line. Aimed at archival/inspection, not round-tripping. There's no
 * parser for this format; callers who want re-import should pick otpauth or
 * the Tokn vault format.
 */
internal fun serializeAccountsAsPlainText(accounts: List<OtpAccount>): String =
    accounts.joinToString(separator = "\n\n", postfix = "\n") { it.toPlainTextBlock() }

private fun OtpAccount.toPlainTextBlock(): String = buildString {
    appendLine("Issuer: $issuer")
    appendLine("Name: $accountName")
    appendLine("Secret: $secret")
    appendLine("Type: ${type.name}")
    appendLine("Algorithm: ${algorithm.name}")
    appendLine("Digits: $digits")
    when (type) {
        OtpType.TOTP -> appendLine("Period: $period")
        OtpType.HOTP -> appendLine("Counter: $counter")
    }
    if (groups.isNotEmpty()) appendLine("Groups: ${groups.joinToString(", ")}")
}.trimEnd()

private fun OtpAccount.toOtpAuthUri(): String {
    val typeSegment = when (type) {
        OtpType.TOTP -> "totp"
        OtpType.HOTP -> "hotp"
    }
    val label = buildString {
        if (issuer.isNotBlank()) {
            append(Uri.encode(issuer))
            append(':')
        }
        append(Uri.encode(accountName))
    }
    val params = buildList {
        add("secret" to secret)
        if (issuer.isNotBlank()) add("issuer" to issuer)
        if (algorithm != OtpAlgorithm.SHA1) add("algorithm" to algorithm.name)
        if (digits != 6) add("digits" to digits.toString())
        when (type) {
            OtpType.TOTP -> if (period != 30) add("period" to period.toString())
            OtpType.HOTP -> add("counter" to counter.toString())
        }
    }
    val query = params.joinToString("&") { (k, v) -> "$k=${Uri.encode(v)}" }
    return "otpauth://$typeSegment/$label?$query"
}
