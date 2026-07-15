package me.diamondforge.tokn.domain.usecase

import me.diamondforge.tokn.domain.repository.AccountRepository

class AddAccountsToGroupsUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(ids: Set<Long>, groupNames: Set<String>): Int =
        repository.addAccountsToGroups(ids, groupNames)
}
