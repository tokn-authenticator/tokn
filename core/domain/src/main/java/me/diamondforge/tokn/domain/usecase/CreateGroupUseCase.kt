package me.diamondforge.tokn.domain.usecase

import me.diamondforge.tokn.domain.repository.AccountRepository

class CreateGroupUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(name: String, colorArgb: Int? = null): Long =
        repository.createGroup(name, colorArgb)
}
