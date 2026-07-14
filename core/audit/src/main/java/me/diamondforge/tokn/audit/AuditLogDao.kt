package me.diamondforge.tokn.audit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {

    @Insert
    suspend fun insert(entry: AuditLogEntry)

    @Query("SELECT * FROM audit_log_entries ORDER BY timestamp DESC LIMIT 5000")
    fun getAll(): Flow<List<AuditLogEntry>>

    @Query("DELETE FROM audit_log_entries WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM audit_log_entries")
    suspend fun clearAll()
}
