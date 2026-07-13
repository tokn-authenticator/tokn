package me.diamondforge.tokn.backup

import android.content.Context
import android.net.Uri
import me.diamondforge.tokn.security.EncryptedPayload
import me.diamondforge.tokn.security.EncryptionManager
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedBackupManager @Inject constructor(
    private val encryptionManager: EncryptionManager,
) {
    fun exportToUri(context: Context, uri: Uri, plainJson: String, password: String) {
        val payload = encryptionManager.encrypt(plainJson.toByteArray(Charsets.UTF_8), password)
        val wrapper = JSONObject().apply { payload.writeTo(this) }
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(wrapper.toString().toByteArray(Charsets.UTF_8))
        } ?: error("Cannot open output stream")
    }

    fun importFromUri(context: Context, uri: Uri, password: String): String {
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes()
        } ?: error("Cannot open input stream")
        return decryptBytes(raw, password)
    }

    fun decryptBytes(raw: ByteArray, password: String): String {
        val wrapper = JSONObject(raw.toString(Charsets.UTF_8))
        val payload = EncryptedPayload.fromJson(wrapper)
        return encryptionManager.decrypt(payload, password).toString(Charsets.UTF_8)
    }
}
