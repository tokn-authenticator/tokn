package me.diamondforge.tokn.importer.otpauth

import android.net.Uri
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType

/**
 * Shared parser for `otpauth://` URIs as defined by Key URI Format
 * (https://github.com/google/google-authenticator/wiki/Key-Uri-Format).
 */
internal object OtpAuthParser {
    fun parse(uriString: String): OtpAccount? {
        val uri = runCatching { Uri.parse(uriString.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "otpauth") return null

        val type = when (uri.host?.lowercase()) {
            "totp" -> OtpType.TOTP
            "hotp" -> OtpType.HOTP
            else -> return null
        }

        // Path is "/label" where label is "Issuer:Account" or just "Account".
        val rawLabel = uri.path?.removePrefix("/") ?: return null
        val labelDecoded = runCatching { Uri.decode(rawLabel) }.getOrDefault(rawLabel)
        val (labelIssuer, accountName) = splitLabel(labelDecoded)

        val secret = uri.getQueryParameter("secret")?.takeIf { it.isNotBlank() } ?: return null
        val paramIssuer = uri.getQueryParameter("issuer").orEmpty()
        val issuer = paramIssuer.ifBlank { labelIssuer }

        val algorithm = when (uri.getQueryParameter("algorithm")?.uppercase()) {
            "SHA256" -> OtpAlgorithm.SHA256
            "SHA512" -> OtpAlgorithm.SHA512
            else -> OtpAlgorithm.SHA1
        }
        val digits = uri.getQueryParameter("digits")?.toIntOrNull() ?: 6
        val period = uri.getQueryParameter("period")?.toIntOrNull() ?: 30
        val counter = uri.getQueryParameter("counter")?.toLongOrNull() ?: 0L

        return OtpAccount(
            issuer = issuer,
            accountName = accountName,
            secret = secret,
            algorithm = algorithm,
            digits = digits,
            period = period,
            counter = counter,
            type = type,
        )
    }

    private fun splitLabel(label: String): Pair<String, String> {
        val idx = label.indexOf(':')
        return if (idx >= 0) {
            label.substring(0, idx).trim() to label.substring(idx + 1).trim()
        } else {
            "" to label.trim()
        }
    }
}
