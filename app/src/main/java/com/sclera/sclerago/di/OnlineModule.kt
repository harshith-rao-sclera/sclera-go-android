package com.sclera.sclerago.di

import com.sclera.sclerago.data.local.DocumentDownloads
import com.sclera.sclerago.data.local.RemoteMediaFiles
import com.sclera.sclerago.data.local.EncryptedCache
import com.sclera.sclerago.data.local.EncryptedFileCache
import com.sclera.sclerago.data.local.EncryptedResponseCache
import com.sclera.sclerago.data.local.ResponseCache
import com.sclera.sclerago.data.online.AccessStore
import com.sclera.sclerago.data.online.CurrentAccessRepositoryImpl
import com.sclera.sclerago.data.sync.AccessSyncSchedulerImpl
import com.sclera.sclerago.data.online.OnlineIdentity
import com.sclera.sclerago.data.online.PropertyStore
import com.sclera.sclerago.data.remote.adc.AccessTokenProvider
import com.sclera.sclerago.data.remote.adc.AdcApiService
import com.sclera.sclerago.data.remote.adc.AdcClassifyService
import com.sclera.sclerago.data.remote.adc.AdcConfig
import com.sclera.sclerago.data.remote.adc.AdcConfigService
import com.sclera.sclerago.data.remote.adc.AdcDocumentsService
import com.sclera.sclerago.data.remote.adc.AdcJobsService
import com.sclera.sclerago.data.remote.adc.AdcValidationService
import com.sclera.sclerago.data.remote.adc.AdcIntegrationService
import com.sclera.sclerago.data.remote.adc.OfflineCacheInterceptor
import com.sclera.sclerago.data.remote.adc.OfflineReads
import com.sclera.sclerago.data.remote.adc.ResponseCacheScope
import com.sclera.sclerago.data.remote.adc.forAdc
import com.sclera.sclerago.data.repository.AccessRepositoryImpl
import com.sclera.sclerago.data.repository.AssetDocumentsRepositoryImpl
import com.sclera.sclerago.data.repository.MediaContentRepositoryImpl
import com.sclera.sclerago.data.repository.AddressLookupRepositoryImpl
import com.sclera.sclerago.data.repository.PropertyImportRepositoryImpl
import com.sclera.sclerago.data.repository.AssetAssignmentRepositoryImpl
import com.sclera.sclerago.data.repository.AuthRepositoryImpl
import com.sclera.sclerago.data.repository.ConfigFieldsRepositoryImpl
import com.sclera.sclerago.data.repository.ConfigPlacesRepositoryImpl
import com.sclera.sclerago.data.repository.CurrencyRepositoryImpl
import com.sclera.sclerago.data.repository.FileImportRepositoryImpl
import com.sclera.sclerago.data.repository.GeofenceRepositoryImpl
import com.sclera.sclerago.data.repository.PremiseRepositoryImpl
import com.sclera.sclerago.data.repository.OnboardingRepositoryImpl
import com.sclera.sclerago.data.repository.PropertyRepositoryImpl
import com.sclera.sclerago.data.repository.PropertySettingsRepositoryImpl
import com.sclera.sclerago.data.repository.ValidationRunRepositoryImpl
import com.sclera.sclerago.data.repository.IntegrationRepositoryImpl
import com.sclera.sclerago.data.repository.VdmsLinkRepositoryImpl
import com.sclera.sclerago.data.repository.ManagedAssetsRepositoryImpl
import com.sclera.sclerago.data.repository.PlacesHistoryRepositoryImpl
import com.sclera.sclerago.data.repository.VdmsPlacesRepositoryImpl
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.repository.AssetDocumentsRepository
import com.sclera.sclerago.domain.repository.MediaContentRepository
import com.sclera.sclerago.domain.repository.AddressLookupRepository
import com.sclera.sclerago.domain.repository.PropertyImportRepository
import com.sclera.sclerago.domain.repository.AssetAssignmentRepository
import com.sclera.sclerago.domain.repository.ConfigFieldsRepository
import com.sclera.sclerago.domain.repository.ConfigPlacesRepository
import com.sclera.sclerago.domain.repository.CurrencyRepository
import com.sclera.sclerago.domain.repository.CurrentAccessRepository
import com.sclera.sclerago.domain.repository.AccessRefusals
import com.sclera.sclerago.domain.repository.AccessSyncScheduler
import com.sclera.sclerago.data.remote.adc.AccessRefusalsImpl
import com.sclera.sclerago.domain.repository.FileImportRepository
import com.sclera.sclerago.domain.repository.GeofenceRepository
import com.sclera.sclerago.domain.repository.PremiseRepository
import com.sclera.sclerago.domain.repository.OnboardingRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import com.sclera.sclerago.domain.repository.PropertySettingsRepository
import com.sclera.sclerago.domain.repository.SavedValidationRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import com.sclera.sclerago.domain.repository.ValidationCatalogRepository
import com.sclera.sclerago.domain.repository.ValidationRunRepository
import com.sclera.sclerago.domain.repository.IntegrationRepository
import com.sclera.sclerago.domain.repository.VdmsLinkRepository
import com.sclera.sclerago.domain.repository.ManagedAssetsRepository
import com.sclera.sclerago.domain.repository.PlacesHistoryRepository
import com.sclera.sclerago.domain.repository.VdmsPlacesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

