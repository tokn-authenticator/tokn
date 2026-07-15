package me.diamondforge.tokn.domain.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import me.diamondforge.tokn.domain.model.Group
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

    private val groupsState = MutableStateFlow<List<Group>>(emptyList())
    private var nextGroupId: Long = 1

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
        renameGroupEntity(source, target)
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
        groupsState.value =
            groupsState.value.filterNot { it.name.equals(target, ignoreCase = true) }
        return changed
    }

    override fun listGroups(): Flow<List<Group>> {
        materializeGroupsFromAccounts()
        return groupsState.map { list -> list.sortedBy { it.sortOrder } }
    }

    override suspend fun createGroup(name: String, colorArgb: Int?): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return 0
        val existing = groupsState.value.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) return existing.id
        val id = nextGroupId++
        groupsState.value = groupsState.value +
                Group(
                    id = id,
                    name = trimmed,
                    colorArgb = colorArgb,
                    sortOrder = groupsState.value.size
                )
        return id
    }

    override suspend fun setGroupColor(name: String, colorArgb: Int?) {
        val trimmed = name.trim()
        groupsState.value = groupsState.value.map {
            if (it.name.equals(trimmed, ignoreCase = true)) it.copy(colorArgb = colorArgb) else it
        }
    }

    override suspend fun reorderGroups(orderedNames: List<String>) {
        if (orderedNames.isEmpty()) return
        val byLower = groupsState.value.associateBy { it.name.lowercase() }
        val order = orderedNames.withIndex()
            .mapNotNull { (index, name) -> byLower[name.lowercase()]?.let { it.id to index } }
            .toMap()
        groupsState.value = groupsState.value.map { row ->
            order[row.id]?.let { row.copy(sortOrder = it) } ?: row
        }
    }

    override suspend fun addAccountsToGroups(ids: Set<Long>, groupNames: Set<String>): Int {
        if (ids.isEmpty() || groupNames.isEmpty()) return 0
        val trimmedNames = groupNames.map { it.trim() }.filter { it.isNotEmpty() }
        if (trimmedNames.isEmpty()) return 0
        trimmedNames.forEach { createGroup(it) }
        var count = 0
        state.value = state.value.map { account ->
            if (account.id !in ids) return@map account
            val currentLower = account.groups.mapTo(mutableSetOf()) { it.lowercase() }
            val toAdd = trimmedNames.filter { it.lowercase() !in currentLower }
            if (toAdd.isEmpty()) return@map account
            count++
            account.copy(groups = account.groups + toAdd)
        }
        return count
    }

    private fun renameGroupEntity(from: String, to: String) {
        val all = groupsState.value
        val sourceRow = all.firstOrNull { it.name.equals(from, ignoreCase = true) }
        val targetRow = all.firstOrNull { it.name.equals(to, ignoreCase = true) }
        groupsState.value = when {
            sourceRow == null -> all
            targetRow != null && targetRow.id != sourceRow.id ->
                all.filterNot { it.id == sourceRow.id }

            else -> all.map { if (it.id == sourceRow.id) it.copy(name = to) else it }
        }
    }

    private fun materializeGroupsFromAccounts() {
        val existingLower = groupsState.value.mapTo(mutableSetOf()) { it.name.lowercase() }
        val accountGroupNames = state.value.flatMap { it.groups }.distinctBy { it.lowercase() }
        var nextOrder = groupsState.value.size
        val toAdd = mutableListOf<Group>()
        accountGroupNames.forEach { name ->
            if (existingLower.add(name.lowercase())) {
                toAdd += Group(id = nextGroupId++, name = name, sortOrder = nextOrder++)
            }
        }
        if (toAdd.isNotEmpty()) groupsState.value = groupsState.value + toAdd
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
