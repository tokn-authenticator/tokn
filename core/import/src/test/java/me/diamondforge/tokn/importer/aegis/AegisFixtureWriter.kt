package me.diamondforge.tokn.importer.aegis

import android.util.Base64
import me.diamondforge.tokn.importer.crypto.ScryptAesGcm
import org.json.JSONArray
import org.json.JSONObject

/**
 * Test-only helper that builds an Aegis-shaped vault file from a plain database JSON and a
 * password. Mirrors the encrypted format consumed by [AegisImporter] so we can round-trip
 * without needing pre-baked binary fixtures.
 */
internal object AegisFixtureWriter {
    private const val SCRYPT_N = 1 shl 5      // tiny params: tests must stay fast
    private const val SCRYPT_R = 8
    private const val SCRYPT_P = 1

    fun plain(db: JSONObject): String =
        JSONObject().apply {
            put("version", 1)
            put("header", JSONObject().apply {
                put("slots", JSONObject.NULL)
                put("params", JSONObject.NULL)
            })
            put("db", db)
        }.toString()

    fun encrypted(db: JSONObject, password: String, deterministicSeed: Int = 1): String {
        val rng = java.util.Random(deterministicSeed.toLong())
        val slotSalt = ByteArray(32).also(rng::nextBytes)
        val masterKey = ByteArray(32).also(rng::nextBytes)
        val keyNonce = ByteArray(12).also(rng::nextBytes)
        val dbNonce = ByteArray(12).also(rng::nextBytes)

        val derived = ScryptAesGcm.deriveKey(password.toByteArray(Charsets.UTF_8), slotSalt, SCRYPT_N, SCRYPT_R, SCRYPT_P)
        val (wrappedKey, wrappedKeyTag) = ScryptAesGcm.encrypt(derived, keyNonce, masterKey)
        val (dbCipher, dbTag) = ScryptAesGcm.encrypt(masterKey, dbNonce, db.toString().toByteArray(Charsets.UTF_8))

        val slot = JSONObject().apply {
            put("type", 1)
            put("uuid", "00000000-0000-0000-0000-000000000001")
            put("key", slotSalt.toHex())   // not actually used as key — placeholder
            put("key_params", JSONObject().apply {
                put("nonce", keyNonce.toHex())
                put("tag", wrappedKeyTag.toHex())
            })
            put("n", SCRYPT_N)
            put("r", SCRYPT_R)
            put("p", SCRYPT_P)
            put("salt", slotSalt.toHex())
        }
        // The slot's "key" field carries the *wrapped* master key, not the salt.
        slot.put("key", wrappedKey.toHex())

        return JSONObject().apply {
            put("version", 1)
            put("header", JSONObject().apply {
                put("slots", JSONArray().put(slot))
                put("params", JSONObject().apply {
                    put("nonce", dbNonce.toHex())
                    put("tag", dbTag.toHex())
                })
            })
            put("db", Base64.encodeToString(dbCipher, Base64.NO_WRAP))
        }.toString()
    }

    /** Same shape as a real Aegis vault that uses only biometric (no password) slots. */
    fun encryptedHardwareOnly(db: JSONObject): String {
        val rng = java.util.Random(0)
        val dbNonce = ByteArray(12).also(rng::nextBytes)
        // We need a valid header structure but no password slot.
        return JSONObject().apply {
            put("version", 1)
            put("header", JSONObject().apply {
                put("slots", JSONArray().put(JSONObject().apply {
                    put("type", 2) // biometric — not 1
                    put("uuid", "00000000-0000-0000-0000-000000000002")
                }))
                put("params", JSONObject().apply {
                    put("nonce", dbNonce.toHex())
                    put("tag", ByteArray(16).toHex())
                })
            })
            put("db", Base64.encodeToString(ByteArray(16), Base64.NO_WRAP))
        }.toString()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
