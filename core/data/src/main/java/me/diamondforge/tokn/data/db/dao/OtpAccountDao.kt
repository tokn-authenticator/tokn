package me.diamondforge.tokn.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.diamondforge.tokn.data.db.entity.OtpAccountEntity

@Dao
interface OtpAccountDao {

    @Query("SELECT * FROM otp_accounts WHERE deleted_at = 0 ORDER BY sortOrder ASC")
    fun getAllAccounts(): Flow<List<OtpAccountEntity>>

    @Query("SELECT * FROM otp_accounts WHERE deleted_at = 0 ORDER BY sortOrder ASC")
    suspend fun getAllAccountsOnce(): List<OtpAccountEntity>

    @Query("SELECT * FROM otp_accounts WHERE id = :id AND deleted_at = 0 LIMIT 1")
    suspend fun getAccountById(id: Long): OtpAccountEntity?

    @Query("SELECT * FROM otp_accounts WHERE deleted_at != 0 ORDER BY deleted_at DESC")
    fun getTrashedAccounts(): Flow<List<OtpAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: OtpAccountEntity): Long

    @Update
    suspend fun update(account: OtpAccountEntity)

    @Delete
    suspend fun delete(account: OtpAccountEntity)

    @Query("DELETE FROM otp_accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM otp_accounts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: Set<Long>)

    @Query("UPDATE otp_accounts SET deleted_at = :timestamp WHERE id IN (:ids)")
    suspend fun softDeleteByIds(ids: Set<Long>, timestamp: Long)

    @Query("UPDATE otp_accounts SET deleted_at = 0 WHERE id IN (:ids)")
    suspend fun restoreByIds(ids: Set<Long>)

    @Query("DELETE FROM otp_accounts WHERE deleted_at != 0 AND deleted_at < :cutoff")
    suspend fun purgeExpired(cutoff: Long): Int

    @Query("UPDATE otp_accounts SET counter = counter + 1 WHERE id = :id")
    suspend fun incrementCounter(id: Long)

    @Query("UPDATE otp_accounts SET usage_count = usage_count + 1, last_used_at = :timestamp WHERE id = :id")
    suspend fun recordUsage(id: Long, timestamp: Long)

    @Query("UPDATE otp_accounts SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)
}
