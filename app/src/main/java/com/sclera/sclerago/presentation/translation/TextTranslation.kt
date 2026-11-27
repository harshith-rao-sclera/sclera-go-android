package com.sclera.sclerago.presentation.translation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import com.sclera.sclerago.domain.model.TranslationResult

/** How server text (names, labels, comments) is translated on screen; the default leaves every text as written. */
@Stable
class TextTranslation(
    val target: String?,
    private val cached: (String, String) -> String?,
    private val run: suspend (String, String) -> TranslationResult,
) {
    fun cached(text: String, target: String): String? = cached.invoke(text, target)
    suspend fun translate(text: String, target: String): TranslationResult = run(text, target)

    companion object {
        val None = TextTranslation(null, { _, _ -> null }, { _, _ -> TranslationResult.Unavailable })
    }
}

val LocalTextTranslation = staticCompositionLocalOf { TextTranslation.None }

/** A name or label in the chosen language: as written until its translation is ready, and when it can't be translated. */
@Composable
fun rememberTranslated(text: String): String {
    val translation = LocalTextTranslation.current
    val target = translation.target
    if (target == null || !isTranslatable(text)) return text
    val shown by produceState(translation.cached(text, target) ?: text, text, target) {
        value = translation.cached(text, target) ?: text
        (translation.translate(text, target) as? TranslationResult.Done)?.let { value = it.text }
    }
    return shown
}

/** Codes, numbers, addresses and short capitals (a tag like "AHU-3", a model like "48TC", an email) are never translated. */
internal fun isTranslatable(text: String): Boolean =
    text.any { it.isLowerCase() } && '@' !in text
