package me.diamondforge.tokn.domain.usecase

import kotlinx.coroutines.flow.first
import me.diamondforge.tokn.domain.model.Group
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.repository.AccountRepository

/**
 * Inserts incoming accounts whose normalized secret isn't already stored. The same
 * account-identity-is-the-secret rule applies to backup imports, sync transfers, and
 * Google Authenticator migration scans; keeping the logic here means a fix to the
 * dedup rule lands in one place.
 */
class ImportAccountsUseCase(private val repository: AccountRepository) {

    data class Summary(val imported: Int, val skipped: Int) {
        val found: Int get() = imported + skipped
    }

    suspend operator fun invoke(
        incoming: List<OtpAccount>,
        declaredGroups: List<Group> = emptyList(),
    ): Summary {
        val existing = repository.getAccounts().first()
            .mapTo(HashSet()) { it.secret.normalize() }
        var imported = 0
        var skipped = 0
        for (account in incoming) {
            val normalized = account.secret.normalize()
            if (normalized in existing) {
                skipped++
                continue
            }
            repository.addAccount(account.copy(id = 0))
            existing.add(normalized)
            imported++
        }
        declaredGroups.forEach { repository.createGroup(it.name, it.colorArgb) }
        if (declaredGroups.isNotEmpty()) {
            repository.reorderGroups(declaredGroups.sortedBy { it.sortOrder }.map { it.name })
        }
        return Summary(imported = imported, skipped = skipped)
    }

    private fun String.normalize(): String =
        replace(" ", "").replace("-", "").uppercase()
}
