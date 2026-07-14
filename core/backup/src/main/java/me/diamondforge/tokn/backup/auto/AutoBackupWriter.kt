package me.diamondforge.tokn.backup.auto

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class AutoBackupStrategy { SINGLE, ROTATING }

class AutoBackupPermissionException : Exception("No persisted write access to the backup location")

@Singleton
open class AutoBackupWriter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun strategyFor(uri: Uri): AutoBackupStrategy =
        if (DocumentsContract.isTreeUri(uri)) AutoBackupStrategy.ROTATING
        else AutoBackupStrategy.SINGLE

    fun hasPersistedPermission(uri: Uri): Boolean {
        val target = uri.toString()
        return context.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == target && it.isWritePermission
        }
    }

    open fun write(
        uri: Uri,
        bytes: ByteArray,
        encrypted: Boolean,
        versionsToKeep: Int,
        timestamp: Long
    ) {
        if (!hasPersistedPermission(uri)) throw AutoBackupPermissionException()
        when (strategyFor(uri)) {
            AutoBackupStrategy.SINGLE -> writeSingle(uri, bytes)
            AutoBackupStrategy.ROTATING -> writeRotating(
                uri,
                bytes,
                encrypted,
                versionsToKeep,
                timestamp
            )
        }
    }

    private fun writeSingle(uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: error("Cannot open output stream")
    }

    private fun writeRotating(
        treeUri: Uri,
        bytes: ByteArray,
        encrypted: Boolean,
        versionsToKeep: Int,
        timestamp: Long,
    ) {
        val dir =
            DocumentFile.fromTreeUri(context, treeUri) ?: throw AutoBackupPermissionException()
        if (!dir.canWrite()) throw AutoBackupPermissionException()
        val name = backupFileName(encrypted, timestamp)
        dir.findFile(name)?.delete()
        val file = dir.createFile(MIME, name) ?: error("Cannot create backup file")
        context.contentResolver.openOutputStream(file.uri)?.use { it.write(bytes) }
            ?: error("Cannot open output stream")
        enforceVersioning(dir, versionsToKeep)
    }

    private fun enforceVersioning(dir: DocumentFile, versionsToKeep: Int) {
        val byName = dir.listFiles().mapNotNull { file -> file.name?.let { it to file } }
        filesToPrune(byName.map { it.first }, versionsToKeep).forEach { name ->
            byName.firstOrNull { it.first == name }?.second?.delete()
        }
    }

    companion object {
        const val MIME = "application/octet-stream"
        const val PERSIST_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

private const val PREFIX = "tokn-backup-"
private val TIMESTAMP_REGEX = Regex("""tokn-backup-(\d{8}-\d{6})\.(?:enc\.kv|json)""")

internal fun backupFileName(encrypted: Boolean, timestamp: Long): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestamp))
    return PREFIX + stamp + if (encrypted) ".enc.kv" else ".json"
}

internal fun parseBackupTimestamp(name: String): Long? {
    val match = TIMESTAMP_REGEX.matchEntire(name) ?: return null
    return runCatching {
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply { isLenient = false }
            .parse(match.groupValues[1])?.time
    }.getOrNull()
}

internal fun filesToPrune(names: List<String>, versionsToKeep: Int): List<String> {
    if (versionsToKeep <= 0) return emptyList()
    val dated = names.mapNotNull { name -> parseBackupTimestamp(name)?.let { it to name } }
        .sortedBy { it.first }
    val excess = dated.size - versionsToKeep
    return if (excess > 0) dated.take(excess).map { it.second } else emptyList()
}
