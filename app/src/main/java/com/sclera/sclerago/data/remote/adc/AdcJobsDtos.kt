package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// The background-job calls' JSON (spec look-ups and validation runs). Only what the app reads is declared.

/** A spec look-up: [status] is queued, running, completed or failed; [values] are keyed by the server's field id (a UUID), never by key. */
@Serializable
data class LookupJobDto(
    val id: String,
    val status: String = "",
    val values: Map<String, JsonElement> = emptyMap(),
    /** Why it failed. */
    val statusDetail: String? = null,
    /** The product the specs were found for. */
    val product: String? = null,
    /** One sentence on how it went. */
    val message: String? = null,
    /** Field id → how sure the suggestion is, 0..1. */
    val confidence: Map<String, Double> = emptyMap(),
    /** Field id → the web page the suggestion came from. */
    val sources: Map<String, String> = emptyMap(),
    /** Field id → what was typed to look the model up (manufacturer, model). */
    val typedValues: Map<String, JsonElement> = emptyMap(),
    val seenAt: String? = null,
    val createdAt: String? = null,
    /** The type the AI thinks the asset is, when it isn't the one it has; values for its fields are in [values]. */
    val suggestedType: TypeSuggestionDto? = null,
    /**
     * A JACS code for the type the asset has, when it has no code (with [suggestedType] none: that type brings its own).
     * Applied with the asset's update as its `jacsCodeId`.
     */
    val suggestedJacsCode: SuggestedCodeDto? = null,
    /** On start: the search for the model's manuals it started, if any. */
    val manualJobId: String? = null,
)

/**
 * The phone's look-up (`POST /properties/{id}/lookup`): values keyed by field id (the standard fields', and the
 * suggested type's), how sure, the page a looked-up value came from, which were read off the photos, where the photos
 * disagree with what is there, and the type the AI thinks it is.
 */
@Serializable
data class SpecSuggestionsDto(
    val product: String? = null,
    val values: Map<String, JsonElement> = emptyMap(),
    val confidence: Map<String, Double> = emptyMap(),
    val sources: Map<String, String> = emptyMap(),
    val fromPhoto: List<String> = emptyList(),
    val differs: List<String> = emptyList(),
    val message: String? = null,
    val suggestedType: TypeSuggestionDto? = null,
    /** What the photos say that none of the fields takes: `[{name, value}]`. */
    val extra: List<Map<String, String>> = emptyList(),
    /** The search for the model's manuals it started, if any. */
    val manualJobId: String? = null,
)

/** A validation run: [status] is ready, queued, running, completed or failed. */
@Serializable
data class ValidationJobDto(val id: String, val status: String = "")

/**
 * What the voice fill read (`POST /properties/{id}/voice-fill`): [values] keyed by the form keys the phone sent, in each
 * field's shape (text, a number, true / false, a list of options); how sure of each; the keys whose value differs from
 * the form's; and what was said that no field took (shown, never saved).
 */
@Serializable
data class VoiceFillDto(
    val values: Map<String, JsonElement> = emptyMap(),
    val confidence: Map<String, Double> = emptyMap(),
    val differs: List<String> = emptyList(),
    val unmatched: List<VoiceUnmatchedDto> = emptyList(),
    val message: String? = null,
)

/** Something said that no field took, and why. */
@Serializable
data class VoiceUnmatchedDto(val text: String = "", val reason: String = "")

/** One manual of a model, from the manufacturer's own site (`GET /assets/{id}/manuals`). */
@Serializable
data class ManualDto(
    val id: String,
    val manufacturer: String = "",
    val model: String = "",
    val title: String = "",
    /** manual, installation, datasheet, parts or other. */
    val kind: String? = null,
    val url: String? = null,
    val publisher: String? = null,
    val hasFile: Boolean = false,
)

/** The latest search for a model's manuals: queued, running, completed or failed. */
@Serializable
data class ManualJobDto(val id: String, val status: String = "")

/** An asset's model's manuals, and the latest search for them. */
@Serializable
data class AssetManualsDto(val manuals: List<ManualDto> = emptyList(), val job: ManualJobDto? = null)
