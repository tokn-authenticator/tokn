package me.diamondforge.tokn.importer.otpauth

import android.net.Uri
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType

fun OtpAccount.toOtpAuthUri(): String {
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
