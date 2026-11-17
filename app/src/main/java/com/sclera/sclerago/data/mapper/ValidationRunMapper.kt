package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.data.remote.adc.CheckDto
import com.sclera.sclerago.data.remote.adc.InsightsDto
import com.sclera.sclerago.data.remote.adc.IssueDto
import com.sclera.sclerago.data.remote.adc.JobDto
import com.sclera.sclerago.data.remote.adc.MergeAssetDto
import com.sclera.sclerago.data.remote.adc.ResultDto
import com.sclera.sclerago.data.remote.adc.RuleDto
import com.sclera.sclerago.data.remote.adc.SavedValidationDto
import com.sclera.sclerago.data.remote.adc.StepStatsDto
import com.sclera.sclerago.data.remote.adc.SuggestionDto
import com.sclera.sclerago.data.remote.adc.TemplateDto
import com.sclera.sclerago.domain.validation.DECISION_NOTE_MAX
import com.sclera.sclerago.domain.validation.Decision
import com.sclera.sclerago.domain.validation.FieldInsight
import com.sclera.sclerago.domain.validation.IssueSeverity
import com.sclera.sclerago.domain.validation.IssueStatus
import com.sclera.sclerago.domain.validation.IssueSuggestion
import com.sclera.sclerago.domain.validation.IssueTarget
import com.sclera.sclerago.domain.validation.IssueValue
import com.sclera.sclerago.domain.validation.isServerId
import com.sclera.sclerago.domain.validation.MergeAsset
import com.sclera.sclerago.domain.validation.RunAssetResult
import com.sclera.sclerago.domain.validation.RunInsights
import com.sclera.sclerago.domain.validation.RunIssue
import com.sclera.sclerago.domain.validation.RunMode
import com.sclera.sclerago.domain.validation.RunRequest
import com.sclera.sclerago.domain.validation.RunScope
import com.sclera.sclerago.domain.validation.RunSource
import com.sclera.sclerago.domain.validation.RunStatus
import com.sclera.sclerago.domain.validation.StepGroup
import com.sclera.sclerago.domain.validation.StepOutcome
import com.sclera.sclerago.domain.validation.StepSeverity
import com.sclera.sclerago.domain.validation.StepStats
import com.sclera.sclerago.domain.validation.TypeScope
import com.sclera.sclerago.domain.validation.ValCheck
import com.sclera.sclerago.domain.validation.ValRun
import com.sclera.sclerago.domain.validation.ValStep
import com.sclera.sclerago.domain.validation.ValTemplate
import com.sclera.sclerago.domain.validation.ValidationDef
import com.sclera.sclerago.domain.validation.ValidationDraft
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// The Validation page's server JSON (and the same rows in the offline database) as domain models, and the bodies sent.

private const val RUN_MAX_TEXT = 2000

private fun String?.clip(): String = orEmpty().take(RUN_MAX_TEXT)

// ---------------------------------------------------------------- Values

/** A JSON value as text to show: strings and numbers as they are, lists joined, empty for null or an object. */
internal fun JsonElement?.shownText(): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> contentOrNull.orEmpty()
    is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ")
    is JsonObject -> ""
}

internal fun JsonElement?.toIssueValue(): IssueValue? = if (this == null || this == JsonNull) null else IssueValue(toString(), shownText().clip())

private fun parseJson(text: String): JsonElement = runCatching { com.sclera.sclerago.data.remote.adc.AdcJson.parseToJsonElement(text) }
    .getOrDefault(JsonPrimitive(text))

// ---------------------------------------------------------------- Catalogue

private fun CheckDto.toDomain() = ValCheck(key, name, short, description, usesAi)

fun RuleDto.toDomain() = ValStep(key, name, short.ifBlank { name }, description, StepGroup.of(group), usesAi, checks.map { it.toDomain() })

fun TemplateDto.toDomain() = ValTemplate(key, name, description, rules)

// ---------------------------------------------------------------- Runs

private fun StepStatsDto.toDomain() = StepStats(passed, warning, failed, skipped)

