package com.sclera.sclerago.domain.model

// Classifying an asset: what the server suggests it is (system type › asset type › sub type and its JACS code), and the
// fields an asset of a type gets (the type library's suggested fields, kept as the asset's own fields, never mandatory).

/**
 * One field a type brings, as the server lists it: its [name], `asset_fields` [type] ("text", "dropdown", …), the
 * dropdown [options], [helpText], and the [position] in the type's list. An asset of the type gets it as its own field.
 */
data class TypeField(
    val name: String,
    val type: String,
    val options: List<String> = emptyList(),
    val helpText: String? = null,
    val position: Int = 0,
)

/** Where a suggested type came from: other units of the same manufacturer and model, the AI, or the type the person picked. */
enum class SuggestionSource { SAME_MODEL, AI, CHOSEN }

/** A type the server suggests for an asset (one of the property's master formats), with how sure it is and the type's fields. */
data class TypeSuggestion(
    val masterFormatId: String,
    val systemType: String,
    val assetType: String?,
    val subtype: String?,
    /** 0..1. */
    val confidence: Double,
    val reason: String,
    val source: SuggestionSource,
    val fields: List<TypeField>,
)

/** A JACS code suggested for the first type: the [code] the asset stores, its catalogue [title], how sure (0..1) and why. */
data class JacsSuggestion(val code: String, val title: String?, val confidence: Double, val reason: String?)

/** The server's answer to "what is this asset?": up to 3 types best first, up to 3 JACS codes, what the photos said, and why it is empty or partial. */
data class ClassificationResult(
    val suggestions: List<TypeSuggestion>,
    val jacsCodes: List<JacsSuggestion>,
    val message: String?,
    /** What the photos said; the server says to offer it for an empty manufacturer and model. Null when they said nothing. */
    val read: PhotoReading? = null,
)

/** What the photos said: [manufacturer] and [model] to fill empty fields, and [kind], the kind of equipment. */
data class PhotoReading(val manufacturer: String?, val model: String?, val kind: String?)

/** What is typed so far, sent with the photos (all optional). [other] is any other field as label to value. */
data class ClassifyDetails(
    val name: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val other: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = listOf(name, manufacturer, model).all { it.isNullOrBlank() }
}

/** How many photos the server reads to classify an asset or look up its specs. */
const val MAX_CLASSIFY_PHOTOS = 3

/** The largest photo the server takes for classifying or a look-up (10 MB). */
const val MAX_AI_PHOTO_BYTES = 10L * 1024 * 1024

/**
 * The details form key a type field's answer is kept under until the asset is saved and the field is made: the field
 * does not exist yet, so it is named by the type field's name.
 */
fun typeFieldKey(name: String): String = TYPE_FIELD_PREFIX + name

/** The type field name of a details form key made by [typeFieldKey], or null for any other key. */
fun typeFieldName(key: String): String? = key.removePrefix(TYPE_FIELD_PREFIX).takeIf { key.startsWith(TYPE_FIELD_PREFIX) }

private const val TYPE_FIELD_PREFIX = "type-field:"
