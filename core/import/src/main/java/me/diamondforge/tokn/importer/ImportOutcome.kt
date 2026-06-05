package me.diamondforge.tokn.importer

import me.diamondforge.tokn.domain.model.OtpAccount

sealed interface ImportOutcome {
    data class Success(val accounts: List<OtpAccount>, val unsupportedCount: Int = 0) : ImportOutcome
    data object NeedsPassword : ImportOutcome
    data class WrongPassword(val cause: Throwable? = null) : ImportOutcome
    data class Malformed(val cause: Throwable? = null) : ImportOutcome
    data object Unsupported : ImportOutcome
}
