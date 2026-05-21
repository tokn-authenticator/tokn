package me.diamondforge.tokn.domain.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.repository.AccountRepository

/**
 * In-memory [AccountRepository] for unit tests. Mirrors the contract of the
 * real Room-backed implementation: ids auto-increment from 1, the Flow emits
 * after every mutation, and reorder rewrites sortOrder by list index.
 */
class FakeAccountRepository(
    initial: List<OtpAccount> = emptyList(),
) : AccountRepository {

    private val state = MutableStateFlow(initial.toList())
    private var nextId: Long = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    val snapshot: List<OtpAccount> get() = state.value

    override fun getAccounts(): Flow<List<OtpAccount>> = state.asStateFlow()

    override suspend fun addAccount(account: OtpAccount): Long {
        val assigned = if (account.id == 0L) nextId++ else account.id.also { nextId = maxOf(nextId, it + 1) }
        state.value = state.value + account.copy(id = assigned)
        return assigned
    }

    override suspend fun updateAccount(account: OtpAccount) {
        state.value = state.value.map { if (it.id == account.id) account else it }
    }

    override suspend fun deleteAccount(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun deleteAccounts(ids: Set<Long>) {
        if (ids.isEmpty()) return
        state.value = state.value.filterNot { it.id in ids }
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

    override suspend fun getAccountById(id: Long): OtpAccount? =
        state.value.firstOrNull { it.id == id }
}
