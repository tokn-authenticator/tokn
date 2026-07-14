package me.diamondforge.tokn.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.diamondforge.tokn.audit.AuditEventType
import me.diamondforge.tokn.audit.AuditLogger
import me.diamondforge.tokn.audit.NoopAuditLogger
import me.diamondforge.tokn.data.db.AppDatabase
import me.diamondforge.tokn.data.db.dao.OtpAccountDao
import me.diamondforge.tokn.data.db.entity.toDomain
import me.diamondforge.tokn.data.db.entity.toEntity
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.TrashedAccount
import me.diamondforge.tokn.domain.repository.AccountRepository
import javax.inject.Inject

class AccountRepositoryImpl @Inject constructor(
    private val dao: OtpAccountDao,
    private val db: AppDatabase,
    private val auditLogger: AuditLogger = NoopAuditLogger,
) : AccountRepository {

    override fun getAccounts(): Flow<List<OtpAccount>> =
        dao.getAllAccounts().map { list -> list.map { it.toDomain() } }

    override suspend fun addAccount(account: OtpAccount): Long {
        val id = dao.insert(account.toEntity())
        auditLogger.log(AuditEventType.ITEM_ADDED, targetId = id)
        return id
    }

    override suspend fun updateAccount(account: OtpAccount) {
        dao.update(account.toEntity())
        auditLogger.log(AuditEventType.ITEM_EDITED, targetId = account.id)
    }

    override suspend fun deleteAccount(id: Long) {
        dao.softDeleteByIds(setOf(id), System.currentTimeMillis())
        auditLogger.log(AuditEventType.ITEM_DELETED, targetId = id)
    }

    override suspend fun deleteAccounts(ids: Set<Long>) {
        if (ids.isEmpty()) return
        dao.softDeleteByIds(ids, System.currentTimeMillis())
        ids.forEach { auditLogger.log(AuditEventType.ITEM_DELETED, targetId = it) }
    }

    override fun getTrashedAccounts(): Flow<List<TrashedAccount>> =
        dao.getTrashedAccounts().map { list ->
            list.map { TrashedAccount(it.id, it.issuer, it.accountName, it.deletedAt) }
        }

    override suspend fun restoreAccounts(ids: Set<Long>) {
        if (ids.isEmpty()) return
        dao.restoreByIds(ids)
        ids.forEach { auditLogger.log(AuditEventType.ITEM_RESTORED, targetId = it) }
    }

    override suspend fun purgeAccounts(ids: Set<Long>) {
        if (ids.isEmpty()) return
        dao.deleteByIds(ids)
        ids.forEach { auditLogger.log(AuditEventType.ITEM_PURGED, targetId = it) }
    }

    override suspend fun purgeExpiredTrash(cutoff: Long): Int {
        val count = dao.purgeExpired(cutoff)
        if (count > 0) {
            auditLogger.log(AuditEventType.ITEM_PURGED_AUTO, detail = count.toString())
        }
        return count
    }

    override suspend fun reorderAccounts(accounts: List<OtpAccount>) {
        accounts.forEachIndexed { index, account ->
            dao.updateSortOrder(account.id, index)
        }
    }

    override suspend fun incrementCounter(id: Long) =
        dao.incrementCounter(id)

    override suspend fun recordUsage(id: Long, timestamp: Long) =
        dao.recordUsage(id, timestamp)

    override suspend fun getAccountById(id: Long): OtpAccount? =
        dao.getAccountById(id)?.toDomain()

    override suspend fun getAccountsByIds(ids: Set<Long>): List<OtpAccount> {
        if (ids.isEmpty()) return emptyList()
        return dao.getAccountsByIds(ids).map { it.toDomain() }
    }

    override suspend fun renameGroup(from: String, to: String): Int {
        val target = to.trim()
        val source = from.trim()
        if (target.isEmpty() || source.isEmpty()) return 0
        val changed = db.withTransaction {
            var count = 0
            dao.getAllAccountsOnce().forEach { entity ->
                val account = entity.toDomain()
                val updated = renameInList(account.groups, source, target) ?: return@forEach
                dao.update(account.copy(groups = updated).toEntity())
                count++
            }
            count
        }
        if (changed > 0) {
            auditLogger.log(AuditEventType.GROUP_RENAMED, detail = "$source -> $target")
        }
        return changed
    }

    override suspend fun removeGroup(name: String): Int {
        val target = name.trim()
        if (target.isEmpty()) return 0
        val changed = db.withTransaction {
            var count = 0
            dao.getAllAccountsOnce().forEach { entity ->
                val account = entity.toDomain()
                if (account.groups.none { it.equals(target, ignoreCase = true) }) return@forEach
                val pruned = account.groups.filterNot { it.equals(target, ignoreCase = true) }
                dao.update(account.copy(groups = pruned).toEntity())
                count++
            }
            count
        }
        if (changed > 0) {
            auditLogger.log(AuditEventType.GROUP_REMOVED, detail = target)
        }
        return changed
    }
}

/**
 * Returns the new groups list with [from] replaced by [to] (case-insensitive
 * match, deduped case-insensitively against the rest of the list), or `null`
 * when the input list contains no occurrence of [from] and therefore needs
 * no write.
 */
private fun renameInList(groups: List<String>, from: String, to: String): List<String>? {
    val hasMatch = groups.any { it.equals(from, ignoreCase = true) }
    if (!hasMatch) return null
    val seen = mutableSetOf<String>()
    val result = mutableListOf<String>()
    for (g in groups) {
        val next = if (g.equals(from, ignoreCase = true)) to else g
        if (seen.add(next.lowercase())) result += next
    }
    return if (result == groups) null else result
}
