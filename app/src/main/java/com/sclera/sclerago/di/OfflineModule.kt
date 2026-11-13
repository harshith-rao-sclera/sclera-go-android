package com.sclera.sclerago.di

import com.sclera.sclerago.data.local.LocalAssetFileStore
import com.sclera.sclerago.data.offline.AssetFileStore
import com.sclera.sclerago.data.offline.AssetsOfflineSource
import com.sclera.sclerago.data.offline.AssetsWriter
import com.sclera.sclerago.data.offline.FieldsOfflineSource
import com.sclera.sclerago.data.offline.FieldsWriter
import com.sclera.sclerago.data.offline.MasterFormatsOfflineSource
import com.sclera.sclerago.data.offline.PlacesOfflineSource
import com.sclera.sclerago.data.offline.PlacesWriter
import com.sclera.sclerago.data.offline.PropertyOfflineSource
import com.sclera.sclerago.data.offline.ValidationOfflineSource
import com.sclera.sclerago.data.offline.ValidationWriter
import com.sclera.sclerago.data.repository.AssetConfigRepositoryImpl
import com.sclera.sclerago.data.repository.AssetRepositoryImpl
import com.sclera.sclerago.data.repository.LocationRepositoryImpl
import com.sclera.sclerago.data.repository.OnlineValidationImpl
import com.sclera.sclerago.data.repository.ValidationRepositoryImpl
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.data.sync.SyncCredentialsProvider
import com.sclera.sclerago.domain.repository.AssetConfigRepository
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.LocationRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import com.sclera.sclerago.domain.repository.OnlineValidation
import com.sclera.sclerago.domain.repository.ValidationRepository
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

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
    fun properties(db: AdcDatabase, @IoDispatcher io: CoroutineDispatcher) = PropertyOfflineSource(db, io)

    @Provides @Singleton
    fun assets(
        db: AdcDatabase,
        @IoDispatcher io: CoroutineDispatcher,
        files: AssetFileStore,
        places: PlacesOfflineSource,
        formats: MasterFormatsOfflineSource,
        validation: ValidationOfflineSource,
    ) = AssetsOfflineSource(db, io, files, places, formats, validation)

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
    ) = AssetsWriter(db, io, time, ids, user, places, formats, files)

    @Provides @Singleton
    fun placesWriter(db: AdcDatabase, @IoDispatcher io: CoroutineDispatcher, time: TimeProvider, ids: IdGenerator) =
        PlacesWriter(db, io, time, ids)

    @Provides @Singleton
    fun fieldsWriter(db: AdcDatabase, @IoDispatcher io: CoroutineDispatcher, time: TimeProvider, ids: IdGenerator) =
        FieldsWriter(db, io, time, ids)

    @Provides @Singleton
    fun validationWriter(
        db: AdcDatabase,
        @IoDispatcher io: CoroutineDispatcher,
        time: TimeProvider,
        ids: IdGenerator,
        user: SyncCredentialsProvider,
    ) = ValidationWriter(db, io, time, ids, user)
}

/**
 * The repositories over the offline database (they live as long as the app, as the database does) and the media
 * files kept with it. Everything here that holds user data is wiped on sign-out.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OfflineRepositoryModule {
    @Binds abstract fun assets(impl: AssetRepositoryImpl): AssetRepository
    @Binds abstract fun locations(impl: LocationRepositoryImpl): LocationRepository
    @Binds abstract fun assetConfig(impl: AssetConfigRepositoryImpl): AssetConfigRepository
    @Binds abstract fun validation(impl: ValidationRepositoryImpl): ValidationRepository
    @Binds abstract fun onlineValidation(impl: OnlineValidationImpl): OnlineValidation
    @Binds abstract fun assetFiles(impl: LocalAssetFileStore): AssetFileStore

    @Binds @IntoSet abstract fun clearLocations(impl: LocationRepositoryImpl): UserDataCleaner
    @Binds @IntoSet abstract fun clearAssetConfig(impl: AssetConfigRepositoryImpl): UserDataCleaner
    @Binds @IntoSet abstract fun clearValidation(impl: ValidationRepositoryImpl): UserDataCleaner
    @Binds @IntoSet abstract fun clearAssetFiles(impl: LocalAssetFileStore): UserDataCleaner
}
