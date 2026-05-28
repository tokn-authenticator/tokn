package me.diamondforge.tokn.home

internal fun formatOtpCode(code: String): String = when (code.length) {
    6 -> "${code.substring(0, 3)} ${code.substring(3)}"
    8 -> "${code.substring(0, 4)} ${code.substring(4)}"
    else -> code
}

internal fun maskOtpCode(code: String): String = formatOtpCode("•".repeat(code.length))
