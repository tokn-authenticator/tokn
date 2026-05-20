package me.diamondforge.tokn.sync

import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncPayloadTest {

    @Test
    fun `roundtrip preserves all fields including optional group`() {
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
                group = "Work",
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
                group = null,
            ),
        )

        val json = SyncPayload.serialize(accounts)
        val restored = SyncPayload.deserialize(json)

        assertEquals(2, restored.size)
        // First account: every non-id field preserved.
        val a = restored[0]
        assertEquals("GitHub", a.issuer)
        assertEquals("alice@example.com", a.accountName)
        assertEquals("JBSWY3DPEHPK3PXP", a.secret)
        assertEquals(OtpAlgorithm.SHA256, a.algorithm)
        assertEquals(8, a.digits)
        assertEquals(60, a.period)
        assertEquals(OtpType.TOTP, a.type)
        assertEquals(2, a.sortOrder)
        assertEquals("Work", a.group)
        // Second: null group + HOTP counter preserved.
        val b = restored[1]
        assertEquals(OtpType.HOTP, b.type)
        assertEquals(7L, b.counter)
        assertNull(b.group)
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
            {"version":1,"accounts":[{"secret":"JBSWY3DPEHPK3PXP"}]}
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
        assertNull(a.group)
    }

    @Test
    fun `empty list roundtrips`() {
        val json = SyncPayload.serialize(emptyList())
        assertEquals(emptyList<OtpAccount>(), SyncPayload.deserialize(json))
    }

    @Test
    fun `blank group is treated as null`() {
        val raw = """{"version":1,"accounts":[{"secret":"X","group":""}]}"""
        assertNull(SyncPayload.deserialize(raw)[0].group)
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
}
