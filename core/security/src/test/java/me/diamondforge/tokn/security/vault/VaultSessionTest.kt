package me.diamondforge.tokn.security.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSessionTest {

    @Test
    fun `locked by default`() {
        val session = VaultSession()
        assertFalse(session.isUnlocked)
        assertEquals(VaultState.LOCKED, session.state.value)
    }

    @Test
    fun `unlock exposes the key and flips the state`() {
        val session = VaultSession()
        session.unlock(byteArrayOf(1, 2, 3))
        assertTrue(session.isUnlocked)
        assertEquals(VaultState.UNLOCKED, session.state.value)
        assertArrayEquals(byteArrayOf(1, 2, 3), session.requireKey())
    }

    @Test
    fun `unlock copies the caller's key so later mutation cannot leak in`() {
        val session = VaultSession()
        val input = byteArrayOf(1, 2, 3)
        session.unlock(input)
        input[0] = 99
        assertArrayEquals(byteArrayOf(1, 2, 3), session.requireKey())
    }

    @Test
    fun `requireKey hands back a copy so callers cannot mutate the stored key`() {
        val session = VaultSession()
        session.unlock(byteArrayOf(1, 2, 3))
        session.requireKey()[0] = 99
        assertArrayEquals(byteArrayOf(1, 2, 3), session.requireKey())
    }

    @Test
    fun `lock clears the key and requireKey then throws`() {
        val session = VaultSession()
        session.unlock(byteArrayOf(1, 2, 3))
        session.lock()
        assertFalse(session.isUnlocked)
        assertEquals(VaultState.LOCKED, session.state.value)
        assertThrows(IllegalStateException::class.java) { session.requireKey() }
    }
}
