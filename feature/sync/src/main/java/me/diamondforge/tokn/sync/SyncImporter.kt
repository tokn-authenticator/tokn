package me.diamondforge.tokn.sync

import kotlinx.coroutines.flow.first
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.usecase.AddAccountUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncImporter @Inject constructor(
    private val getAccountsUseCase: GetAccountsUseCase,
    private val addAccountUseCase: AddAccountUseCase,
) {
    data class Summary(val imported: Int, val skipped: Int)

    /**
     * Adds every incoming account whose secret is not already present locally.
     * The secret is treated as the unique identity of an entry — same secret
     * means same TOTP/HOTP code, so duplicating it would just be noise.
     */
    suspend fun import(incoming: List<OtpAccount>): Summary {
        val existingSecrets = getAccountsUseCase().first().map { it.secret.normalize() }.toHashSet()
        var imported = 0
        var skipped = 0
        for (account in incoming) {
            val normalized = account.secret.normalize()
            if (normalized in existingSecrets) {
                skipped++
                continue
            }
            addAccountUseCase(account.copy(id = 0))
            existingSecrets.add(normalized)
            imported++
        }
        return Summary(imported = imported, skipped = skipped)
    }

    private fun String.normalize(): String =
        replace(" ", "").replace("-", "").uppercase()
}
