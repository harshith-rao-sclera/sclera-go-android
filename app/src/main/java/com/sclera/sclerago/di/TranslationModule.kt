package com.sclera.sclerago.di

import android.content.Context
import com.sclera.sclerago.data.translation.DataStoreTranslationPreference
import com.sclera.sclerago.data.translation.MlKitTextTranslator
import com.sclera.sclerago.data.translation.ModelUsage
import com.sclera.sclerago.data.translation.PrefsModelUsageStore
import com.sclera.sclerago.domain.repository.TextTranslator
import com.sclera.sclerago.domain.repository.TranslationModels
import com.sclera.sclerago.domain.repository.TranslationPreference
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The translation engine and the saved language. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TranslationBindings {
    @Binds abstract fun preference(impl: DataStoreTranslationPreference): TranslationPreference
    @Binds abstract fun translator(impl: MlKitTextTranslator): TextTranslator
    @Binds abstract fun models(impl: MlKitTextTranslator): TranslationModels

    companion object {
        @Provides @Singleton
        fun mlKit(@ApplicationContext context: Context): MlKitTextTranslator =
            MlKitTextTranslator(ModelUsage(PrefsModelUsageStore(context)))
    }
}
