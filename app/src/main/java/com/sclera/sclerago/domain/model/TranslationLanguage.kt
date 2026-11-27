package com.sclera.sclerago.domain.model

/**
 * The languages text can be translated into, by the app's own locale code (the codes the app's string folders use:
 * `iw` for Hebrew, `in` for Indonesian, `zh-CN` / `zh-TW` for Chinese). English is both a target and the fallback source.
 */
object TranslationLanguages {
    const val ENGLISH = "en"

    /** Every language offered, in the order of the app's locales. */
    val supported: List<String> = listOf(
        "ar", "zh-CN", "zh-TW", "nl", "en", "fr", "de", "hi", "in", "it", "iw", "ja", "ko", "pl", "pt-BR", "ru", "es", "th", "tr", "uk", "vi",
    )
}

/** What translating one text came to. */
sealed interface TranslationResult {
    /** The text in the chosen language. */
    data class Done(val text: String) : TranslationResult

    /** Left as written: the text is already in that language, or the language isn't on the phone. */
    data object Unavailable : TranslationResult

    /** The translation was tried and failed. */
    data object Failed : TranslationResult
}

/** How preparing a language (its translation model) ended. */
enum class ModelResult { ALREADY_INSTALLED, DOWNLOADED, FAILED }
