package me.diamondforge.tokn.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType

@Entity(tableName = "otp_accounts")
data class OtpAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val issuer: String,
    val accountName: String,
    val secret: String,
    val algorithm: String,
    val digits: Int,
    val period: Int,
    val counter: Long,
    val type: String,
    val sortOrder: Int,
    val group: String? = null,
)

fun OtpAccountEntity.toDomain(): OtpAccount = OtpAccount(
    id = id,
    issuer = issuer,
    accountName = accountName,
    secret = secret,
    algorithm = OtpAlgorithm.valueOf(algorithm),
    digits = digits,
    period = period,
    counter = counter,
    type = OtpType.valueOf(type),
    sortOrder = sortOrder,
    group = group,
)

fun OtpAccount.toEntity(): OtpAccountEntity = OtpAccountEntity(
    id = id,
    issuer = issuer,
    accountName = accountName,
    secret = secret,
    algorithm = algorithm.name,
    digits = digits,
    period = period,
    counter = counter,
    type = type.name,
    sortOrder = sortOrder,
    group = group,
)
