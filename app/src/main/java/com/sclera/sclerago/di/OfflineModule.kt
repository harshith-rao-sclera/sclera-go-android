package com.sclera.sclerago.di

import com.sclera.sclerago.data.local.LocalAssetFileStore
import com.sclera.sclerago.data.local.PropertyListCache
import com.sclera.sclerago.data.local.PropertyTable
import com.sclera.sclerago.data.offline.AssetFileStore
import com.sclera.sclerago.data.offline.AssetsOfflineSource
import com.sclera.sclerago.data.offline.AssetsWriter
import com.sclera.sclerago.data.offline.FieldsOfflineSource
import com.sclera.sclerago.data.offline.MasterFormatsOfflineSource
import com.sclera.sclerago.data.offline.MediaBlobs
import com.sclera.sclerago.data.offline.PlacesOfflineSource
import com.sclera.sclerago.data.offline.PropertyOfflineSource
import com.sclera.sclerago.data.offline.TypeFieldsLookup
import com.sclera.sclerago.data.offline.ValidationOfflineSource
import com.sclera.sclerago.data.offline.ValidationRunsOfflineSource
import com.sclera.sclerago.data.repository.AssetActivityRepositoryImpl
import com.sclera.sclerago.data.repository.SyncSettingsRepositoryImpl
import com.sclera.sclerago.domain.repository.SyncSettingsRepository
import com.sclera.sclerago.data.repository.AssetConfigRepositoryImpl
import com.sclera.sclerago.data.repository.AssetFormRepositoryImpl
import com.sclera.sclerago.data.repository.AssetOwnFieldsRepositoryImpl
import com.sclera.sclerago.data.repository.AssetIconRepositoryImpl
import com.sclera.sclerago.data.repository.AssetRepositoryImpl
import com.sclera.sclerago.data.repository.AssetValidationRepositoryImpl
import com.sclera.sclerago.data.repository.ClassificationRepositoryImpl
import com.sclera.sclerago.data.repository.JacsRepositoryImpl
import com.sclera.sclerago.data.repository.LocationRepositoryImpl
import com.sclera.sclerago.data.repository.PhotoRulesRepositoryImpl
import com.sclera.sclerago.data.repository.SpecLookupRepositoryImpl
import com.sclera.sclerago.data.repository.VoiceFillRepositoryImpl
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.data.sync.SyncCredentialsProvider
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.repository.AssetActivityRepository
import com.sclera.sclerago.domain.repository.AssetConfigRepository
import com.sclera.sclerago.domain.repository.AssetFormRepository
import com.sclera.sclerago.domain.repository.AssetOwnFieldsRepository
import com.sclera.sclerago.domain.repository.AssetIconRepository
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.AssetValidationRepository
import com.sclera.sclerago.domain.repository.AuthRepository
import com.sclera.sclerago.domain.repository.ClassificationRepository
import com.sclera.sclerago.domain.repository.JacsRepository
import com.sclera.sclerago.domain.repository.LocationRepository
import com.sclera.sclerago.domain.repository.PhotoRulesRepository
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.repository.SpecLookupRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import com.sclera.sclerago.domain.repository.VoiceFillRepository
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

/** The sources and writers over the offline database. */
@Module
@InstallIn(SingletonComponent::class)
object OfflineModule {
    @Provides @Singleton
    fun places(db: AdcDatabase, @IoDispatcher io: CoroutineDispatcher) = PlacesOfflineSource(db, io)

    @Provides @Singleton
    fun fields(db: AdcDatabase, @IoDispatcher io: CoroutineDispatcher) = FieldsOfflineSource(db, io)

    @Provides @Singleton
    fun masterFormats(db: AdcDatabase, @IoDispatcher io: CoroutineDispatcher) = MasterFormatsOfflineSource(db, io)

    @Provides @Singleton
    fun validation(db: AdcDatabase, @IoDispatcher io: CoroutineDispatcher) = ValidationOfflineSource(db, io)

