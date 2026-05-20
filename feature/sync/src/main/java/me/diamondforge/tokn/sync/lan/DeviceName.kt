package me.diamondforge.tokn.sync.lan

import android.os.Build
import java.security.SecureRandom

object DeviceName {
    /**
     * Builds a human-readable service name like "Pixel 7 #4823". The numeric
     * suffix avoids collisions when two devices of the same model are sending
     * at once and is short enough to be readable on a phone screen.
     */
    fun build(): String {
        val base = friendlyBase()
        val suffix = "%04d".format(SecureRandom().nextInt(10000))
        return "$base #$suffix"
    }

    private fun friendlyBase(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val combined = when {
            model.isBlank() -> manufacturer
            manufacturer.isBlank() -> model
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
        return combined.ifBlank { "Android device" }
    }
}
