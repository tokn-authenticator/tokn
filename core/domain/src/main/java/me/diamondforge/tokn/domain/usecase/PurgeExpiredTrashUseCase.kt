package me.diamondforge.tokn.domain.usecase

import me.diamondforge.tokn.domain.repository.AccountRepository

class PurgeExpiredTrashUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(now: Long = System.currentTimeMillis()): Int =
        repository.purgeExpiredTrash(now - RETENTION_MS)

    companion object {
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
