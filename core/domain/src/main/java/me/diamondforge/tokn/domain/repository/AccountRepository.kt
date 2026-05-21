package me.diamondforge.tokn.domain.repository

import me.diamondforge.tokn.domain.model.OtpAccount
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun getAccounts(): Flow<List<OtpAccount>>
    suspend fun addAccount(account: OtpAccount): Long
    suspend fun updateAccount(account: OtpAccount)
    suspend fun deleteAccount(id: Long)
    suspend fun deleteAccounts(ids: Set<Long>)
    suspend fun reorderAccounts(accounts: List<OtpAccount>)
    suspend fun incrementCounter(id: Long)
    suspend fun getAccountById(id: Long): OtpAccount?
}
