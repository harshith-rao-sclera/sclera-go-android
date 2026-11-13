package com.sclera.sclerago.di

import com.sclera.sclerago.data.local.WebDataCleaner
import com.sclera.sclerago.data.repository.FileRepositoryImpl
import com.sclera.sclerago.data.repository.OnlineAutofillRepository
import com.sclera.sclerago.domain.repository.AutofillRepository
import com.sclera.sclerago.domain.repository.FileRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import dagger.Binds
import dagger.multibindings.IntoSet
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The AI autofill, looked up online (properties and people are in OnlineModule).
 */
@Module
@InstallIn(ActivityRetainedComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun autofill(impl: OnlineAutofillRepository): AutofillRepository
}

/** Device files (photos, documents, spreadsheets). */
@Module
@InstallIn(SingletonComponent::class)
abstract class FileModule {
    @Binds @Singleton abstract fun files(impl: FileRepositoryImpl): FileRepository
    @Binds @IntoSet abstract fun clearFiles(impl: FileRepositoryImpl): UserDataCleaner
    @Binds @IntoSet abstract fun clearWebData(impl: WebDataCleaner): UserDataCleaner
}
