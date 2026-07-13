package me.diamondforge.tokn.domain.usecase

import me.diamondforge.tokn.domain.repository.AccountRepository

class GetTrashedAccountsUseCase(private val repository: AccountRepository) {
    operator fun invoke() = repository.getTrashedAccounts()
}
