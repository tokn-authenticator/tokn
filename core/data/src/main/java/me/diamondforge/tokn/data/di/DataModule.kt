package me.diamondforge.tokn.data.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.diamondforge.tokn.data.db.AppDatabase
import me.diamondforge.tokn.data.db.dao.OtpAccountDao
import me.diamondforge.tokn.data.repository.AccountRepositoryImpl
import me.diamondforge.tokn.domain.repository.AccountRepository
import me.diamondforge.tokn.domain.usecase.AddAccountUseCase
import me.diamondforge.tokn.domain.usecase.DeleteAccountUseCase
import me.diamondforge.tokn.domain.usecase.DeleteAccountsUseCase
import me.diamondforge.tokn.domain.usecase.DeleteGroupUseCase
import me.diamondforge.tokn.domain.usecase.GenerateOtpUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountByIdUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsByIdsUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.GetTrashedAccountsUseCase
import me.diamondforge.tokn.domain.usecase.ImportAccountsUseCase
import me.diamondforge.tokn.domain.usecase.IncrementHotpCounterUseCase
import me.diamondforge.tokn.domain.usecase.PurgeAccountsUseCase
import me.diamondforge.tokn.domain.usecase.PurgeExpiredTrashUseCase
import me.diamondforge.tokn.domain.usecase.RecordUsageUseCase
import me.diamondforge.tokn.domain.usecase.RenameGroupUseCase
import me.diamondforge.tokn.domain.usecase.ReorderAccountsUseCase
import me.diamondforge.tokn.domain.usecase.RestoreAccountsUseCase
import me.diamondforge.tokn.domain.usecase.UpdateAccountUseCase
import me.diamondforge.tokn.security.vault.VaultSession
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        vaultSession: VaultSession,
    ): AppDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = vaultSession.requireKey()
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(context, AppDatabase::class.java, "otp_database")
            .openHelperFactory(factory)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
            )
            .build()
    }

    @Provides
    fun provideOtpAccountDao(db: AppDatabase): OtpAccountDao = db.otpAccountDao()

    @Provides
    fun provideGetAccountsUseCase(repo: AccountRepository) = GetAccountsUseCase(repo)

    @Provides
    fun provideAddAccountUseCase(repo: AccountRepository) = AddAccountUseCase(repo)

    @Provides
    fun provideDeleteAccountUseCase(repo: AccountRepository) = DeleteAccountUseCase(repo)

    @Provides
    fun provideDeleteAccountsUseCase(repo: AccountRepository) = DeleteAccountsUseCase(repo)

    @Provides
    fun provideGetTrashedAccountsUseCase(repo: AccountRepository) = GetTrashedAccountsUseCase(repo)

    @Provides
    fun provideRestoreAccountsUseCase(repo: AccountRepository) = RestoreAccountsUseCase(repo)

    @Provides
    fun providePurgeAccountsUseCase(repo: AccountRepository) = PurgeAccountsUseCase(repo)

    @Provides
    fun providePurgeExpiredTrashUseCase(repo: AccountRepository) = PurgeExpiredTrashUseCase(repo)

    @Provides
    fun provideUpdateAccountUseCase(repo: AccountRepository) = UpdateAccountUseCase(repo)

    @Provides
    fun provideReorderAccountsUseCase(repo: AccountRepository) = ReorderAccountsUseCase(repo)

    @Provides
    fun provideGetAccountByIdUseCase(repo: AccountRepository) = GetAccountByIdUseCase(repo)

    @Provides
    fun provideGetAccountsByIdsUseCase(repo: AccountRepository) = GetAccountsByIdsUseCase(repo)

    @Provides
    fun provideIncrementHotpCounterUseCase(repo: AccountRepository) =
        IncrementHotpCounterUseCase(repo)

    @Provides
    fun provideRecordUsageUseCase(repo: AccountRepository) = RecordUsageUseCase(repo)

    @Provides
    fun provideImportAccountsUseCase(repo: AccountRepository) = ImportAccountsUseCase(repo)

    @Provides
    fun provideRenameGroupUseCase(repo: AccountRepository) = RenameGroupUseCase(repo)

    @Provides
    fun provideDeleteGroupUseCase(repo: AccountRepository) = DeleteGroupUseCase(repo)

    @Provides
    fun provideGenerateOtpUseCase() = GenerateOtpUseCase()
}

@Module
@InstallIn(SingletonComponent::class)
interface DataBindsModule {
    @Binds
    @Singleton
    fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository
}