fun JobDto.toDomain() = ValRun(
    id = id,
    validationId = validationId,
    name = name.clip(),
    steps = rules,
    scope = RunScope.of(scope),
    status = RunStatus.of(status),
    statusDetail = statusDetail?.clip(),
    phase = phase?.clip(),
    total = total,
    processed = processed,
    issueCount = issueCount,
    openCount = openCount,
    passedCount = passedCount,
    warningCount = warningCount,
    failedCount = failedCount,
    score = score,
    stepStats = ruleStats.mapValues { it.value.toDomain() },
    resumedFrom = resumedFrom,
    createdAt = parseDbTime(createdAt),
    startedAt = parseDbTime(startedAt),
    finishedAt = parseDbTime(finishedAt),
)

fun ResultDto.toDomain() = RunAssetResult(
    assetId = assetId,
    assetName = assetName.clip(),
    outcome = StepOutcome.of(outcome),
    checks = checks.mapValues { StepOutcome.of(it.value) },
    issueCount = issueCount,
    openCount = openCount,
    score = score,
    decision = Decision.of(decision),
    decisionNote = decisionNote?.clip(),
    checkedAt = parseDbTime(checkedAt),
)

private fun SuggestionDto.toDomain() = IssueSuggestion(
    value = value.toIssueValue() ?: IssueValue("null", ""),
    label = label?.clip(),
    confidence = confidence.coerceIn(0.0, 1.0),
    reason = reason?.clip(),
    sourceUrl = sourceUrl?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) },
)

private fun severityOf(text: String?) = when (text) {
    "error" -> IssueSeverity.ERROR
    "warning" -> IssueSeverity.WARNING
    else -> IssueSeverity.INFO
}

fun IssueDto.toDomain() = RunIssue(
    id = id,
    jobId = jobId,
    assetId = assetId,
    assetName = assetName.clip(),
    target = IssueTarget.of(target),
    fieldId = fieldId,
    fieldName = fieldName?.clip(),
    fieldType = fieldType,
    rule = rule,
    ruleName = ruleName.clip(),
    step = step.ifBlank { rule },
    severity = severityOf(severity),
    message = message.clip(),
    currentValue = currentValue.toIssueValue(),
    suggestions = suggestions.map { it.toDomain() },
    related = related,
    status = IssueStatus.of(status),
    resolvedValue = resolvedValue.toIssueValue(),
)

fun InsightsDto.toDomain() = RunInsights(
    topFields = topFields.map { FieldInsight(it.fieldId, it.fieldName.clip(), it.issues, it.open, it.missing) },
    validated = decisions["validated"] ?: 0,
    recollect = decisions["recollect"] ?: 0,
    excluded = decisions["excluded"] ?: 0,
    pending = decisions["pending"] ?: 0,
    applied = fixes["applied"] ?: 0,
    kept = fixes["kept"] ?: 0,
    open = fixes["open"] ?: 0,
    // The server keeps these dictionary keys snake_case.
    aiSuggested = fixes["ai_suggested"] ?: fixes["aiSuggested"] ?: 0,
    aiApplied = fixes["ai_applied"] ?: fixes["aiApplied"] ?: 0,
)

fun MergeAssetDto.toDomain() = MergeAsset(
    id = id,
    name = name.clip(),
    place = listOfNotNull(buildingName, floorName, locationName).filter { it.isNotBlank() }.joinToString(" › ").clip(),
    values = values.mapNotNull { (k, v) -> v.toIssueValue()?.let { k to it } }.toMap(),
)

// ---------------------------------------------------------------- Saved validations

private fun JsonObject.pickKey(snake: String, camel: String): JsonElement? = get(snake) ?: get(camel)

