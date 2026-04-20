package me.diamondforge.tokn.domain.model

data class OtpAccount(
    val id: Long = 0,
    val issuer: String,
    val accountName: String,
    val secret: String,
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
    val counter: Long = 0,
    val type: OtpType = OtpType.TOTP,
    val sortOrder: Int = 0,
    val group: String? = null,
)

enum class OtpAlgorithm { SHA1, SHA256, SHA512 }

enum class OtpType { TOTP, HOTP }
