package com.sclera.sclerago.data.translation

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sclera.sclerago.domain.repository.TranslationPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.translationStore by preferencesDataStore("translation")

/** The chosen language, in DataStore: a language code only, not sensitive. */
@Singleton
class DataStoreTranslationPreference @Inject constructor(@ApplicationContext private val context: Context) : TranslationPreference {
    override val target: Flow<String?> = context.translationStore.data.map { it[KEY] }

    override suspend fun setTarget(code: String?) {
        context.translationStore.edit { if (code == null) it.remove(KEY) else it[KEY] = code }
    }

    private companion object {
        val KEY = stringPreferencesKey("target")
    }
}