    @Provides @Singleton
    fun validationRuns(db: AdcDatabase, @IoDispatcher io: CoroutineDispatcher) = ValidationRunsOfflineSource(db, io)

    @Provides @Singleton
    fun properties(db: AdcDatabase, @IoDispatcher io: CoroutineDispatcher) = PropertyOfflineSource(db, io)

    @Provides @Singleton
    fun assets(
        db: AdcDatabase,
        @IoDispatcher io: CoroutineDispatcher,
        files: AssetFileStore,
        places: PlacesOfflineSource,
        formats: MasterFormatsOfflineSource,
        validation: ValidationOfflineSource,
        blobs: MediaBlobs,
    ) = AssetsOfflineSource(db, io, files, places, formats, validation, blobs)

    @Provides @Singleton
    fun assetsWriter(
        db: AdcDatabase,
        @IoDispatcher io: CoroutineDispatcher,
        time: TimeProvider,
        ids: IdGenerator,
        user: SyncCredentialsProvider,
        places: PlacesOfflineSource,
        formats: MasterFormatsOfflineSource,
        files: AssetFileStore,
        fields: FieldsOfflineSource,
        // Lazily: these repositories are made after the writer (sign-out wipes through it).
        people: Provider<AccessRepository>,
        auth: Provider<AuthRepository>,
    ) = AssetsWriter(
        db, io, time, ids, user, places, formats, files,
        // The type's fields in the property's database (an asset of a type gets them when saved).
        TypeFieldsLookup(fields::typeFieldsNow),
        // Names for "Asset assigned to …": the people list kept here (admins'), else the signed-in user's own name.
        nameOf = { userId ->
            people.get().people.value.firstOrNull { it.id == userId }?.name?.takeIf { it.isNotBlank() }
                ?: (auth.get().state.value as? AuthState.SignedIn)?.session?.displayName?.takeIf { userId == user.credentials.value?.userId }
        },
    )
}

/**
 * The repositories over the offline database (they live as long as the app, as the database does) and the media
 * files kept with it. Everything here that holds user data is wiped on sign-out.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OfflineRepositoryModule {
    @Binds abstract fun assets(impl: AssetRepositoryImpl): AssetRepository
    @Binds abstract fun assetActivity(impl: AssetActivityRepositoryImpl): AssetActivityRepository
    @Binds abstract fun assetForm(impl: AssetFormRepositoryImpl): AssetFormRepository
    @Binds abstract fun assetOwnFields(impl: AssetOwnFieldsRepositoryImpl): AssetOwnFieldsRepository
    @Binds abstract fun assetValidation(impl: AssetValidationRepositoryImpl): AssetValidationRepository
    @Binds abstract fun photoRules(impl: PhotoRulesRepositoryImpl): PhotoRulesRepository
    @Binds abstract fun specLookups(impl: SpecLookupRepositoryImpl): SpecLookupRepository
    @Binds abstract fun syncSettings(impl: SyncSettingsRepositoryImpl): SyncSettingsRepository
    @Binds abstract fun voiceFill(impl: VoiceFillRepositoryImpl): VoiceFillRepository
    @Binds abstract fun classification(impl: ClassificationRepositoryImpl): ClassificationRepository
    @Binds abstract fun jacs(impl: JacsRepositoryImpl): JacsRepository
    @Binds abstract fun assetIcons(impl: AssetIconRepositoryImpl): AssetIconRepository
    @Binds abstract fun locations(impl: LocationRepositoryImpl): LocationRepository
    @Binds abstract fun assetConfig(impl: AssetConfigRepositoryImpl): AssetConfigRepository
    @Binds abstract fun assetFiles(impl: LocalAssetFileStore): AssetFileStore
    @Binds abstract fun propertyList(impl: PropertyTable): PropertyListCache

    @Binds @IntoSet abstract fun clearAssetFiles(impl: LocalAssetFileStore): UserDataCleaner
    @Binds @IntoSet abstract fun clearAssetIcons(impl: AssetIconRepositoryImpl): UserDataCleaner
}
