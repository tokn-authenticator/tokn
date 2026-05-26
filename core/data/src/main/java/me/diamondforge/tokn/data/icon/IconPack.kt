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

enum class IconMatchType { NORMAL, INVERSE }

data class IconSuggestion(
    val icon: IconPackIcon,
    val matchType: IconMatchType,
)

fun InstalledIconPack.suggestionsFor(issuer: String): List<IconSuggestion> {
    if (issuer.isBlank()) return emptyList()
    val lowerEntry = issuer.lowercase()
    val normal = mutableListOf<IconPackIcon>()
    val inverse = mutableListOf<IconPackIcon>()
    pack.icons.forEach { icon ->
        var matched = false
        var inverseMatched = false
        for (candidate in icon.issuerMatches) {
            val lowerIcon = candidate.lowercase()
            if (lowerIcon.contains(lowerEntry)) {
                matched = true
                break
            }
            if (lowerEntry.contains(lowerIcon)) {
                inverseMatched = true
            }
        }
        if (matched) normal += icon
        else if (inverseMatched) inverse += icon
    }
    return normal.map { IconSuggestion(it, IconMatchType.NORMAL) } +
        inverse.map { IconSuggestion(it, IconMatchType.INVERSE) }
}
