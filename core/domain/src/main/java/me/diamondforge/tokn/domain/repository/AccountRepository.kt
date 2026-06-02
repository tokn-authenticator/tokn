package me.diamondforge.tokn.domain.repository

import kotlinx.coroutines.flow.Flow
import me.diamondforge.tokn.domain.model.OtpAccount

interface AccountRepository {
    fun getAccounts(): Flow<List<OtpAccount>>
    suspend fun addAccount(account: OtpAccount): Long
    suspend fun updateAccount(account: OtpAccount)
    suspend fun deleteAccount(id: Long)
    suspend fun deleteAccounts(ids: Set<Long>)
    suspend fun reorderAccounts(accounts: List<OtpAccount>)
    suspend fun incrementCounter(id: Long)
    suspend fun recordUsage(id: Long, timestamp: Long)
    suspend fun getAccountById(id: Long): OtpAccount?

    /**
     * Replace every case-insensitive occurrence of [from] in any account's
     * groups list with [to] (trimmed). When the new name already exists on
     * an account, the result is deduplicated case-insensitively so the same
     * group never appears twice on one account. Returns the number of
     * accounts whose stored groups list actually changed.
     */
    suspend fun renameGroup(from: String, to: String): Int

    /**
     * Remove [name] (case-insensitive) from every account's groups list.
     * Accounts whose last group is removed end up with an empty list, which
     * the storage layer collapses back to NULL. Returns the number of
     * accounts that had the group.
     */
    suspend fun removeGroup(name: String): Int
}
