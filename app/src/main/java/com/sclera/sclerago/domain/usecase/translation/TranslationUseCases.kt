package com.sclera.sclerago.domain.usecase.translation

import com.sclera.sclerago.domain.model.ModelResult
import com.sclera.sclerago.domain.model.TranslationLanguages
import com.sclera.sclerago.domain.model.TranslationResult
import com.sclera.sclerago.domain.repository.TextTranslator
import com.sclera.sclerago.domain.repository.TranslationModels
import com.sclera.sclerago.domain.repository.TranslationPreference
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** The chosen language (null: none), kept up to date. */
class ObserveTranslationTargetUseCase @Inject constructor(private val preference: TranslationPreference) {
    operator fun invoke(): Flow<String?> = preference.target
}

/** Saves the language; null turns translation off. A code the app doesn't offer is ignored. */
class SetTranslationTargetUseCase @Inject constructor(private val preference: TranslationPreference) {
    suspend operator fun invoke(code: String?) {
        if (code == null || code in TranslationLanguages.supported) preference.setTarget(code)
    }
}

/** Translates a text; a blank one is left as it is. */
class TranslateTextUseCase @Inject constructor(private val translator: TextTranslator) {
    suspend operator fun invoke(text: String, target: String): TranslationResult =
        if (text.isBlank()) TranslationResult.Unavailable
        else translator.translate(listOf(text), target).firstOrNull() ?: TranslationResult.Failed
}

/** Makes a language work offline for names and comments (downloads it when needed). */
class PrepareTranslationLanguageUseCase @Inject constructor(private val models: TranslationModels) {
    suspend operator fun invoke(target: String): ModelResult = models.ensure(target)
}

/** The languages already on the phone. */
class GetInstalledTranslationLanguagesUseCase @Inject constructor(private val models: TranslationModels) {
    suspend operator fun invoke(): Set<String> = models.installed()
}

/** The translation feature's use cases, injected together. */
class TranslationUseCases @Inject constructor(
    val observeTarget: ObserveTranslationTargetUseCase,
    val setTarget: SetTranslationTargetUseCase,
    val translate: TranslateTextUseCase,
    val prepare: PrepareTranslationLanguageUseCase,
    val installed: GetInstalledTranslationLanguagesUseCase,
)
