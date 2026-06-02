package me.diamondforge.tokn.data.icon

import java.io.File

data class IconPack(
    val uuid: String,
    val name: String,
    val version: Int,
    val icons: List<IconPackIcon>,
)

data class IconPackIcon(
    val filename: String,
    val displayName: String,
    val category: String?,
    val issuerMatches: List<String>,
)

data class InstalledIconPack(
    val pack: IconPack,
    val directory: File,
) {
    val iconCount: Int get() = pack.icons.size

    fun fileFor(filename: String): File = File(directory, filename)
}

enum class IconMatchType { EXACT, NORMAL, INVERSE }

data class IconSuggestion(
    val icon: IconPackIcon,
    val matchType: IconMatchType,
)

fun InstalledIconPack.suggestionsFor(issuer: String): List<IconSuggestion> {
    if (issuer.isBlank()) return emptyList()
    val lowerEntry = issuer.lowercase().trim()
    if (lowerEntry.isEmpty()) return emptyList()
    val exact = mutableListOf<IconPackIcon>()
    val normal = mutableListOf<IconPackIcon>()
    val inverse = mutableListOf<IconPackIcon>()
    pack.icons.forEach { icon ->
        var exactMatched = false
        var matched = false
        var inverseMatched = false
        for (candidate in icon.issuerMatches) {
            val lowerIcon = candidate.lowercase().trim()
            if (lowerIcon == lowerEntry) {
                exactMatched = true
                break
            }
            if (lowerIcon.contains(lowerEntry)) {
                matched = true
                continue
            }
            if (lowerEntry.contains(lowerIcon)) {
                inverseMatched = true
            }
        }
        when {
            exactMatched -> exact += icon
            matched -> normal += icon
            inverseMatched -> inverse += icon
        }
    }
    return exact.map { IconSuggestion(it, IconMatchType.EXACT) } +
            normal.map { IconSuggestion(it, IconMatchType.NORMAL) } +
            inverse.map { IconSuggestion(it, IconMatchType.INVERSE) }
}
