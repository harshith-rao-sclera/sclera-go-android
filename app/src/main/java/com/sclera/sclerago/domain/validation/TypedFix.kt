package com.sclera.sclerago.domain.validation

/**
 * What a value typed to fix an issue must be, from its field's type, as the server checks it when the fix is accepted
 * (it refuses the rest): digits for a number, one of a dropdown's options. Dates, times and media are picked on the
 * asset's own page, so there's nothing to type for them (the web's typedValue).
 */
enum class TypedKind { NUMBER, OPTION, URL, TEXT }

private val NUMBER_TYPES = setOf("number", "currency")
private val NO_TYPING = setOf("date", "time", "image", "video", "voice_memo")

/** How the Valid step reads a number. */
private val NUMBER = Regex("""^-?\d+(\.\d+)?$""")

/** What can be on the way to a number while it's typed. */
private val NUMBER_SO_FAR = Regex("""^-?\d*\.?\d*$""")

/** The kind of value a field of [fieldType] takes; null when it isn't typed (dates, times, media). */
fun typedKind(fieldType: String?, options: List<String>): TypedKind? = when {
    fieldType in NO_TYPING -> null
    fieldType in NUMBER_TYPES -> TypedKind.NUMBER
    fieldType == "dropdown" && options.isNotEmpty() -> TypedKind.OPTION
    fieldType == "url" -> TypedKind.URL
    else -> TypedKind.TEXT
}

/** Whether [text] may stay in the box as it's typed: letters never get into a number. */
fun canTypeAs(kind: TypedKind?, text: String): Boolean = kind != TypedKind.NUMBER || NUMBER_SO_FAR.matches(text.trim())

/** Whether [text] can be saved as the fix: not blank, a whole number for a number, one of [options] for a dropdown. */
fun canSaveAs(kind: TypedKind?, text: String, options: List<String>): Boolean {
    val value = text.trim()
    if (kind == null || value.isEmpty()) return false
    return when (kind) {
        TypedKind.NUMBER -> NUMBER.matches(value)
        TypedKind.OPTION -> value in options
        TypedKind.URL, TypedKind.TEXT -> true
    }
}
