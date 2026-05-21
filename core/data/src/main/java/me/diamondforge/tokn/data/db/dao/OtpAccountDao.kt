package me.diamondforge.tokn.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import me.diamondforge.tokn.data.db.entity.OtpAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OtpAccountDao {

    @Query("SELECT * FROM otp_accounts ORDER BY sortOrder ASC")
    fun getAllAccounts(): Flow<List<OtpAccountEntity>>

    @Query("SELECT * FROM otp_accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: Long): OtpAccountEntity?

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

    @Query("UPDATE otp_accounts SET counter = counter + 1 WHERE id = :id")
    suspend fun incrementCounter(id: Long)

    @Query("UPDATE otp_accounts SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)
}
