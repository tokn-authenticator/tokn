package me.diamondforge.tokn.data.icon

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IconPackManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val rootDir: File by lazy {
        File(context.filesDir, "icon-packs").apply { mkdirs() }
    }
    private val mutex = Mutex()

    private val _installed = MutableStateFlow<List<InstalledIconPack>>(emptyList())
    val installed: StateFlow<List<InstalledIconPack>> = _installed.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val packs = rootDir.listFiles { f -> f.isDirectory }?.mapNotNull { dir ->
            runCatching { loadPackFromDir(dir) }.getOrNull()
        } ?: emptyList()
        _installed.value = packs.sortedBy { it.pack.name.lowercase() }
    }

    fun getByUuid(uuid: String): InstalledIconPack? =
        _installed.value.firstOrNull { it.pack.uuid == uuid }

    fun iconFile(packUuid: String, filename: String): File? {
        val pack = getByUuid(packUuid) ?: return null
        val file = pack.fileFor(filename)
        return file.takeIf { it.exists() }
    }

    suspend fun install(uri: Uri): InstallResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val staging = File(rootDir, ".staging-${System.currentTimeMillis()}")
            staging.mkdirs()
            try {
                val packJsonBytes = extractZip(uri, staging)
                    ?: return@withLock InstallResult.MissingPackJson

                val pack = try {
                    IconPackParser.parse(String(packJsonBytes, Charsets.UTF_8))
                } catch (e: IconPackParseException) {
                    return@withLock InstallResult.InvalidPackJson(e.message ?: "parse error")
                }

                val target = File(rootDir, pack.uuid)
                if (target.exists()) target.deleteRecursively()
                if (!staging.renameTo(target)) {
                    staging.copyRecursively(target, overwrite = true)
                    staging.deleteRecursively()
                }

                refresh()
                val installed = getByUuid(pack.uuid)
                    ?: return@withLock InstallResult.Failed("Installed but not loaded")
                InstallResult.Success(installed)
            } catch (e: IOException) {
                staging.deleteRecursively()
                InstallResult.Failed(e.message ?: "I/O error")
            } catch (e: Exception) {
                staging.deleteRecursively()
                InstallResult.Failed(e.message ?: "Unexpected error")
            }
        }
    }

    suspend fun uninstall(uuid: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = File(rootDir, uuid)
            val ok = target.exists() && target.deleteRecursively()
            if (ok) refresh()
            ok
        }
    }

    private fun extractZip(uri: Uri, target: File): ByteArray? {
        var packJsonBytes: ByteArray? = null
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (entry.isDirectory) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    // Zip Slip protection: resolve path and ensure it stays under target.
                    val outFile = File(target, name).canonicalFile
                    val targetCanonical = target.canonicalPath + File.separator
                    if (!outFile.canonicalPath.startsWith(targetCanonical)) {
                        throw IOException("Zip entry escapes target: $name")
                    }
                    outFile.parentFile?.mkdirs()
                    val bytes = zis.readBytes()
                    outFile.writeBytes(bytes)
                    if (name.equals("pack.json", ignoreCase = true) ||
                        name.endsWith("/pack.json", ignoreCase = true)) {
                        packJsonBytes = bytes
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } ?: return null
        return packJsonBytes
    }

    private fun loadPackFromDir(dir: File): InstalledIconPack {
        val packJson = File(dir, "pack.json")
        require(packJson.exists()) { "pack.json missing in ${dir.name}" }
        val pack = IconPackParser.parse(packJson.readText(Charsets.UTF_8))
        return InstalledIconPack(pack, dir)
    }

    fun suggestionsAcross(issuer: String): List<Pair<InstalledIconPack, IconSuggestion>> {
        if (issuer.isBlank()) return emptyList()
        return _installed.value.flatMap { installed ->
            installed.suggestionsFor(issuer).map { installed to it }
        }
    }
}

sealed interface InstallResult {
    data class Success(val pack: InstalledIconPack) : InstallResult
    data object MissingPackJson : InstallResult
    data class InvalidPackJson(val reason: String) : InstallResult
    data class Failed(val reason: String) : InstallResult
}
