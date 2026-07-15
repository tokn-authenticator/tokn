package me.diamondforge.tokn.domain.usecase

import me.diamondforge.tokn.domain.repository.AccountRepository

class SetGroupColorUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(name: String, colorArgb: Int?) =
        repository.setGroupColor(name, colorArgb)
}
