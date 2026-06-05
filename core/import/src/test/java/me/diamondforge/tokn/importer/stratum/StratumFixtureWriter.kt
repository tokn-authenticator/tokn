package me.diamondforge.tokn.importer.stratum

import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object StratumFixtureWriter {

    fun plain(backup: JSONObject): ByteArray =
        backup.toString().toByteArray(Charsets.UTF_8)

    /** Builds a Legacy-encrypted Stratum file (PBKDF2-SHA1 + AES-CBC). */
    fun legacy(backup: JSONObject, password: String, seed: Int = 1): ByteArray {
        val rng = java.util.Random(seed.toLong())
        val salt = ByteArray(20).also(rng::nextBytes)
        val iv = ByteArray(16).also(rng::nextBytes)

        val spec = PBEKeySpec(password.toCharArray(), salt, 64_000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(spec)
            .encoded

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(backup.toString().toByteArray(Charsets.UTF_8))

        return "AuthenticatorPro".toByteArray(Charsets.US_ASCII) + salt + iv + ciphertext
    }

    fun backup(vararg authenticators: JSONObject): JSONObject = JSONObject().apply {
        put("Authenticators", JSONArray().also { arr -> authenticators.forEach(arr::put) })
        put("Categories", JSONArray())
        put("AuthenticatorCategories", JSONArray())
        put("CustomIcons", JSONArray())
    }

    fun totp(
        issuer: String = "ACME",
        username: String = "alice@example.com",
        secret: String = "JBSWY3DPEHPK3PXP",
        algorithm: Int = 0,
        digits: Int = 6,
        period: Int = 30,
    ): JSONObject = JSONObject().apply {
        put("Type", 2)
        put("Issuer", issuer)
        put("Username", username)
        put("Secret", secret)
        put("Algorithm", algorithm)
        put("Digits", digits)
        put("Period", period)
        put("Counter", 0)
        put("Icon", issuer.lowercase())
        put("Pin", JSONObject.NULL)
        put("Ranking", 0)
        put("CopyCount", 0)
    }

    fun hotp(
        issuer: String = "ACME",
        username: String = "bob@example.com",
        secret: String = "KRUGS4Y=",
        counter: Long = 7,
    ): JSONObject = JSONObject().apply {
        put("Type", 1)
        put("Issuer", issuer)
        put("Username", username)
        put("Secret", secret)
        put("Algorithm", 0)
        put("Digits", 6)
        put("Period", 30)
        put("Counter", counter)
        put("Icon", issuer.lowercase())
        put("Pin", JSONObject.NULL)
        put("Ranking", 0)
        put("CopyCount", 0)
    }

    fun unsupported(type: Int): JSONObject = JSONObject().apply {
        put("Type", type)
        put("Issuer", "Steam")
        put("Username", "user")
        put("Secret", "STEAMKEY")
        put("Algorithm", 0)
        put("Digits", 5)
        put("Period", 30)
        put("Counter", 0)
        put("Icon", "steam")
        put("Pin", JSONObject.NULL)
        put("Ranking", 0)
        put("CopyCount", 0)
    }
}

private operator fun ByteArray.plus(other: ByteArray): ByteArray {
    val result = ByteArray(size + other.size)
    copyInto(result)
    other.copyInto(result, size)
    return result
}
