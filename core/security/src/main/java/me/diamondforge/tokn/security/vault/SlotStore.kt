package me.diamondforge.tokn.security.vault

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

class SlotStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isInitialized(): Boolean = prefs.contains(KEY_SLOTS)

    fun load(): MutableList<Slot> {
        val raw = prefs.getString(KEY_SLOTS, null) ?: return mutableListOf()
        val arr = JSONArray(raw)
        return (0 until arr.length())
            .map { Slot.fromJson(arr.getJSONObject(it)) }
            .toMutableList()
    }

    fun save(slots: List<Slot>) {
        val arr = JSONArray()
        slots.forEach { arr.put(it.toJson()) }
        prefs.edit(commit = true) { putString(KEY_SLOTS, arr.toString()) }
    }

    var legacyPasswordPresent: Boolean
        get() = prefs.getBoolean(KEY_LEGACY_PW, false)
        set(value) = prefs.edit(commit = true) { putBoolean(KEY_LEGACY_PW, value) }

    companion object {
        private const val PREFS = "vault_slots_prefs"
        private const val KEY_SLOTS = "vault_slots"
        private const val KEY_LEGACY_PW = "legacy_password_present"
    }
}
