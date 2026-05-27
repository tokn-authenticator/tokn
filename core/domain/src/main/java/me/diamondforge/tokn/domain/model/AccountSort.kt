package me.diamondforge.tokn.domain.model

/**
 * Display order for the account list. CUSTOM honours the user's manual
 * drag-to-reorder; any other value disables the drag handle and renders the
 * list in the chosen order without mutating [OtpAccount.sortOrder].
 *
 * USAGE_COUNT and LAST_USED are descending only ("most used" / "recently
 * used")
 */
enum class AccountSort {
    CUSTOM,
    ISSUER_ASC,
    ISSUER_DESC,
    NAME_ASC,
    NAME_DESC,
    USAGE_COUNT,
    LAST_USED,
}
