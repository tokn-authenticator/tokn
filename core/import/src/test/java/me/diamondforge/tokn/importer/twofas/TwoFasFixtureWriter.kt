package me.diamondforge.tokn.importer.twofas

import android.util.Base64
import me.diamondforge.tokn.importer.crypto.Pbkdf2AesGcm
import org.json.JSONArray
import org.json.JSONObject

internal object TwoFasFixtureWriter {
    private const val ITER = 10_000
    private const val KEY_BITS = 256

    fun plain(services: JSONArray, schemaVersion: Int = 4): String =
        JSONObject().apply {
            put("schemaVersion", schemaVersion)
            put("appOrigin", "android")
            put("services", services)
            put("servicesEncrypted", "")
        }.toString()

    fun encrypted(services: JSONArray, password: String, seed: Int = 1): String {
        val rng = java.util.Random(seed.toLong())
        val salt = ByteArray(256).also(rng::nextBytes)
        val iv = ByteArray(12).also(rng::nextBytes)
        val key = Pbkdf2AesGcm.deriveKey(password.toCharArray(), salt, ITER, KEY_BITS)
        val cipher = Pbkdf2AesGcm.encrypt(key, iv, services.toString().toByteArray(Charsets.UTF_8))
        val blob = listOf(cipher, salt, iv)
            .joinToString(":") { Base64.encodeToString(it, Base64.NO_WRAP) }
        return JSONObject().apply {
            put("schemaVersion", 4)
            put("appOrigin", "android")
            put("services", JSONArray())
            put("servicesEncrypted", blob)
        }.toString()
    }
}
