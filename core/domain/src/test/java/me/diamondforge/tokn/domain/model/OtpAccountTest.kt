package me.diamondforge.tokn.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpAccountTest {

    private fun base(icon: ByteArray? = null) = OtpAccount(
        id = 1,
        issuer = "ACME",
        accountName = "alice",
        secret = "JBSWY3DPEHPK3PXP",
        customIconBytes = icon,
    )

    @Test
    fun `defaults match the TOTP common case`() {
        val acc = base()
        assertEquals(OtpAlgorithm.SHA1, acc.algorithm)
        assertEquals(6, acc.digits)
        assertEquals(30, acc.period)
        assertEquals(OtpType.TOTP, acc.type)
        assertTrue(acc.groups.isEmpty())
    }

    @Test
    fun `equal icon bytes compare equal`() {
        val a = base(byteArrayOf(1, 2, 3))
        val b = base(byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `differing icon bytes compare unequal`() {
        assertNotEquals(base(byteArrayOf(1, 2, 3)), base(byteArrayOf(9, 9, 9)))
    }

    @Test
    fun `one null icon and one set compare unequal`() {
        assertNotEquals(base(null), base(byteArrayOf(1)))
        assertNotEquals(base(byteArrayOf(1)), base(null))
    }

    @Test
    fun `both null icons compare equal`() {
        assertEquals(base(null), base(null))
    }

    @Test
    fun `copy changing one field leaves the rest intact`() {
        val original = base(byteArrayOf(7))
        val copy = original.copy(issuer = "Other")

        assertEquals("Other", copy.issuer)
        assertEquals(original.accountName, copy.accountName)
        assertEquals(original.secret, copy.secret)
        assertTrue(copy.customIconBytes.contentEquals(byteArrayOf(7)))
    }

    @Test
    fun `differing scalar field breaks equality`() {
        assertFalse(base().copy(digits = 8) == base())
    }
}
