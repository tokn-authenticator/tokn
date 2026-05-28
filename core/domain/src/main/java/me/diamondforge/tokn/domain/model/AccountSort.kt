package me.diamondforge.tokn.domain.model

/**
 * Non-CUSTOM modes render in the chosen order without mutating
 * [OtpAccount.sortOrder]; only CUSTOM honours manual drag-to-reorder.
 *
 * USAGE_COUNT and LAST_USED are descending only ("most used" /
 * "recently used").
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
