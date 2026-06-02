package me.diamondforge.tokn.sync

import org.json.JSONObject

/**
 * Wire/protocol version. Bump when **any** of the following changes in a way
 * that breaks compatibility with older clients:
 *  - JPAKE / SecureChannel framing
 *  - SyncPayload JSON schema (incompatible; additive optional fields don't count)
 *  - QR wrapper format
 *
 * The protocol version is the *only* thing that gates "can these two devices
 * sync." `app`/`build` are surfaced to the user purely so they know which side
 * to update when versions don't match.
 */
object SyncProtocol {
    const val VERSION = 2

    data class Hello(
        val protocol: Int,
        val app: String,
        val build: Long,
    )

    fun makeHello(app: String, build: Long): ByteArray =
        JSONObject().apply {
            put("protocol", VERSION)
            put("app", app)
            put("build", build)
        }.toString().toByteArray(Charsets.UTF_8)

    fun parseHello(bytes: ByteArray): Hello {
        val o = JSONObject(String(bytes, Charsets.UTF_8))
        return Hello(
            protocol = o.getInt("protocol"),
            app = o.optString("app").ifBlank { "?" },
            build = o.optLong("build", 0L),
        )
    }

    fun isCompatible(peer: Hello): Boolean = peer.protocol == VERSION
}
