package me.diamondforge.tokn.audit

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AuditLogEntry::class],
    version = 1,
    exportSchema = false,
)
abstract class AuditDatabase : RoomDatabase() {
    abstract fun auditLogDao(): AuditLogDao
}
