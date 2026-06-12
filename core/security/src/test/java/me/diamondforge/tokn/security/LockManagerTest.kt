package me.diamondforge.tokn.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LockManagerTest {

    @Test
    fun `starts in the not-yet-evaluated state`() {
        assertNull(LockManager().isLocked.value)
    }

    @Test
    fun `foreground before any background does nothing`() {
        val lm = LockManager()
        lm.onAppForeground(0)
        assertNull(lm.isLocked.value)
    }

    @Test
    fun `zero timeout locks on the next foreground`() {
        val lm = LockManager()
        lm.onAppBackground()
        lm.onAppForeground(0)
        assertEquals(true, lm.isLocked.value)
    }

    @Test
    fun `a generous timeout leaves a freshly backgrounded app unlocked`() {
        val lm = LockManager()
        lm.unlock()
        lm.onAppBackground()
        lm.onAppForeground(3600)
        assertEquals(false, lm.isLocked.value)
    }

    @Test
    fun `suppressed foreground skips the lock exactly once`() {
        val lm = LockManager()
        lm.unlock()

        lm.suppressNextForeground()
        lm.onAppBackground()
        lm.onAppForeground(0)
        assertEquals(false, lm.isLocked.value)

        lm.onAppBackground()
        lm.onAppForeground(0)
        assertEquals(true, lm.isLocked.value)
    }

    @Test
    fun `unlock clears the pending background so a stray foreground is a no-op`() {
        val lm = LockManager()
        lm.onAppBackground()
        lm.unlock()
        lm.onAppForeground(0)
        assertEquals(false, lm.isLocked.value)
    }

    @Test
    fun `lock and unlock drive the flow`() {
        val lm = LockManager()
        lm.lock()
        assertEquals(true, lm.isLocked.value)
        lm.unlock()
        assertEquals(false, lm.isLocked.value)
    }
}
