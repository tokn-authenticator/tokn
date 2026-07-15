package me.diamondforge.tokn.backup

import me.diamondforge.tokn.domain.model.Group
import me.diamondforge.tokn.domain.model.OtpAccount
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ToknBackupJsonTest {

    @Test
    fun `serialize emits groups array when account has groups`() {
        val account = OtpAccount(
            issuer = "GitHub",
            accountName = "alice",
            secret = "JBSWY3DPEHPK3PXP",
            groups = listOf("Work", "Critical"),
        )
        val json = JSONObject(serializeAccountsToJson(listOf(account)))
        val emitted = json.getJSONArray("accounts").getJSONObject(0)
        val groups = emitted.getJSONArray("groups")
        assertEquals(2, groups.length())
        assertEquals("Work", groups.getString(0))
        assertEquals("Critical", groups.getString(1))
        // Legacy single-field key is not co-written; readers always look
        // at `groups` first and fall back to `group` for old files.
        assertTrue(!emitted.has("group"))
    }

    @Test
    fun `legacy single-string group field still restores into the groups list`() {
        // Pre-multi-group backup format; user upgrades and restores from
        // an older file. The single string becomes a 1-element list.
        val raw = """
            {
              "version": 1,
              "accounts": [
                {"issuer":"GitHub","accountName":"alice","secret":"JBSWY3DPEHPK3PXP","group":"Work"}
              ]
            }
        """.trimIndent()
        val restored = deserializeAccountsFromJson(raw)
        assertEquals(1, restored.size)
        assertEquals(listOf("Work"), restored.first().groups)
    }

    @Test
    fun `groups array takes precedence over a legacy group field`() {
        // Defensive against a backup-rewriter that wrote both for transition.
        val raw = """
            {
              "version": 1,
              "accounts": [
                {"issuer":"i","accountName":"n","secret":"X",
                 "group":"Old","groups":["Work","Personal"]}
              ]
            }
        """.trimIndent()
        val restored = deserializeAccountsFromJson(raw)
        assertEquals(listOf("Work", "Personal"), restored.first().groups)
    }

    @Test
    fun `missing group fields yield an empty list`() {
        val raw = """
            {"version":1,"accounts":[{"issuer":"i","accountName":"n","secret":"X"}]}
        """.trimIndent()
        val restored = deserializeAccountsFromJson(raw)
        assertTrue(restored.first().groups.isEmpty())
    }

    @Test
    fun `old backup without declaredGroups still restores accounts and yields no declared groups`() {
        val raw = """
            {
              "version": 1,
              "accounts": [
                {"issuer":"GitHub","accountName":"alice","secret":"JBSWY3DPEHPK3PXP","group":"Work"}
              ]
            }
        """.trimIndent()
        val restored = deserializeAccountsFromJson(raw)
        assertEquals(listOf("Work"), restored.first().groups)
        assertTrue(readDeclaredGroups(raw).isEmpty())
    }

    @Test
    fun `declaredGroups round trips name color and order`() {
        val groups = listOf(
            Group(name = "Work", colorArgb = 0xFF1155CC.toInt(), sortOrder = 0),
            Group(name = "Empty", colorArgb = null, sortOrder = 1),
        )
        val json = serializeAccountsToJson(emptyList(), groups)
        val restored = readDeclaredGroups(json)
        assertEquals(2, restored.size)
        assertEquals("Work", restored[0].name)
        assertEquals(0xFF1155CC.toInt(), restored[0].colorArgb)
        assertEquals(0, restored[0].sortOrder)
        assertEquals("Empty", restored[1].name)
        assertEquals(null, restored[1].colorArgb)
        assertEquals(1, restored[1].sortOrder)
    }
}
