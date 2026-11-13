package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.data.remote.adc.ValidationIssueDto
import com.sclera.sclerago.data.remote.adc.ValidationResultDto
import com.sclera.sclerago.domain.validation.AssetResult
import com.sclera.sclerago.domain.validation.Finding
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/** The server's step (`complete`, `valid`, `unique`, `consistent`, `verified`) each of the app's rules is checked in. */
private val STEP_OF_RULE = mapOf(
    "mandatory" to "complete", "location" to "complete", "photo" to "complete",
    "formats" to "valid", "placeholders" to "valid", "dates" to "valid", "ranges" to "valid", "related" to "valid", "service" to "valid",
    "dup_entered_twice" to "unique", "dup_serial" to "unique",
    "dup_names" to "consistent", "make_consistent" to "consistent", "one_off" to "consistent", "outliers" to "consistent",
    "spec_published" to "verified", "spec_twins" to "verified", "spec_gaps" to "verified", "ai_read" to "verified", "ai_photos" to "verified",
)

private const val MAX_TEXT = 300
private const val MAX_NAME = 120

/** The body of `POST /validation-jobs`: the steps of [ruleIds] (once each, in order), on [assetIds]; null when no step or asset applies. */
fun validationJobBody(name: String, ruleIds: List<String>, assetIds: List<String>): JsonObject? {
    val steps = ruleIds.mapNotNull { STEP_OF_RULE[it] }.distinct()
    val ids = assetIds.filter { runCatching { UUID.fromString(it) }.isSuccess }.distinct()
    if (steps.isEmpty() || ids.isEmpty()) return null
    return JsonObject(
        mapOf(
            "rules" to JsonArray(steps.map(::JsonPrimitive)),
            "name" to JsonPrimitive(name.take(MAX_NAME)),
            "assetIds" to JsonArray(ids.map(::JsonPrimitive)),
        ),
    )
}

/** The run's results as [AssetResult]s: each asset's outcome with the problems found in it as its findings. */
fun List<ValidationResultDto>.toAssetResults(issues: List<ValidationIssueDto>): List<AssetResult> {
    val byAsset = issues.groupBy { it.assetId }
    return map { r ->
        AssetResult(
            r.assetId,
            r.assetName.orEmpty().take(MAX_TEXT),
            outcomeFromDb(r.outcome),
            byAsset[r.assetId].orEmpty().map { Finding(it.ruleName.take(MAX_TEXT), outcomeFromDb(it.severity), it.message.take(MAX_TEXT)) },
        )
    }
}
