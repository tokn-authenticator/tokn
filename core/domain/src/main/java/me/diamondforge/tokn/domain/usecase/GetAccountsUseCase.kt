package me.diamondforge.tokn.domain.usecase

import kotlinx.coroutines.flow.Flow
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.repository.AccountRepository

class GetAccountsUseCase(private val repository: AccountRepository) {
    operator fun invoke(): Flow<List<OtpAccount>> = repository.getAccounts()
}
