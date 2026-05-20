package me.diamondforge.tokn.add

import android.net.Uri
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType

/**
 * Parses `otpauth://...` URIs into [OtpAccount]s. Lives outside the
 * ViewModel so it can be unit-tested without Hilt or coroutine setup.
 * Reference: https://github.com/google/google-authenticator/wiki/Key-Uri-Format
 */
object OtpAuthParser {

    fun parse(raw: String): OtpAccount {
        val uri = Uri.parse(raw)
        require(uri.scheme == "otpauth") { "Not an otpauth URI" }

        val type = when (uri.host?.lowercase()) {
            "totp" -> OtpType.TOTP
            "hotp" -> OtpType.HOTP
            else -> OtpType.TOTP
        }

        val label = Uri.decode(uri.path?.removePrefix("/") ?: "")
        val issuerFromLabel = if (label.contains(":")) label.substringBefore(":").trim() else ""
        val accountNameFromLabel = if (label.contains(":")) label.substringAfter(":").trim() else label

        val secret = uri.getQueryParameter("secret") ?: error("Missing secret")
        val issuer = uri.getQueryParameter("issuer") ?: issuerFromLabel
        val algorithm = when (uri.getQueryParameter("algorithm")?.uppercase()) {
            "SHA256" -> OtpAlgorithm.SHA256
            "SHA512" -> OtpAlgorithm.SHA512
            else -> OtpAlgorithm.SHA1
        }
        val digits = uri.getQueryParameter("digits")?.toIntOrNull() ?: 6
        val period = uri.getQueryParameter("period")?.toIntOrNull() ?: 30

        return OtpAccount(
            issuer = issuer,
            accountName = accountNameFromLabel,
            secret = secret,
            algorithm = algorithm,
            digits = digits,
            period = period,
            type = type,
        )
    }
}
