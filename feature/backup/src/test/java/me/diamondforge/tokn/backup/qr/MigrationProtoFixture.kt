package me.diamondforge.tokn.backup.qr

import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Minimal protobuf writer for synthesising Google Authenticator `otpauth-migration://`
 * URIs in tests. Mirrors the fixture writer in :core:import but lives here so :feature:backup
 * tests don't depend on another module's test source set.
 */
internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    private fun varint(value: Long): ProtoWriter = apply {
        var v = value
        while (true) {
            if ((v and 0x7FL.inv()) == 0L) {
                out.write(v.toInt() and 0x7F)
                return@apply
            }
            out.write((v.toInt() and 0x7F) or 0x80)
            v = v ushr 7
        }
    }

    private fun tag(field: Int, wireType: Int): ProtoWriter =
        varint(((field shl 3) or wireType).toLong())

    fun varintField(field: Int, value: Long): ProtoWriter = apply { tag(field, 0).varint(value) }

    fun bytesField(field: Int, value: ByteArray): ProtoWriter = apply {
        tag(field, 2).varint(value.size.toLong())
        out.write(value)
    }

    fun stringField(field: Int, value: String): ProtoWriter =
        bytesField(field, value.toByteArray(Charsets.UTF_8))

    fun messageField(field: Int, body: ByteArray): ProtoWriter = bytesField(field, body)

    fun toByteArray(): ByteArray = out.toByteArray()
}

internal data class MigrationEntry(
    val secret: ByteArray,
    val name: String,
    val issuer: String,
)

internal object MigrationFixture {
    fun buildUri(
        entries: List<MigrationEntry>,
        batchIndex: Int = 0,
        batchSize: Int = 1,
        batchId: Int = 1,
    ): String {
        val outer = ProtoWriter()
        for (e in entries) {
            val inner = ProtoWriter()
                .bytesField(1, e.secret)
                .stringField(2, e.name)
                .stringField(3, e.issuer)
                .varintField(4, 1)   // SHA1
                .varintField(5, 1)   // SIX digits
                .varintField(6, 2)   // TOTP
                .varintField(7, 0)
                .toByteArray()
            outer.messageField(1, inner)
        }
        outer.varintField(2, 1)
            .varintField(3, batchSize.toLong())
            .varintField(4, batchIndex.toLong())
            .varintField(5, batchId.toLong())
        val data = Base64.encodeToString(outer.toByteArray(), Base64.NO_WRAP)
        return "otpauth-migration://offline?data=" + Uri.encode(data)
    }
}
