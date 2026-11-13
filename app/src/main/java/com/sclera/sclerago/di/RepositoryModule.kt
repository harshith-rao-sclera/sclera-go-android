package com.sclera.sclerago.di

import com.sclera.sclerago.data.local.WebDataCleaner
import com.sclera.sclerago.data.repository.AccessRepositoryImpl
import com.sclera.sclerago.data.repository.FileRepositoryImpl
import com.sclera.sclerago.data.repository.LocalAutofillRepository
import com.sclera.sclerago.data.repository.OnboardingRepositoryImpl
import com.sclera.sclerago.data.repository.PropertyRepositoryImpl
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.repository.AutofillRepository
import com.sclera.sclerago.domain.repository.FileRepository
import com.sclera.sclerago.domain.repository.OnboardingRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import dagger.Binds
import dagger.multibindings.IntoSet
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The working data that is not in the offline database yet (properties, Property Settings, people and access, autofill).
 * Held in memory for as long as the screen's ViewModels, as when it lived in them.
 */
@Module
@InstallIn(ActivityRetainedComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun properties(impl: PropertyRepositoryImpl): PropertyRepository
    @Binds abstract fun onboarding(impl: OnboardingRepositoryImpl): OnboardingRepository
    @Binds abstract fun access(impl: AccessRepositoryImpl): AccessRepository
    @Binds abstract fun autofill(impl: LocalAutofillRepository): AutofillRepository

    // Everything above that holds user data is also wiped on sign-out (ClearUserDataUseCase).
    @Binds @IntoSet abstract fun clearProperties(impl: PropertyRepositoryImpl): UserDataCleaner
    @Binds @IntoSet abstract fun clearOnboarding(impl: OnboardingRepositoryImpl): UserDataCleaner
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
