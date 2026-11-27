package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.ModelResult
import com.sclera.sclerago.domain.model.TranslationResult
import kotlinx.coroutines.flow.Flow

/** The language the user reads the app and its data in; null means "Don't translate". */
interface TranslationPreference {
    val target: Flow<String?>
    suspend fun setTarget(code: String?)
}

/** Turns server text (names, labels, comments) into another language, using only what is on the phone: it never downloads. */
interface TextTranslator {
    /** Each of [texts] translated into [target] (an app locale code), in the same order. */
    suspend fun translate(texts: List<String>, target: String): List<TranslationResult>
}

/** The languages' translation models on the phone. */
interface TranslationModels {
    /** Makes [target] available offline, downloading it when it isn't on the phone yet. */
    suspend fun ensure(target: String): ModelResult

    /** The app language codes that work offline right now (English always does). */
    suspend fun installed(): Set<String>
}
