package me.diamondforge.tokn.backup.qr

import me.diamondforge.tokn.importer.otpauth.MigrationBatchInfo

/**
 * Pure state for a Google Authenticator migration scan session. Held independently from
 * the camera so it can be unit-tested without Robolectric.
 *
 * Scanning multiple batches works incrementally: every accepted URI is appended to
 * [uris], and the per-batch metadata is recorded in [batchesSeen]. When the user moves
 * mid-scan to a different vault, the [batchId] changes and the scanner must surface a
 * warning before discarding what was collected so far.
 */
data class MigrationScanState(
    val uris: List<String> = emptyList(),
    val batchesSeen: Set<Int> = emptySet(),
    val expectedBatchCount: Int = 0,
    val batchId: Int? = null,
) {
    val isComplete: Boolean
        get() = expectedBatchCount > 0 && batchesSeen.size >= expectedBatchCount

    val isEmpty: Boolean get() = uris.isEmpty()
}

sealed interface ScanEvent {
    data class Accepted(val info: MigrationBatchInfo) : ScanEvent
    data object Duplicate : ScanEvent
    data object NotMigration : ScanEvent
    data class CrossVault(val incoming: MigrationBatchInfo) : ScanEvent
}

class MigrationScanSession(private val peek: (String) -> MigrationBatchInfo?) {
    var state: MigrationScanState = MigrationScanState()
        private set

    /**
     * Process a raw decoded QR string. Returns what happened so the caller (UI) can react.
     * [Accepted] commits the URI to state; everything else leaves [state] untouched.
     */
    fun onScanned(uri: String): ScanEvent {
        val info = peek(uri) ?: return ScanEvent.NotMigration

        val currentId = state.batchId
        if (currentId != null && info.batchId != currentId) {
            return ScanEvent.CrossVault(info)
        }
        if (info.batchIndex in state.batchesSeen) {
            return ScanEvent.Duplicate
        }

        state = state.copy(
            uris = state.uris + uri,
            batchesSeen = state.batchesSeen + info.batchIndex,
            expectedBatchCount = info.batchSize,
            batchId = info.batchId,
        )
        return ScanEvent.Accepted(info)
    }

    /**
     * Confirm a [ScanEvent.CrossVault] situation: replace the session with whatever the
     * incoming URI is part of. UI is expected to call this after the user explicitly
     * acknowledges the discard.
     */
    fun replaceWith(uri: String): ScanEvent {
        state = MigrationScanState()
        return onScanned(uri)
    }

    fun reset() {
        state = MigrationScanState()
    }

    fun joinedPayload(): String = state.uris.joinToString("\n")
}
