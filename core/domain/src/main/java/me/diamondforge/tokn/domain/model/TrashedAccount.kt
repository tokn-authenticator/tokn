package me.diamondforge.tokn.domain.model

data class TrashedAccount(
    val id: Long,
    val issuer: String,
    val accountName: String,
    val deletedAt: Long,
)
