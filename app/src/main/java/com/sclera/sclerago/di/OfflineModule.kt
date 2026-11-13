package com.sclera.sclerago.di

import com.sclera.sclerago.data.offline.AssetsOfflineSource
import com.sclera.sclerago.data.offline.FieldsOfflineSource
import com.sclera.sclerago.data.offline.MasterFormatsOfflineSource
import com.sclera.sclerago.data.offline.PlacesOfflineSource
import com.sclera.sclerago.data.offline.PropertyOfflineSource
import com.sclera.sclerago.data.offline.ValidationOfflineSource
import com.sclera.sclerago.data.sync.AdcDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/** The read-only sources over the offline database. */
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
        places: PlacesOfflineSource,
        formats: MasterFormatsOfflineSource,
        validation: ValidationOfflineSource,
    ) = AssetsOfflineSource(db, io, places, formats, validation)
}
