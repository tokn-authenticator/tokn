package me.diamondforge.tokn.domain.usecase

import me.diamondforge.tokn.domain.repository.AccountRepository

class ReorderGroupsUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(orderedNames: List<String>) =
        repository.reorderGroups(orderedNames)
}
