package com.sclera.sclerago.di

import com.sclera.sclerago.data.repository.AccessRepositoryImpl
import com.sclera.sclerago.data.repository.AssetConfigRepositoryImpl
import com.sclera.sclerago.data.repository.AssetRepositoryImpl
import com.sclera.sclerago.data.repository.FileRepositoryImpl
import com.sclera.sclerago.data.repository.LocationRepositoryImpl
import com.sclera.sclerago.data.repository.OnboardingRepositoryImpl
import com.sclera.sclerago.data.repository.PropertyRepositoryImpl
import com.sclera.sclerago.data.repository.ValidationRepositoryImpl
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.repository.AssetConfigRepository
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.FileRepository
import com.sclera.sclerago.domain.repository.LocationRepository
import com.sclera.sclerago.domain.repository.OnboardingRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import com.sclera.sclerago.domain.repository.ValidationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The app's working data (assets, properties, configuration, validation, people and access).
 * Held in memory for as long as the screen's ViewModels, as when it lived in them.
 */
@Module
@InstallIn(ActivityRetainedComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun assets(impl: AssetRepositoryImpl): AssetRepository
    @Binds abstract fun properties(impl: PropertyRepositoryImpl): PropertyRepository
    @Binds abstract fun locations(impl: LocationRepositoryImpl): LocationRepository
    @Binds abstract fun assetConfig(impl: AssetConfigRepositoryImpl): AssetConfigRepository
    @Binds abstract fun onboarding(impl: OnboardingRepositoryImpl): OnboardingRepository
    @Binds abstract fun validation(impl: ValidationRepositoryImpl): ValidationRepository
    @Binds abstract fun access(impl: AccessRepositoryImpl): AccessRepository
}

/** Device files (photos, documents, spreadsheets). */
@Module
@InstallIn(SingletonComponent::class)
abstract class FileModule {
    @Binds @Singleton abstract fun files(impl: FileRepositoryImpl): FileRepository
}
