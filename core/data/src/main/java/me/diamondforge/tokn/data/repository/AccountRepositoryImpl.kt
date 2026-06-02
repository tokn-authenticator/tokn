package me.diamondforge.tokn.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.diamondforge.tokn.data.db.AppDatabase
import me.diamondforge.tokn.data.db.dao.OtpAccountDao
import me.diamondforge.tokn.data.db.entity.toDomain
import me.diamondforge.tokn.data.db.entity.toEntity
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.repository.AccountRepository
import javax.inject.Inject

class AccountRepositoryImpl @Inject constructor(
    private val dao: OtpAccountDao,
    private val db: AppDatabase,
) : AccountRepository {

    override fun getAccounts(): Flow<List<OtpAccount>> =
        dao.getAllAccounts().map { list -> list.map { it.toDomain() } }

    override suspend fun addAccount(account: OtpAccount): Long =
        dao.insert(account.toEntity())

    override suspend fun updateAccount(account: OtpAccount) =
        dao.update(account.toEntity())

    override suspend fun deleteAccount(id: Long) =
        dao.deleteById(id)

    override suspend fun deleteAccounts(ids: Set<Long>) {
        if (ids.isNotEmpty()) dao.deleteByIds(ids)
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

    override suspend fun renameGroup(from: String, to: String): Int {
        val target = to.trim()
        val source = from.trim()
        if (target.isEmpty() || source.isEmpty()) return 0
        return db.withTransaction {
            var changed = 0
            dao.getAllAccountsOnce().forEach { entity ->
                val account = entity.toDomain()
                val updated = renameInList(account.groups, source, target) ?: return@forEach
                dao.update(account.copy(groups = updated).toEntity())
                changed++
            }
            changed
        }
    }

    override suspend fun removeGroup(name: String): Int {
        val target = name.trim()
        if (target.isEmpty()) return 0
        return db.withTransaction {
            var changed = 0
            dao.getAllAccountsOnce().forEach { entity ->
                val account = entity.toDomain()
                if (account.groups.none { it.equals(target, ignoreCase = true) }) return@forEach
                val pruned = account.groups.filterNot { it.equals(target, ignoreCase = true) }
                dao.update(account.copy(groups = pruned).toEntity())
                changed++
            }
            changed
        }
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
