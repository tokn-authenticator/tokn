package me.diamondforge.tokn.backup.qr

import me.diamondforge.tokn.importer.otpauth.MigrationBatchInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationScanStateTest {

    @Test
    fun `first scan establishes expected batch count and id`() {
        val session = sessionWith(mapOf("uri-a" to MigrationBatchInfo(0, 3, 99)))
        val event = session.onScanned("uri-a")
        assertTrue(event is ScanEvent.Accepted)
        assertEquals(3, session.state.expectedBatchCount)
        assertEquals(99, session.state.batchId)
        assertEquals(setOf(0), session.state.batchesSeen)
        assertFalse(session.state.isComplete)
    }

    @Test
    fun `scanning all batches completes the session`() {
        val session = sessionWith(
            mapOf(
                "a" to MigrationBatchInfo(0, 3, 1),
                "b" to MigrationBatchInfo(1, 3, 1),
                "c" to MigrationBatchInfo(2, 3, 1),
            ),
        )
        session.onScanned("a")
        session.onScanned("b")
        session.onScanned("c")
        assertTrue(session.state.isComplete)
        assertEquals(3, session.state.uris.size)
    }

    @Test
    fun `same batch index is dedup'd as Duplicate`() {
        val session = sessionWith(mapOf("a" to MigrationBatchInfo(0, 2, 1)))
        session.onScanned("a")
        val again = session.onScanned("a")
        assertEquals(ScanEvent.Duplicate, again)
        assertEquals(1, session.state.uris.size)
    }

    @Test
    fun `non-migration text is ignored`() {
        val session = sessionWith(emptyMap())
        val event = session.onScanned("otpauth://totp/X?secret=AAAA")
        assertEquals(ScanEvent.NotMigration, event)
        assertTrue(session.state.isEmpty)
    }

    @Test
    fun `mid-scan batch id change reports CrossVault and preserves state until replaceWith`() {
        val session = sessionWith(
            mapOf(
                "first" to MigrationBatchInfo(0, 2, 100),
                "other" to MigrationBatchInfo(0, 2, 200),
            ),
        )
        session.onScanned("first")
        val event = session.onScanned("other")
        assertTrue(event is ScanEvent.CrossVault)
        assertEquals(listOf("first"), session.state.uris)  // unchanged

        val replaced = session.replaceWith("other")
        assertTrue(replaced is ScanEvent.Accepted)
        assertEquals(listOf("other"), session.state.uris)
        assertEquals(200, session.state.batchId)
    }

    @Test
    fun `joinedPayload assembles all uris with newlines for parser consumption`() {
        val session = sessionWith(
            mapOf(
                "a" to MigrationBatchInfo(0, 2, 1),
                "b" to MigrationBatchInfo(1, 2, 1),
            ),
        )
        session.onScanned("a")
        session.onScanned("b")
        assertEquals("a\nb", session.joinedPayload())
    }

    @Test
    fun `reset returns to empty state`() {
        val session = sessionWith(mapOf("a" to MigrationBatchInfo(0, 1, 1)))
        session.onScanned("a")
        session.reset()
        assertTrue(session.state.isEmpty)
        assertEquals(null, session.state.batchId)
        assertEquals(0, session.state.expectedBatchCount)
    }

    private fun sessionWith(mapping: Map<String, MigrationBatchInfo>) =
        MigrationScanSession { mapping[it] }
}
