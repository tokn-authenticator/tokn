package me.diamondforge.tokn.home

import me.diamondforge.tokn.domain.model.AccountSort
import me.diamondforge.tokn.domain.model.OtpAccount

/**
 * Stable sort with consistent tiebreakers:
 * - String comparisons are case-insensitive.
 * - sortOrder is the universal tiebreaker (matches CUSTOM ordering).
 * - lastUsedAt = 0L means "never used" and naturally sinks to the bottom
 *   under LAST_USED (descending) without null-special-casing.
 */
internal fun List<OtpAccount>.sortedFor(sort: AccountSort): List<OtpAccount> {
    val ci = String.CASE_INSENSITIVE_ORDER
    return when (sort) {
        AccountSort.CUSTOM -> this
        AccountSort.ISSUER_ASC -> sortedWith(byString(ci) { it.issuer })
        AccountSort.ISSUER_DESC -> sortedWith(byString(ci.reversed()) { it.issuer })
        AccountSort.NAME_ASC -> sortedWith(byString(ci) { it.accountName })
        AccountSort.NAME_DESC -> sortedWith(byString(ci.reversed()) { it.accountName })
        AccountSort.USAGE_COUNT -> sortedWith(byNumberDesc { it.usageCount })
        AccountSort.LAST_USED -> sortedWith(byNumberDesc { it.lastUsedAt })
    }
}

private fun byString(
    order: Comparator<String>,
    selector: (OtpAccount) -> String,
): Comparator<OtpAccount> = compareBy(order, selector).thenBy { it.sortOrder }

private fun <T : Comparable<T>> byNumberDesc(
    selector: (OtpAccount) -> T,
): Comparator<OtpAccount> = compareByDescending(selector).thenBy { it.sortOrder }
