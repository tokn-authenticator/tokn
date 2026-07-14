package me.diamondforge.tokn.audit

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_log_entries",
    indices = [Index("timestamp"), Index("category")],
)
data class AuditLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val category: String,
    val timestamp: Long,
    val targetId: Long? = null,
    val detail: String? = null,
)
