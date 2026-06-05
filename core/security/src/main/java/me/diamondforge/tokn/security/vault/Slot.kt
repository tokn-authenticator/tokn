package me.diamondforge.tokn.security.vault

import android.util.Base64
import org.json.JSONObject

sealed class Slot {
    abstract val uuid: String
    abstract fun toJson(): JSONObject

    companion object {
        const val TYPE_KEYSTORE = "keystore"
        const val TYPE_PASSWORD = "password"

        fun fromJson(obj: JSONObject): Slot = when (val type = obj.getString("type")) {
            TYPE_KEYSTORE -> KeystoreSlot.fromJson(obj)
            TYPE_PASSWORD -> PasswordSlot.fromJson(obj)
            else -> throw IllegalArgumentException("unknown slot type: $type")
        }
    }
}

class KeystoreSlot(
    override val uuid: String,
    val requiresAuth: Boolean,
    val wrappedKey: String,
) : Slot() {
    override fun toJson(): JSONObject = JSONObject().apply {
        put("type", TYPE_KEYSTORE)
        put("uuid", uuid)
        put("auth", requiresAuth)
        put("key", wrappedKey)
    }

    companion object {
        fun fromJson(o: JSONObject) = KeystoreSlot(
            uuid = o.getString("uuid"),
            requiresAuth = o.getBoolean("auth"),
            wrappedKey = o.getString("key"),
        )
    }
}

class PasswordSlot(
    override val uuid: String,
    val salt: ByteArray,
    val memoryKib: Int,
    val iterations: Int,
    val parallelism: Int,
    val wrappedKey: ByteArray,
) : Slot() {
    override fun toJson(): JSONObject = JSONObject().apply {
        put("type", TYPE_PASSWORD)
        put("uuid", uuid)
        put("salt", b64(salt))
        put("m", memoryKib)
        put("t", iterations)
        put("p", parallelism)
        put("key", b64(wrappedKey))
    }

    companion object {
        fun fromJson(o: JSONObject) = PasswordSlot(
            uuid = o.getString("uuid"),
            salt = d64(o.getString("salt")),
            memoryKib = o.getInt("m"),
            iterations = o.getInt("t"),
            parallelism = o.getInt("p"),
            wrappedKey = d64(o.getString("key")),
        )
    }
}

private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
private fun d64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
