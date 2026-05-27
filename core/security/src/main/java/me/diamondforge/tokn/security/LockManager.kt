package me.diamondforge.tokn.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockManager @Inject constructor() {
    // null = auth check not yet completed, true = locked, false = unlocked
    private val _isLocked = MutableStateFlow<Boolean?>(null)
    val isLocked: StateFlow<Boolean?> = _isLocked.asStateFlow()

    @Volatile
    private var backgroundedAt: Long = -1L

    // Holds the absolute deadline (millis since epoch) until which the next
    // foreground transition should be suppressed. The TTL guards against
    // stranded flags: if the suppression was set but the user never returned
    // (Activity destroyed, OS killed the process), the flag auto-expires
    // instead of silently bypassing the lock on the next lifecycle.
    @Volatile
    private var suppressUntil: Long = -1L

    /** Call before launching any system UI (file picker, share sheet, etc.) */
    fun suppressNextForeground() {
        suppressUntil = System.currentTimeMillis() + SUPPRESS_TTL_MS
    }

    fun onAppBackground() {
        backgroundedAt = System.currentTimeMillis()
    }

    fun onAppForeground(timeoutSeconds: Int) {
        if (backgroundedAt < 0) return
        val now = System.currentTimeMillis()
        val suppressed = suppressUntil > 0 && now < suppressUntil
        suppressUntil = -1L
        if (suppressed) {
            backgroundedAt = -1L
            return
        }
        val elapsed = (now - backgroundedAt) / 1000
        if (timeoutSeconds == 0 || elapsed >= timeoutSeconds) {
            _isLocked.value = true
        }
    }

    fun unlock() {
        _isLocked.value = false
        backgroundedAt = -1L
    }

    fun lock() {
        _isLocked.value = true
    }

    companion object {
        // Long enough for a slow picker / share sheet round trip, short enough
        // that a stranded flag can't bypass the next "real" foreground.
        private const val SUPPRESS_TTL_MS = 2 * 60 * 1000L
    }
}
