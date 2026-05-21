package me.diamondforge.tokn.importer.otpauth

import java.io.ByteArrayOutputStream

internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(value: Long): ProtoWriter = apply {
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

    fun tag(field: Int, wireType: Int): ProtoWriter = varint(((field shl 3) or wireType).toLong())

    fun varintField(field: Int, value: Long): ProtoWriter = apply {
        tag(field, 0).varint(value)
    }

    fun bytesField(field: Int, value: ByteArray): ProtoWriter = apply {
        tag(field, 2).varint(value.size.toLong())
        out.write(value)
    }

    fun stringField(field: Int, value: String): ProtoWriter =
        bytesField(field, value.toByteArray(Charsets.UTF_8))

    fun messageField(field: Int, body: ByteArray): ProtoWriter = bytesField(field, body)

    fun toByteArray(): ByteArray = out.toByteArray()
}
