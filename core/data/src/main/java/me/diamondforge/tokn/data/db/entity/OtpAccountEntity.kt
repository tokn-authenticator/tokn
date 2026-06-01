package me.diamondforge.tokn.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.model.OtpAlgorithm
import me.diamondforge.tokn.domain.model.OtpType
import org.json.JSONArray
import org.json.JSONException

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
    // Kotlin-side name is `groupsJson` but the column kept its original
    // `group` name to avoid an unsupported SQLite column rename. Holds a
    // JSON array of strings, or NULL when the account belongs to no group.
    @ColumnInfo(name = "group") val groupsJson: String? = null,
    @ColumnInfo(name = "custom_icon_blob") val customIconBlob: ByteArray? = null,
    @ColumnInfo(name = "icon_pack_id") val iconPackId: String? = null,
    @ColumnInfo(name = "icon_pack_file") val iconPackFile: String? = null,
    @ColumnInfo(name = "usage_count", defaultValue = "0") val usageCount: Int = 0,
    @ColumnInfo(name = "last_used_at", defaultValue = "0") val lastUsedAt: Long = 0L,
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
    groups = decodeGroups(groupsJson),
    customIconBytes = customIconBlob,
    iconPackId = iconPackId,
    iconPackFile = iconPackFile,
    usageCount = usageCount,
    lastUsedAt = lastUsedAt,
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
    groupsJson = encodeGroups(groups),
    customIconBlob = customIconBytes,
    iconPackId = iconPackId,
    iconPackFile = iconPackFile,
    usageCount = usageCount,
    lastUsedAt = lastUsedAt,
)

private fun encodeGroups(groups: List<String>): String? {
    if (groups.isEmpty()) return null
    val arr = JSONArray()
    groups.forEach { arr.put(it) }
    return arr.toString()
}

private fun decodeGroups(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        buildList(arr.length()) {
            for (i in 0 until arr.length()) add(arr.getString(i))
        }
    } catch (_: JSONException) {
        // A pre-migration row could in principle slip through if a future
        // migration is botched; treat the raw string as a single group so
        // we never lose data silently.
        listOf(raw)
    }
}
