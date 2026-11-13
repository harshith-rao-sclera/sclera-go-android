package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// The background-job calls' JSON (spec look-ups and validation runs). Only what the app reads is declared.

/** A spec look-up: [status] is queued, running, completed or failed; [values] are keyed by the server's field key. */
@Serializable
data class LookupJobDto(
    val id: String,
    val status: String = "",
    val values: Map<String, JsonElement> = emptyMap(),
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
