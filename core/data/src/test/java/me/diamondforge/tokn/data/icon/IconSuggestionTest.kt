package me.diamondforge.tokn.data.icon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IconSuggestionTest {

    private fun installed(vararg icons: IconPackIcon): InstalledIconPack {
        val pack = IconPack(
            uuid = "00000000-0000-0000-0000-000000000000",
            name = "test",
            version = 1,
            icons = icons.toList(),
        )
        return InstalledIconPack(pack, File("/tmp/ignored"))
    }

    private fun icon(filename: String, vararg issuers: String) =
        IconPackIcon(
            filename = filename,
            displayName = filename,
            category = null,
            issuerMatches = issuers.toList()
        )

    @Test
    fun `blank issuer yields empty list`() {
        val pack = installed(icon("g.svg", "Google"))
        assertTrue(pack.suggestionsFor("").isEmpty())
        assertTrue(pack.suggestionsFor("   ").isEmpty())
    }

    @Test
    fun `match is case-insensitive`() {
        val pack = installed(icon("g.svg", "Google"))
        val result = pack.suggestionsFor("google").single()
        assertEquals(IconMatchType.NORMAL, result.matchType)
        assertEquals("g.svg", result.icon.filename)
    }

    @Test
    fun `normal match (icon contains query) sorts before inverse match (query contains icon)`() {
        val pack = installed(
            icon("normal.svg", "GitHub Actions"),  // candidate contains query "GitHub" — NORMAL
            icon(
                "inverse.svg",
                "Git"
            ),            // query "GitHub" contains candidate "Git" — INVERSE
        )
        val result = pack.suggestionsFor("GitHub")
        assertEquals(2, result.size)
        assertEquals(IconMatchType.NORMAL, result[0].matchType)
        assertEquals("normal.svg", result[0].icon.filename)
        assertEquals(IconMatchType.INVERSE, result[1].matchType)
        assertEquals("inverse.svg", result[1].icon.filename)
    }

    @Test
    fun `icon with no matching candidate is excluded`() {
        val pack = installed(
            icon("g.svg", "Google"),
            icon("x.svg", "Microsoft"),
        )
        val result = pack.suggestionsFor("Google")
        assertEquals(1, result.size)
        assertEquals("g.svg", result.single().icon.filename)
    }

    @Test
    fun `normal match wins even if an earlier candidate would have inverse-matched`() {
        // For a given icon, the algorithm stops at the first NORMAL hit. An icon that
        // could match both ways should be reported as NORMAL, not INVERSE.
        val pack = installed(
            icon("g.svg", "Google", "G"),  // "G" is inverse of any longer query
        )
        val result = pack.suggestionsFor("Google")
        assertEquals(IconMatchType.NORMAL, result.single().matchType)
    }

    @Test
    fun `icon with empty issuer list never matches`() {
        val pack = installed(icon("g.svg"))
        assertTrue(pack.suggestionsFor("Google").isEmpty())
    }
}
