package com.sclera.sclerago.di

import com.sclera.sclerago.data.local.WebDataCleaner
import com.sclera.sclerago.data.repository.AccessRepositoryImpl
import com.sclera.sclerago.data.repository.AssetConfigRepositoryImpl
import com.sclera.sclerago.data.repository.AssetRepositoryImpl
import com.sclera.sclerago.data.repository.FileRepositoryImpl
import com.sclera.sclerago.data.repository.LocalAutofillRepository
import com.sclera.sclerago.data.repository.LocationRepositoryImpl
import com.sclera.sclerago.data.repository.OnboardingRepositoryImpl
import com.sclera.sclerago.data.repository.PropertyRepositoryImpl
import com.sclera.sclerago.data.repository.ValidationRepositoryImpl
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.repository.AssetConfigRepository
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.AutofillRepository
import com.sclera.sclerago.domain.repository.FileRepository
import com.sclera.sclerago.domain.repository.LocationRepository
import com.sclera.sclerago.domain.repository.OnboardingRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import com.sclera.sclerago.domain.repository.ValidationRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import dagger.Binds
import dagger.multibindings.IntoSet
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
    @Binds abstract fun autofill(impl: LocalAutofillRepository): AutofillRepository

    // Everything above that holds user data is also wiped on sign-out (ClearUserDataUseCase).
    @Binds @IntoSet abstract fun clearAssets(impl: AssetRepositoryImpl): UserDataCleaner
    @Binds @IntoSet abstract fun clearProperties(impl: PropertyRepositoryImpl): UserDataCleaner
    @Binds @IntoSet abstract fun clearLocations(impl: LocationRepositoryImpl): UserDataCleaner
    @Binds @IntoSet abstract fun clearAssetConfig(impl: AssetConfigRepositoryImpl): UserDataCleaner
    @Binds @IntoSet abstract fun clearOnboarding(impl: OnboardingRepositoryImpl): UserDataCleaner
    @Binds @IntoSet abstract fun clearValidation(impl: ValidationRepositoryImpl): UserDataCleaner
    @Binds @IntoSet abstract fun clearAccess(impl: AccessRepositoryImpl): UserDataCleaner
}

/** Device files (photos, documents, spreadsheets). */
@Module
@InstallIn(SingletonComponent::class)
abstract class FileModule {
    @Binds @Singleton abstract fun files(impl: FileRepositoryImpl): FileRepository
    @Binds @IntoSet abstract fun clearFiles(impl: FileRepositoryImpl): UserDataCleaner
    @Binds @IntoSet abstract fun clearWebData(impl: WebDataCleaner): UserDataCleaner
}
