package me.diamondforge.tokn.domain.model

class OtpAccount(
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
    val customIconBytes: ByteArray? = null,
    val iconPackId: String? = null,
    val iconPackFile: String? = null,
) {
    fun copy(
        id: Long = this.id,
        issuer: String = this.issuer,
        accountName: String = this.accountName,
        secret: String = this.secret,
        algorithm: OtpAlgorithm = this.algorithm,
        digits: Int = this.digits,
        period: Int = this.period,
        counter: Long = this.counter,
        type: OtpType = this.type,
        sortOrder: Int = this.sortOrder,
        group: String? = this.group,
        customIconBytes: ByteArray? = this.customIconBytes,
        iconPackId: String? = this.iconPackId,
        iconPackFile: String? = this.iconPackFile,
    ): OtpAccount = OtpAccount(
        id, issuer, accountName, secret, algorithm, digits, period, counter,
        type, sortOrder, group, customIconBytes, iconPackId, iconPackFile,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OtpAccount) return false
        return id == other.id &&
                issuer == other.issuer &&
                accountName == other.accountName &&
                secret == other.secret &&
                algorithm == other.algorithm &&
                digits == other.digits &&
                period == other.period &&
                counter == other.counter &&
                type == other.type &&
                sortOrder == other.sortOrder &&
                group == other.group &&
                customIconBytes.contentEqualsOrBothNull(other.customIconBytes) &&
                iconPackId == other.iconPackId &&
                iconPackFile == other.iconPackFile
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + issuer.hashCode()
        result = 31 * result + accountName.hashCode()
        result = 31 * result + secret.hashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + digits
        result = 31 * result + period
        result = 31 * result + counter.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + sortOrder
        result = 31 * result + (group?.hashCode() ?: 0)
        result = 31 * result + (customIconBytes?.contentHashCode() ?: 0)
        result = 31 * result + (iconPackId?.hashCode() ?: 0)
        result = 31 * result + (iconPackFile?.hashCode() ?: 0)
        return result
    }
}

private fun ByteArray?.contentEqualsOrBothNull(other: ByteArray?): Boolean =
    if (this == null && other == null) true
    else if (this == null || other == null) false
    else this.contentEquals(other)

enum class OtpAlgorithm { SHA1, SHA256, SHA512 }

enum class OtpType { TOTP, HOTP }
