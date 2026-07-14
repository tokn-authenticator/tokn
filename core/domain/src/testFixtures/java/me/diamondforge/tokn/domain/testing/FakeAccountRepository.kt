package me.diamondforge.tokn.domain.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.TrashedAccount
import me.diamondforge.tokn.domain.repository.AccountRepository

/**
 * In-memory [AccountRepository] for unit tests. Mirrors the contract of the
 * real Room-backed implementation: ids auto-increment from 1, the Flow emits
 * after every mutation, and reorder rewrites sortOrder by list index.
 */
class FakeAccountRepository(
    initial: List<OtpAccount> = emptyList(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AccountRepository {

    private val state = MutableStateFlow(initial.toList())
    private val trash = MutableStateFlow<List<Pair<OtpAccount, Long>>>(emptyList())
    private var nextId: Long = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    val snapshot: List<OtpAccount> get() = state.value
    val trashSnapshot: List<OtpAccount> get() = trash.value.map { it.first }

    override fun getAccounts(): Flow<List<OtpAccount>> = state.asStateFlow()

    override suspend fun addAccount(account: OtpAccount): Long {
        val assigned =
            if (account.id == 0L) nextId++ else account.id.also { nextId = maxOf(nextId, it + 1) }
        state.value = state.value + account.copy(id = assigned)
        return assigned
    }

    override suspend fun updateAccount(account: OtpAccount) {
        state.value = state.value.map { if (it.id == account.id) account else it }
    }

    override suspend fun deleteAccount(id: Long) = deleteAccounts(setOf(id))

    override suspend fun deleteAccounts(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val now = clock()
        val moved = state.value.filter { it.id in ids }
        state.value = state.value.filterNot { it.id in ids }
        trash.value = trash.value + moved.map { it to now }
    }

    override fun getTrashedAccounts(): Flow<List<TrashedAccount>> = trash.map { list ->
        list.sortedByDescending { it.second }
            .map { (a, deletedAt) -> TrashedAccount(a.id, a.issuer, a.accountName, deletedAt) }
    }

    override suspend fun restoreAccounts(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val restored = trash.value.filter { it.first.id in ids }.map { it.first }
        trash.value = trash.value.filterNot { it.first.id in ids }
        state.value = state.value + restored
    }

    override suspend fun purgeAccounts(ids: Set<Long>) {
        if (ids.isEmpty()) return
        trash.value = trash.value.filterNot { it.first.id in ids }
    }

    override suspend fun purgeExpiredTrash(cutoff: Long): Int {
        val expired = trash.value.filter { it.second < cutoff }
        trash.value = trash.value - expired.toSet()
        return expired.size
    }

    override suspend fun reorderAccounts(accounts: List<OtpAccount>) {
        val byId = accounts.withIndex().associate { (i, a) -> a.id to i }
        state.value = state.value.map { acc ->
            byId[acc.id]?.let { acc.copy(sortOrder = it) } ?: acc
        }
    }

    override suspend fun incrementCounter(id: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(counter = it.counter + 1) else it }
    }

    override suspend fun recordUsage(id: Long, timestamp: Long) {
        state.value = state.value.map {
            if (it.id == id) it.copy(usageCount = it.usageCount + 1, lastUsedAt = timestamp) else it
        }
    }

    override suspend fun getAccountById(id: Long): OtpAccount? =
        state.value.firstOrNull { it.id == id }

    override suspend fun getAccountsByIds(ids: Set<Long>): List<OtpAccount> =
        state.value.filter { it.id in ids }

    override suspend fun renameGroup(from: String, to: String): Int {
        val target = to.trim()
        val source = from.trim()
        if (target.isEmpty() || source.isEmpty()) return 0
        var changed = 0
        state.value = state.value.map { account ->
            val updated = renameInList(account.groups, source, target) ?: return@map account
            changed++
            account.copy(groups = updated)
        }
        return changed
    }

    override suspend fun removeGroup(name: String): Int {
        val target = name.trim()
        if (target.isEmpty()) return 0
        var changed = 0
        state.value = state.value.map { account ->
            if (account.groups.none { it.equals(target, ignoreCase = true) }) return@map account
            changed++
            account.copy(groups = account.groups.filterNot { it.equals(target, ignoreCase = true) })
        }
        return changed
    }
}

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
