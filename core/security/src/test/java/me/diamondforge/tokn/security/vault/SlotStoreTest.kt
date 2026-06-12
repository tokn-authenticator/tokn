package me.diamondforge.tokn.security.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SlotStoreTest {

    private lateinit var context: Context
    private lateinit var store: SlotStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vault_slots_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        store = SlotStore(context)
    }

    @Test
    fun `uninitialized store loads nothing`() {
        assertFalse(store.isInitialized())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `saved slots survive a reload`() {
        store.save(
            listOf(
                KeystoreSlot(uuid = "k", requiresAuth = false, wrappedKey = "kc:abc"),
                PasswordSlot(
                    uuid = "p",
                    salt = byteArrayOf(5, 6),
                    memoryKib = 1024,
                    iterations = 2,
                    parallelism = 1,
                    wrappedKey = byteArrayOf(1, 1),
                ),
            ),
        )

        assertTrue(store.isInitialized())
        val reloaded = SlotStore(context).load()
        assertEquals(2, reloaded.size)

        val keystore = reloaded.filterIsInstance<KeystoreSlot>().single()
        assertEquals("kc:abc", keystore.wrappedKey)

        val password = reloaded.filterIsInstance<PasswordSlot>().single()
        assertArrayEquals(byteArrayOf(5, 6), password.salt)
        assertEquals(1024, password.memoryKib)
    }

    @Test
    fun `legacy password flag defaults to false and persists`() {
        assertFalse(store.legacyPasswordPresent)
        store.legacyPasswordPresent = true
        assertTrue(SlotStore(context).legacyPasswordPresent)
    }
}
