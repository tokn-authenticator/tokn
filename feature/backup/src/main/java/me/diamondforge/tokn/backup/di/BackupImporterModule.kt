package me.diamondforge.tokn.backup.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import me.diamondforge.tokn.backup.ToknBackupImporter
import me.diamondforge.tokn.importer.ExternalImporter

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupImporterModule {
    @Binds
    @IntoSet
    abstract fun bindToknBackup(impl: ToknBackupImporter): ExternalImporter
}
