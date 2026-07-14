package me.diamondforge.tokn.security.vault

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SlotTest {

    @Test
    fun `keystore slot round trips through json`() {
        val slot = KeystoreSlot(uuid = "u-1", requiresAuth = true, wrappedKey = "wrapped==")
        val restored = Slot.fromJson(slot.toJson()) as KeystoreSlot

        assertEquals("u-1", restored.uuid)
        assertTrue(restored.requiresAuth)
        assertEquals("wrapped==", restored.wrappedKey)
    }

    @Test
    fun `password slot round trips through json`() {
        val slot = PasswordSlot(
            uuid = "u-2",
            salt = byteArrayOf(1, 2, 3, 4),
            memoryKib = 47_104,
            iterations = 3,
            parallelism = 2,
            wrappedKey = byteArrayOf(9, 8, 7),
        )
        val restored = Slot.fromJson(slot.toJson()) as PasswordSlot

        assertEquals("u-2", restored.uuid)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), restored.salt)
        assertEquals(47_104, restored.memoryKib)
        assertEquals(3, restored.iterations)
        assertEquals(2, restored.parallelism)
        assertArrayEquals(byteArrayOf(9, 8, 7), restored.wrappedKey)
    }

    @Test
    fun `unknown slot type is rejected`() {
        val obj = JSONObject().put("type", "bogus")
        assertThrows(IllegalArgumentException::class.java) { Slot.fromJson(obj) }
    }
}
