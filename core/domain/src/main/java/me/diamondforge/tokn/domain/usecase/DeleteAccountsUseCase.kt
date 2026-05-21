package me.diamondforge.tokn.domain.usecase

import me.diamondforge.tokn.domain.repository.AccountRepository

class DeleteAccountsUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(ids: Set<Long>) = repository.deleteAccounts(ids)
}
