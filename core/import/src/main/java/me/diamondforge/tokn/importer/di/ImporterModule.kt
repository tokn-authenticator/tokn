package me.diamondforge.tokn.importer.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import me.diamondforge.tokn.importer.ExternalImporter
import me.diamondforge.tokn.importer.aegis.AegisImporter
import me.diamondforge.tokn.importer.otpauth.OtpAuthUriImporter
import me.diamondforge.tokn.importer.twofas.TwoFasImporter

/**
 * `OtpAuthMigrationImporter` is intentionally not bound here. Google Authenticator only
 * surfaces `otpauth-migration://` URIs via on-screen QR codes, never as a file, so binding
 * it would mislead users into an unusable file picker. The parser class stays available
 * for the QR scan flow to call directly.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ImporterModule {
    @Binds @IntoSet
    abstract fun bindAegis(impl: AegisImporter): ExternalImporter

    @Binds @IntoSet
    abstract fun bindTwoFas(impl: TwoFasImporter): ExternalImporter

    @Binds @IntoSet
    abstract fun bindOtpAuthUri(impl: OtpAuthUriImporter): ExternalImporter
}
