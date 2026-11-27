package com.sclera.sclerago.data.translation

/** Maps the app's locale codes to the codes ML Kit uses. */
internal object LanguageCodes {
    /** `iw` is ML Kit's `he`, `in` its `id`, and both Chinese scripts are `zh`. */
    fun toMlKit(appCode: String): String = when (appCode) {
        "iw" -> "he"
        "in" -> "id"
        "zh-CN", "zh-TW" -> "zh"
        "pt-BR" -> "pt"
        else -> appCode
    }
}
