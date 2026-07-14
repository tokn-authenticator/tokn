package me.diamondforge.tokn.audit.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.diamondforge.tokn.audit.AuditDatabase
import me.diamondforge.tokn.audit.AuditLogDao
import me.diamondforge.tokn.audit.AuditLogger
import me.diamondforge.tokn.audit.AuditLoggerImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuditModule {

    @Provides
    @Singleton
    fun provideAuditDatabase(@ApplicationContext context: Context): AuditDatabase =
        Room.databaseBuilder(context, AuditDatabase::class.java, "audit_log_database").build()

    @Provides
    fun provideAuditLogDao(db: AuditDatabase): AuditLogDao = db.auditLogDao()
}

@Module
@InstallIn(SingletonComponent::class)
interface AuditBindsModule {
    @Binds
    @Singleton
    fun bindAuditLogger(impl: AuditLoggerImpl): AuditLogger
}
