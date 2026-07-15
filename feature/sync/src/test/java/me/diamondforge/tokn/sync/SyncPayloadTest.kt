package me.diamondforge.tokn.sync

import me.diamondforge.tokn.domain.model.Group
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPayloadTest {

    @Test
    fun `roundtrip preserves all fields including groups`() {
        val accounts = listOf(
            OtpAccount(
                id = 42, // id is intentionally dropped on the wire
                issuer = "GitHub",
                accountName = "alice@example.com",
                secret = "JBSWY3DPEHPK3PXP",
                algorithm = OtpAlgorithm.SHA256,
                digits = 8,
                period = 60,
                counter = 0,
                type = OtpType.TOTP,
                sortOrder = 2,
                groups = listOf("Work", "Critical"),
            ),
            OtpAccount(
                issuer = "Bank",
                accountName = "12345",
                secret = "MFRGGZDFMZTWQ2LK",
                algorithm = OtpAlgorithm.SHA1,
                digits = 6,
                period = 30,
                counter = 7,
                type = OtpType.HOTP,
                sortOrder = 0,
                groups = emptyList(),
            ),
        )

        val json = SyncPayload.serialize(accounts)
        val restored = SyncPayload.deserialize(json)

        assertEquals(2, restored.size)
        val a = restored[0]
        assertEquals("GitHub", a.issuer)
        assertEquals("alice@example.com", a.accountName)
        assertEquals("JBSWY3DPEHPK3PXP", a.secret)
        assertEquals(OtpAlgorithm.SHA256, a.algorithm)
        assertEquals(8, a.digits)
        assertEquals(60, a.period)
        assertEquals(OtpType.TOTP, a.type)
        assertEquals(2, a.sortOrder)
        assertEquals(listOf("Work", "Critical"), a.groups)

        val b = restored[1]
        assertEquals(OtpType.HOTP, b.type)
        assertEquals(7L, b.counter)
        assertTrue(b.groups.isEmpty())
    }

    @Test
    fun `serialize emits version and accounts wrapper`() {
        val json = JSONObject(SyncPayload.serialize(emptyList()))
        assertEquals(SyncPayload.VERSION, json.getInt("version"))
        assertEquals(0, json.getJSONArray("accounts").length())
    }

    @Test
    fun `deserialize fills defaults for omitted optional fields`() {
        val raw = """
            {"version":2,"accounts":[{"secret":"JBSWY3DPEHPK3PXP"}]}
        """.trimIndent()
        val list = SyncPayload.deserialize(raw)
        assertEquals(1, list.size)
        val a = list[0]
        assertEquals("", a.issuer)
        assertEquals("", a.accountName)
        assertEquals("JBSWY3DPEHPK3PXP", a.secret)
        assertEquals(OtpAlgorithm.SHA1, a.algorithm)
        assertEquals(6, a.digits)
        assertEquals(30, a.period)
        assertEquals(0L, a.counter)
        assertEquals(OtpType.TOTP, a.type)
        assertEquals(0, a.sortOrder)
        assertTrue(a.groups.isEmpty())
    }

    @Test
    fun `empty list roundtrips`() {
        val json = SyncPayload.serialize(emptyList())
        assertEquals(emptyList<OtpAccount>(), SyncPayload.deserialize(json))
    }

    @Test
    fun `empty groups array is treated as no groups`() {
        val raw = """{"version":2,"accounts":[{"secret":"X","groups":[]}]}"""
        assertTrue(SyncPayload.deserialize(raw)[0].groups.isEmpty())
    }

    @Test
    fun `groups field is omitted from emitted JSON when account has none`() {
        // Keeps the wire as small as the pre-multi-group format for the
        // common case where an account is ungrouped.
        val account = OtpAccount(
            issuer = "Ungrouped",
            accountName = "user",
            secret = "JBSWY3DPEHPK3PXP",
        )
        val emitted = JSONObject(SyncPayload.serialize(listOf(account)))
            .getJSONArray("accounts")
            .getJSONObject(0)
        assertTrue(!emitted.has("groups"))
        assertTrue(!emitted.has("group"))
    }

    @Test
    fun `all algorithm enum values roundtrip`() {
        for (algo in OtpAlgorithm.values()) {
            val account = OtpAccount(
                issuer = "i", accountName = "n", secret = "JBSWY3DPEHPK3PXP",
                algorithm = algo,
            )
            val restored = SyncPayload.deserialize(SyncPayload.serialize(listOf(account)))[0]
            assertEquals(algo, restored.algorithm)
        }
    }

    @Test
    fun `usageCount and lastUsedAt are not transferred`() {
        // Per-device behavioural data should not roam across the wire.
        // The receiving device builds up its own usage profile.
        val account = OtpAccount(
            issuer = "GitHub",
            accountName = "alice",
            secret = "JBSWY3DPEHPK3PXP",
            usageCount = 42,
            lastUsedAt = 1_700_000_000_000L,
        )
        val json = JSONObject(SyncPayload.serialize(listOf(account)))
        val emitted = json.getJSONArray("accounts").getJSONObject(0)
        assert(!emitted.has("usageCount")) { "usageCount leaked into sync payload" }
        assert(!emitted.has("lastUsedAt")) { "lastUsedAt leaked into sync payload" }

        // And a roundtrip lands at defaults.
        val restored = SyncPayload.deserialize(json.toString())[0]
        assertEquals(0, restored.usageCount)
        assertEquals(0L, restored.lastUsedAt)
    }

    @Test
    fun `old payload without declaredGroups still restores accounts and yields no declared groups`() {
        val raw = """
            {"version":1,"accounts":[{"issuer":"GitHub","accountName":"alice","secret":"X","groups":["Work"]}]}
        """.trimIndent()
        val restored = SyncPayload.deserialize(raw)
        assertEquals(listOf("Work"), restored.first().groups)
        assertTrue(SyncPayload.readDeclaredGroups(raw).isEmpty())
    }

    @Test
    fun `declaredGroups round trips name color and order`() {
        val groups = listOf(
            Group(name = "Work", colorArgb = 0xFF1155CC.toInt(), sortOrder = 0),
            Group(name = "Empty", colorArgb = null, sortOrder = 1),
        )
        val json = SyncPayload.serialize(emptyList(), groups)
        val restored = SyncPayload.readDeclaredGroups(json)
        assertEquals(2, restored.size)
        assertEquals("Work", restored[0].name)
        assertEquals(0xFF1155CC.toInt(), restored[0].colorArgb)
        assertEquals(0, restored[0].sortOrder)
        assertEquals("Empty", restored[1].name)
        assertEquals(null, restored[1].colorArgb)
        assertEquals(1, restored[1].sortOrder)
    }
}
