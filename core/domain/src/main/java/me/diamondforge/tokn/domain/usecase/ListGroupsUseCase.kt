package me.diamondforge.tokn.domain.usecase

import kotlinx.coroutines.flow.Flow
import me.diamondforge.tokn.domain.model.Group
import me.diamondforge.tokn.domain.repository.AccountRepository

class ListGroupsUseCase(private val repository: AccountRepository) {
    operator fun invoke(): Flow<List<Group>> = repository.listGroups()
}