private fun settingsOf(settings: JsonObject): Triple<Map<String, List<String>>, Map<String, StepSeverity>, TypeScope?> {
    val fields = (settings.pickKey("rule_fields", "ruleFields") as? JsonObject).orEmpty().mapValues { (_, v) ->
        (v as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    }.filterValues { it.isNotEmpty() }
    val severity = (settings.pickKey("rule_severity", "ruleSeverity") as? JsonObject).orEmpty()
        .mapNotNull { (k, v) -> StepSeverity.of((v as? JsonPrimitive)?.contentOrNull)?.let { k to it } }.toMap()
    val scope = (settings["scope"] as? JsonObject)?.let { s ->
        TypeScope(
            (s.pickKey("master_format_ids", "masterFormatIds") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            (s["unclassified"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }
    return Triple(fields, severity, scope)
}

fun SavedValidationDto.toDomain(): ValidationDef {
    val (fields, severity, scope) = settingsOf(settings)
    return ValidationDef(id, name.clip(), description?.clip(), rules, templateKey, fields, severity, scope, lastRun?.toDomain())
}

/** The body of `POST /validations` (with the template) or `PATCH /validations/{id}`: settings are sent camelCase. */
fun ValidationDraft.toBody(create: Boolean): JsonObject = buildJsonObject {
    put("name", name.trim().take(VALIDATION_MAX_NAME))
    val text = description.trim().take(VALIDATION_MAX_DESCRIPTION)
    if (text.isEmpty()) put("description", JsonNull) else put("description", text)
    putJsonArray("rules") { steps.distinct().forEach { add(JsonPrimitive(it)) } }
    if (create) templateKey?.let { put("templateKey", it) }
    putJsonObject("settings") {
        val fields = stepFields.filter { (k, v) -> k in steps && v.isNotEmpty() }
        if (fields.isNotEmpty()) putJsonObject("ruleFields") {
            fields.forEach { (k, ids) -> put(k, buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } }) }
        }
        val severity = stepSeverity.filterKeys { it in steps }
        if (severity.isNotEmpty()) putJsonObject("ruleSeverity") { severity.forEach { (k, v) -> put(k, v.wire) } }
        typeScope?.let { s ->
            putJsonObject("scope") {
                putJsonArray("masterFormatIds") { s.masterFormatIds.forEach { add(JsonPrimitive(it)) } }
                put("unclassified", s.unclassified)
            }
        }
    }
}

const val VALIDATION_MAX_NAME = 120
const val VALIDATION_MAX_DESCRIPTION = 500

// ---------------------------------------------------------------- Bodies

/** The body of `POST /validation-jobs`; chosen asset ids that aren't UUIDs (never synced) are left out. */
fun RunRequest.toBody(): JsonObject = buildJsonObject {
    when (val s = source) {
        is RunSource.Template -> put("template", s.key)
        is RunSource.Saved -> put("validationId", s.id)
        is RunSource.Steps -> putJsonArray("rules") { s.keys.distinct().forEach { add(JsonPrimitive(it)) } }
    }
    when (mode) {
        RunMode.CHOSEN -> putJsonArray("assetIds") { assetIds.filter(::isServerId).distinct().forEach { add(JsonPrimitive(it)) } }
        RunMode.CHANGED -> put("onlyChanged", true)
        RunMode.ALL -> Unit
    }
    if (!start) put("start", false)
}


fun acceptBody(value: JsonElement?): JsonObject = buildJsonObject { value?.let { put("value", it) } }

/** A suggestion's value as it was received. */
fun IssueValue.toJson(): JsonElement = parseJson(json)

fun decisionBody(assetIds: List<String>, decision: Decision, note: String?): JsonObject = buildJsonObject {
    putJsonArray("assetIds") { assetIds.distinct().forEach { add(JsonPrimitive(it)) } }
    put("decision", decision.wire)
    val n = note?.trim()?.take(DECISION_NOTE_MAX).orEmpty()
    if (n.isEmpty()) put("note", JsonNull) else put("note", n)
}

fun bulkAcceptBody(ids: List<String>, minConfidence: Double): JsonObject = buildJsonObject {
    putJsonArray("ids") { ids.distinct().forEach { add(JsonPrimitive(it)) } }
    put("action", "accept")
    put("minConfidence", minConfidence.coerceIn(0.0, 1.0))
}

fun mergeBody(keepId: String, mergeIds: List<String>, values: Map<String, IssueValue>): JsonObject = buildJsonObject {
    put("keepId", keepId)
    putJsonArray("mergeIds") { mergeIds.filter { it != keepId }.distinct().forEach { add(JsonPrimitive(it)) } }
    if (values.isNotEmpty()) putJsonObject("values") { values.forEach { (k, v) -> put(k, v.toJson()) } }
}

fun orderBody(steps: List<String>): JsonObject = buildJsonObject { putJsonArray("rules") { steps.distinct().forEach { add(JsonPrimitive(it)) } } }

// ---------------------------------------------------------------- Offline copies (same columns, snake_case)

fun DbRow.toValRun(): ValRun {
    val stats = DbJson.intMaps(string("rule_stats")).mapValues { (_, m) ->
        StepStats(m["passed"] ?: 0, m["warning"] ?: 0, m["failed"] ?: 0, m["skipped"] ?: 0)
    }
    val processed = int("processed") ?: 0
    val assetIds = string("asset_ids")
    return ValRun(
        id = string("id").orEmpty(),
        validationId = string("validation_id")?.takeIf { it.isNotBlank() },
        name = string("name").clip(),
        steps = DbJson.stringList(string("rules")),
        scope = when {
            bool("only_changed") -> RunScope.CHANGED
            assetIds.isNullOrBlank() || assetIds == "null" -> RunScope.ALL
            DbJson.obj(string("settings"))["scope"] != null -> RunScope.TYPES
            else -> RunScope.SELECTED
        },
        status = RunStatus.of(string("status")),
        statusDetail = string("status_detail")?.clip(),
        phase = string("phase")?.clip(),
        total = int("total") ?: 0,
        processed = processed,
        issueCount = int("issue_count") ?: 0,
        openCount = int("open_count") ?: 0,
        passedCount = int("passed_count") ?: 0,
        warningCount = int("warning_count") ?: 0,
        failedCount = int("failed_count") ?: 0,
        score = double("score_total")?.takeIf { processed > 0 }?.let { Math.round(it / processed * 10) / 10.0 },
        stepStats = stats,
        resumedFrom = null,
        createdAt = parseDbTime(string("created_at")),
        startedAt = parseDbTime(string("started_at")),
        finishedAt = parseDbTime(string("finished_at")),
    )
}

fun DbRow.toRunAssetResult() = RunAssetResult(
    assetId = string("asset_id").orEmpty(),
    assetName = string("asset_name").clip(),
    outcome = StepOutcome.of(string("outcome")),
    checks = DbJson.stringMap(string("checks")).mapValues { StepOutcome.of(it.value) },
    issueCount = int("issue_count") ?: 0,
    openCount = 0,
    score = int("score") ?: 0,
    decision = Decision.of(string("decision")),
    decisionNote = string("decision_note")?.clip(),
    checkedAt = parseDbTime(string("created_at")),
)

private fun parsed(text: String?): JsonElement? = text?.takeIf { it.isNotBlank() }?.let(::parseJson)

fun DbRow.toRunIssue(fieldNames: Map<String, Pair<String, String>>): RunIssue {
    val fieldId = string("field_id")?.takeIf { it.isNotBlank() }
    val target = IssueTarget.of(string("target"))
    val rule = string("rule").orEmpty()
    return RunIssue(
        id = string("id").orEmpty(),
        jobId = string("job_id").orEmpty(),
        assetId = string("asset_id").orEmpty(),
        assetName = "",
        target = target,
        fieldId = fieldId,
        fieldName = if (target == IssueTarget.JACS_CODE) "JACS code" else fieldId?.let { fieldNames[it]?.first },
        fieldType = if (target == IssueTarget.JACS_CODE) "text" else fieldId?.let { fieldNames[it]?.second },
        rule = rule,
        ruleName = rule,
        step = com.sclera.sclerago.domain.validation.stepOf(rule),
        severity = severityOf(string("severity")),
        message = string("message").clip(),
        currentValue = parsed(string("current_value")).toIssueValue(),
        suggestions = (parsed(string("suggestions")) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { o ->
            IssueSuggestion(
                value = o["value"].toIssueValue() ?: IssueValue("null", ""),
                label = (o["label"] as? JsonPrimitive)?.contentOrNull?.clip(),
                confidence = ((o["confidence"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 1.0),
                reason = (o["reason"] as? JsonPrimitive)?.contentOrNull?.clip(),
                sourceUrl = (o.pickKey("source_url", "sourceUrl") as? JsonPrimitive)?.contentOrNull,
            )
        },
        related = DbJson.stringList(string("related")),
        status = IssueStatus.of(string("status")),
        resolvedValue = parsed(string("resolved_value")).toIssueValue(),
    )
}

fun DbRow.toValidationDef(lastRun: ValRun?): ValidationDef {
    val (fields, severity, scope) = settingsOf(DbJson.obj(string("settings")))
    return ValidationDef(
        id = string("id").orEmpty(),
        name = string("name").clip(),
        description = string("description")?.clip(),
        steps = DbJson.stringList(string("rules")),
        templateKey = string("template_key"),
        stepFields = fields,
        stepSeverity = severity,
        typeScope = scope,
        lastRun = lastRun,
    )
}
