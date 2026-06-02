package me.diamondforge.tokn.domain.usecase

import me.diamondforge.tokn.domain.repository.AccountRepository

class DeleteGroupUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(name: String): Int = repository.removeGroup(name)
}
