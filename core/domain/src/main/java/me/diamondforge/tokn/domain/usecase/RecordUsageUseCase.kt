package me.diamondforge.tokn.domain.usecase

import me.diamondforge.tokn.domain.repository.AccountRepository

/**
 * Records that an account's code was viewed or copied. Drives the
 * "Most used" / "Recently used" sort modes. Dedup of rapid repeat events
 * (e.g. reveal-then-copy within seconds) is the caller's responsibility.
 */
class RecordUsageUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(id: Long, timestamp: Long) =
        repository.recordUsage(id, timestamp)
}
