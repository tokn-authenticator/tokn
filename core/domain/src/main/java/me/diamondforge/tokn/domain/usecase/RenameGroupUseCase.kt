package me.diamondforge.tokn.domain.usecase

import me.diamondforge.tokn.domain.repository.AccountRepository

class RenameGroupUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(from: String, to: String): Int =
        repository.renameGroup(from, to)
}
