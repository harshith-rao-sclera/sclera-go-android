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
)

/** A validation run: [status] is ready, queued, running, completed or failed. */
@Serializable
data class ValidationJobDto(val id: String, val status: String = "")

/** How one asset did in a run; [outcome] is passed, warning or failed. */
@Serializable
data class ValidationResultDto(val assetId: String, val assetName: String? = null, val outcome: String = "")

/** One problem found in a run; [severity] is error or warning. */
@Serializable
data class ValidationIssueDto(
    val assetId: String,
    val ruleName: String = "",
    val severity: String = "",
    val message: String = "",
)