/** The OkHttp client for the asset data collection API (the shared client plus the Sclera token handling). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AdcClient

/** The online ADC API: its HTTP client and service, and the repositories over it. Everything that holds user data is wiped on sign-out. */
@Module
@InstallIn(SingletonComponent::class)
abstract class OnlineModule {
    @Binds abstract fun properties(impl: PropertyRepositoryImpl): PropertyRepository
    @Binds abstract fun onboarding(impl: OnboardingRepositoryImpl): OnboardingRepository
    @Binds abstract fun access(impl: AccessRepositoryImpl): AccessRepository
    @Binds abstract fun assetAssignment(impl: AssetAssignmentRepositoryImpl): AssetAssignmentRepository
    @Binds abstract fun assetDocuments(impl: AssetDocumentsRepositoryImpl): AssetDocumentsRepository
    @Binds abstract fun mediaContent(impl: MediaContentRepositoryImpl): MediaContentRepository
    @Binds abstract fun addressLookup(impl: AddressLookupRepositoryImpl): AddressLookupRepository
    @Binds abstract fun propertyImport(impl: PropertyImportRepositoryImpl): PropertyImportRepository
    @Binds abstract fun currentAccess(impl: CurrentAccessRepositoryImpl): CurrentAccessRepository
    @Binds abstract fun accessRefusals(impl: AccessRefusalsImpl): AccessRefusals
    @Binds abstract fun accessSync(impl: AccessSyncSchedulerImpl): AccessSyncScheduler
    @Binds abstract fun cache(impl: EncryptedFileCache): EncryptedCache
    @Binds abstract fun responseCache(impl: EncryptedResponseCache): ResponseCache
    @Binds abstract fun offlineReads(impl: OfflineCacheInterceptor): OfflineReads
    @Binds abstract fun validationCatalog(impl: ValidationRunRepositoryImpl): ValidationCatalogRepository
    @Binds abstract fun savedValidations(impl: ValidationRunRepositoryImpl): SavedValidationRepository
    @Binds abstract fun validationRuns(impl: ValidationRunRepositoryImpl): ValidationRunRepository
    @Binds abstract fun integration(impl: IntegrationRepositoryImpl): IntegrationRepository
    @Binds abstract fun vdmsLink(impl: VdmsLinkRepositoryImpl): VdmsLinkRepository
    @Binds abstract fun managedAssets(impl: ManagedAssetsRepositoryImpl): ManagedAssetsRepository
    @Binds abstract fun vdmsPlaces(impl: VdmsPlacesRepositoryImpl): VdmsPlacesRepository
    @Binds abstract fun placesHistory(impl: PlacesHistoryRepositoryImpl): PlacesHistoryRepository
    @Binds abstract fun propertySettings(impl: PropertySettingsRepositoryImpl): PropertySettingsRepository
    @Binds abstract fun geofence(impl: GeofenceRepositoryImpl): GeofenceRepository
    @Binds abstract fun premise(impl: PremiseRepositoryImpl): PremiseRepository
    @Binds abstract fun configFields(impl: ConfigFieldsRepositoryImpl): ConfigFieldsRepository
    @Binds abstract fun configPlaces(impl: ConfigPlacesRepositoryImpl): ConfigPlacesRepository
    @Binds abstract fun currencies(impl: CurrencyRepositoryImpl): CurrencyRepository
    @Binds abstract fun fileImport(impl: FileImportRepositoryImpl): FileImportRepository

