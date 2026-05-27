package me.diamondforge.tokn.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.diamondforge.tokn.data.db.dao.OtpAccountDao
import me.diamondforge.tokn.data.db.entity.toDomain
import me.diamondforge.tokn.data.db.entity.toEntity
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.repository.AccountRepository
import javax.inject.Inject

class AccountRepositoryImpl @Inject constructor(
    private val dao: OtpAccountDao,
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

    override suspend fun getAccountById(id: Long): OtpAccount? =
        dao.getAccountById(id)?.toDomain()
}
