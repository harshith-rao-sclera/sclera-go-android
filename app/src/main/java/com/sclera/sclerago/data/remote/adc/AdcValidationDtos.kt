package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// The Validation page's JSON (camelCase, as the API sends it). Only what the app reads is declared.
// Dictionaries inside values keep the server's own keys: `settings` (rule_fields, rule_severity, scope) and the
// insights' `fixes` (ai_suggested, ai_applied) are snake_case.

@Serializable
data class CheckDto(
    val key: String,
    val name: String = "",
    val short: String = "",
    val description: String = "",
    val usesAi: Boolean = false,
)

@Serializable
data class RuleDto(
    val key: String,
    val name: String = "",
    val short: String = "",
    val description: String = "",
    val group: String = "field",
    val usesAi: Boolean = false,
    val checks: List<CheckDto> = emptyList(),
)

@Serializable
data class TemplateDto(val key: String, val name: String = "", val description: String = "", val rules: List<String> = emptyList())

@Serializable
data class RuleOrderDto(val rules: List<String> = emptyList(), val custom: Boolean = false)

@Serializable
data class StepStatsDto(val passed: Int = 0, val warning: Int = 0, val failed: Int = 0, val skipped: Int = 0)

@Serializable
data class JobDto(
    val id: String,
    val validationId: String? = null,
    val name: String = "",
    val rules: List<String> = emptyList(),
    val scope: String = "all",
    val status: String = "",
    val statusDetail: String? = null,
    val phase: String? = null,
    val total: Int = 0,
    val processed: Int = 0,
    val issueCount: Int = 0,
    val openCount: Int = 0,
    val passedCount: Int = 0,
    val warningCount: Int = 0,
    val failedCount: Int = 0,
    val score: Double? = null,
    val ruleStats: Map<String, StepStatsDto> = emptyMap(),
    val resumedFrom: String? = null,
    val createdAt: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val startedBy: String? = null,
    /** Who started it (a user id); null when the app did. */
    val createdBy: String? = null,
    /** How it was set up; only when one run is read. */
    val setup: JobSetupDto? = null,
)

@Serializable
data class SetupCheckDto(val key: String = "", val name: String = "", val fields: List<String>? = null, val severity: String? = null)

@Serializable
data class SetupStepDto(val key: String = "", val name: String = "", val checks: List<SetupCheckDto> = emptyList())

/** `setup` of one run: each step's checks with the fields and severity they used, and the types it ran on. */
@Serializable
data class JobSetupDto(val steps: List<SetupStepDto> = emptyList(), val types: List<String> = emptyList(), val unclassified: Boolean = false)

@Serializable
data class SavedValidationDto(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val rules: List<String> = emptyList(),
    val templateKey: String? = null,
    val settings: JsonObject = JsonObject(emptyMap()),
    val lastRun: JobDto? = null,
    val locked: Boolean = false,
)

@Serializable
data class ResultDto(
    /** The result's id (the list is newest first by it); null from an older server. */
    val id: String? = null,
    val assetId: String,
    val assetName: String? = null,
    val outcome: String = "",
    val checks: Map<String, String> = emptyMap(),
    val issueCount: Int = 0,
    val openCount: Int = 0,
    val score: Int = 0,
    val decision: String? = null,
    val decisionNote: String? = null,
    val checkedAt: String? = null,
    /** The asset's status now (drafted, submitted, exception, saved); null when it was deleted. */
    val assetStatus: String? = null,
)

@Serializable
data class SuggestionDto(
    val value: JsonElement? = null,
    val label: String? = null,
    val confidence: Double = 0.0,
    val reason: String? = null,
    val sourceUrl: String? = null,
)

@Serializable
data class IssueDto(
    val id: String,
    val jobId: String = "",
    val assetId: String = "",
    val assetName: String? = null,
    val target: String = "field",
    val fieldId: String? = null,
    val fieldName: String? = null,
    val fieldType: String? = null,
    val rule: String = "",
    val ruleName: String = "",
    val step: String = "",
    val severity: String = "",
    val message: String = "",
    val currentValue: JsonElement? = null,
    val suggestions: List<SuggestionDto> = emptyList(),
    val related: List<String> = emptyList(),
    val status: String = "open",
    val resolvedValue: JsonElement? = null,
    /** False for missing data (the Complete step): filled in, never dismissed. */
    val canDismiss: Boolean = true,
)

@Serializable
data class FieldInsightDto(val fieldId: String = "", val fieldName: String = "", val issues: Int = 0, val open: Int = 0, val missing: Int = 0)

@Serializable
data class InsightsDto(
    val topFields: List<FieldInsightDto> = emptyList(),
    val decisions: Map<String, Int> = emptyMap(),
    val fixes: Map<String, Int> = emptyMap(),
)

@Serializable
data class BulkResultDto(val accepted: Int = 0, val dismissed: Int = 0, val skipped: Int = 0)

@Serializable
data class DecisionResultDto(
    val updated: Int = 0,
    /** Left as they were: marking as validated needs a submitted asset. */
    val notSubmitted: List<String> = emptyList(),
)

@Serializable
data class FieldDto(val id: String, val name: String = "", val isActive: Boolean = true)

@Serializable
data class MergeAssetDto(
    val id: String,
    val name: String? = null,
    val values: Map<String, JsonElement> = emptyMap(),
    val buildingName: String? = null,
    val floorName: String? = null,
    val locationName: String? = null,
    /** drafted, submitted, exception or saved. */
    val status: String? = null,
)