    @Binds @IntoSet abstract fun clearCache(impl: EncryptedFileCache): UserDataCleaner
    @Binds @IntoSet abstract fun clearResponses(impl: EncryptedResponseCache): UserDataCleaner
    @Binds @IntoSet abstract fun clearDocumentDownloads(impl: DocumentDownloads): UserDataCleaner
    @Binds @IntoSet abstract fun clearRemoteMedia(impl: RemoteMediaFiles): UserDataCleaner
    @Binds @IntoSet abstract fun clearProperties(impl: PropertyStore): UserDataCleaner
    @Binds @IntoSet abstract fun clearAccess(impl: AccessStore): UserDataCleaner
    @Binds @IntoSet abstract fun clearAssignees(impl: AssetAssignmentRepositoryImpl): UserDataCleaner
    @Binds @IntoSet abstract fun clearCurrentAccess(impl: CurrentAccessRepositoryImpl): UserDataCleaner
    @Binds @IntoSet abstract fun clearIdentity(impl: OnlineIdentity): UserDataCleaner
    @Binds @IntoSet abstract fun clearConfigPlaces(impl: ConfigPlacesRepositoryImpl): UserDataCleaner
    @Binds @IntoSet abstract fun clearConfigFields(impl: ConfigFieldsRepositoryImpl): UserDataCleaner
    @Binds @IntoSet abstract fun clearPremise(impl: PremiseRepositoryImpl): UserDataCleaner

    companion object {
        @Provides
        fun tokens(auth: AuthRepositoryImpl): AccessTokenProvider = auth

        @Provides
        @Singleton
        @AdcClient
        fun adcClient(base: OkHttpClient, tokens: AccessTokenProvider, offlineCache: OfflineCacheInterceptor): OkHttpClient =
            base.forAdc(tokens, offlineCache)

        /** The ADC answers kept for offline belong to the signed-in user in their organisation (as `GET /me` names them). */
        @Provides
        fun responseCacheScope(identity: OnlineIdentity): ResponseCacheScope =
            ResponseCacheScope { identity.identity.value?.let { it.userId + "\n" + it.organisationId } }

        @Provides
        @Singleton
        fun adcService(@AdcClient client: OkHttpClient): AdcApiService =
            Retrofit.Builder().baseUrl(AdcConfig.API_URL).client(client).build().create(AdcApiService::class.java)

        @Provides
        @Singleton
        fun adcClassifyService(@AdcClient client: OkHttpClient): AdcClassifyService =
            Retrofit.Builder().baseUrl(AdcConfig.API_URL).client(client).build().create(AdcClassifyService::class.java)

        @Provides
        @Singleton
        fun adcJobsService(@AdcClient client: OkHttpClient): AdcJobsService =
            Retrofit.Builder().baseUrl(AdcConfig.API_URL).client(client).build().create(AdcJobsService::class.java)

        @Provides
        @Singleton
        fun adcDocumentsService(@AdcClient client: OkHttpClient): AdcDocumentsService =
            Retrofit.Builder().baseUrl(AdcConfig.API_URL).client(client).build().create(AdcDocumentsService::class.java)

        @Provides
        @Singleton
        fun adcValidationService(@AdcClient client: OkHttpClient): AdcValidationService =
            Retrofit.Builder().baseUrl(AdcConfig.API_URL).client(client).build().create(AdcValidationService::class.java)

        @Provides
        @Singleton
        fun adcIntegrationService(@AdcClient client: OkHttpClient): AdcIntegrationService =
            Retrofit.Builder().baseUrl(AdcConfig.API_URL).client(client).build().create(AdcIntegrationService::class.java)

        @Provides
        @Singleton
        fun adcConfigService(@AdcClient client: OkHttpClient): AdcConfigService =
            Retrofit.Builder().baseUrl(AdcConfig.API_URL).client(client).build().create(AdcConfigService::class.java)
    }
}
