package me.diamondforge.tokn.security

import me.diamondforge.tokn.audit.NoopAuditLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LockManagerTest {

    private fun newLockManager() = LockManager(NoopAuditLogger)

    @Test
    fun `starts in the not-yet-evaluated state`() {
        assertNull(newLockManager().isLocked.value)
    }

    @Test
    fun `foreground before any background does nothing`() {
        val lm = newLockManager()
        lm.onAppForeground(0)
        assertNull(lm.isLocked.value)
    }

    @Test
    fun `zero timeout locks on the next foreground`() {
        val lm = newLockManager()
        lm.onAppBackground()
        lm.onAppForeground(0)
        assertEquals(true, lm.isLocked.value)
    }

    @Test
    fun `a generous timeout leaves a freshly backgrounded app unlocked`() {
        val lm = newLockManager()
        lm.unlock()
        lm.onAppBackground()
        lm.onAppForeground(3600)
        assertEquals(false, lm.isLocked.value)
    }

    @Test
    fun `suppressed foreground skips the lock exactly once`() {
        val lm = newLockManager()
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
        val lm = newLockManager()
        lm.onAppBackground()
        lm.unlock()
        lm.onAppForeground(0)
        assertEquals(false, lm.isLocked.value)
    }

    @Test
    fun `lock and unlock drive the flow`() {
        val lm = newLockManager()
        lm.lock()
        assertEquals(true, lm.isLocked.value)
        lm.unlock()
        assertEquals(false, lm.isLocked.value)
    }
}
