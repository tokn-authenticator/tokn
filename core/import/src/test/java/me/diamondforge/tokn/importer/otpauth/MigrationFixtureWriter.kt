package me.diamondforge.tokn.importer.otpauth

import android.util.Base64

internal data class MigrationEntry(
    val secret: ByteArray,
    val name: String,
    val issuer: String,
    val algorithm: Int = 1,  // SHA1
    val digits: Int = 1,     // SIX
    val type: Int = 2,       // TOTP
    val counter: Long = 0,
)

internal object MigrationFixtureWriter {
    fun buildUri(entries: List<MigrationEntry>, batchIndex: Int = 0, batchSize: Int = 1, batchId: Int = 1): String {
        val outer = ProtoWriter()
        for (e in entries) {
            val inner = ProtoWriter()
                .bytesField(1, e.secret)
                .stringField(2, e.name)
                .stringField(3, e.issuer)
                .varintField(4, e.algorithm.toLong())
                .varintField(5, e.digits.toLong())
                .varintField(6, e.type.toLong())
                .varintField(7, e.counter)
                .toByteArray()
            outer.messageField(1, inner)
        }
        outer.varintField(2, 1)
            .varintField(3, batchSize.toLong())
            .varintField(4, batchIndex.toLong())
            .varintField(5, batchId.toLong())
        val data = Base64.encodeToString(outer.toByteArray(), Base64.NO_WRAP)
        return "otpauth-migration://offline?data=" + android.net.Uri.encode(data)
    }
}
