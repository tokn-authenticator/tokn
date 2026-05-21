package me.diamondforge.tokn.importer

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImporterRegistry @Inject constructor(
    private val importers: Set<@JvmSuppressWildcards ExternalImporter>,
) {
    fun all(): List<ExternalImporter> = importers.sortedBy { it.displayName.lowercase() }

    fun byId(id: String): ExternalImporter? = importers.firstOrNull { it.id == id }

    /** Returns the first importer that claims the file, or null. */
    fun detect(raw: ByteArray): ExternalImporter? = importers.firstOrNull { it.canHandle(raw) }
}
