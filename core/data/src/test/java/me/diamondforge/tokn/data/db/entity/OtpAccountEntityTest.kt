package me.diamondforge.tokn.data.db.entity

import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpAccountEntityTest {

    @Test
    fun `round trips every field through entity and back`() {
        val account = OtpAccount(
            id = 7,
            issuer = "ACME",
            accountName = "alice",
            secret = "JBSWY3DPEHPK3PXP",
            algorithm = OtpAlgorithm.SHA256,
            digits = 8,
            period = 60,
            counter = 12,
            type = OtpType.HOTP,
            sortOrder = 3,
            groups = listOf("Work", "VIP"),
            customIconBytes = byteArrayOf(1, 2, 3),
            iconPackId = "pack",
            iconPackFile = "icon.png",
            usageCount = 5,
            lastUsedAt = 1_700_000_000_000L,
        )

        assertEquals(account, account.toEntity().toDomain())
    }

    @Test
    fun `empty groups serialize to a null column`() {
        val entity = OtpAccount(issuer = "i", accountName = "n", secret = "s").toEntity()
        assertNull(entity.groupsJson)
    }

    @Test
    fun `null group column decodes to an empty list`() {
        val entity = baseEntity(groupsJson = null)
        assertEquals(emptyList<String>(), entity.toDomain().groups)
    }

    @Test
    fun `groups containing json metacharacters survive the round trip`() {
        val groups = listOf("a\"b", "c\\d", "öäü", "with space")
        val account = OtpAccount(issuer = "i", accountName = "n", secret = "s", groups = groups)
        assertEquals(groups, account.toEntity().toDomain().groups)
    }

    @Test
    fun `a non-json group column falls back to a single group`() {
        val entity = baseEntity(groupsJson = "LegacyGroup")
        assertEquals(listOf("LegacyGroup"), entity.toDomain().groups)
    }

    private fun baseEntity(groupsJson: String?) = OtpAccountEntity(
        id = 1,
        issuer = "i",
        accountName = "n",
        secret = "s",
        algorithm = "SHA1",
        digits = 6,
        period = 30,
        counter = 0,
        type = "TOTP",
        sortOrder = 0,
        groupsJson = groupsJson,
    )
}
