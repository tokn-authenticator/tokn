package me.diamondforge.tokn.domain.usecase

import me.diamondforge.tokn.domain.repository.AccountRepository

class GetAccountsByIdsUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(ids: Set<Long>) = repository.getAccountsByIds(ids)
}
