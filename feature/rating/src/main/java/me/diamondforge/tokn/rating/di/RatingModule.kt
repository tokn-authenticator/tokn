package me.diamondforge.tokn.rating.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.diamondforge.tokn.rating.AndroidInstallInfoProvider
import me.diamondforge.tokn.rating.InstallInfoProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RatingModule {

    @Binds
    @Singleton
    abstract fun bindInstallInfoProvider(impl: AndroidInstallInfoProvider): InstallInfoProvider
}
