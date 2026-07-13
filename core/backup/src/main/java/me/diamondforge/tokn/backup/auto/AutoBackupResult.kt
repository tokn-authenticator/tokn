package me.diamondforge.tokn.backup.auto

sealed interface AutoBackupResult {
    data object Success : AutoBackupResult
    data class Skipped(val reason: String) : AutoBackupResult
    data class Failure(val reason: String) : AutoBackupResult
}
