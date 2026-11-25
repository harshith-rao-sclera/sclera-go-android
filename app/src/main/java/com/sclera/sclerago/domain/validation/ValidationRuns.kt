package com.sclera.sclerago.domain.validation

import com.sclera.sclerago.domain.model.Status

/*
 * The Validation page, as the server runs it (`/v1/properties/{p}/validation…`): the catalogue of steps and templates,
 * the user's saved validations, the runs (jobs) with their per-asset results and the issues they found.
 * Step keys are the server's: complete, valid, unique, consistent, verified.
 */

/** What a step compares: each asset on its own, assets with each other, or the web and AI. */
enum class StepGroup {
    FIELD, GROUP, AI;

    companion object {
        fun of(text: String?): StepGroup = when (text) {
            "group" -> GROUP
            "ai" -> AI
            else -> FIELD
        }
    }
}

/** One check inside a step. */
data class ValCheck(val key: String, val name: String, val short: String, val description: String, val usesAi: Boolean)

/** One validation step (the server's "rule"): a group of checks that come out as one outcome. */
data class ValStep(
    val key: String,
    val name: String,
    val short: String,
    val description: String,
    val group: StepGroup,
    val usesAi: Boolean,
    val checks: List<ValCheck>,
)

/** A ready-made validation; [steps] are step keys, in the user's order. */
data class ValTemplate(val key: String, val name: String, val description: String, val steps: List<String>)

/** The order the user runs steps in (templates and custom runs follow it); [custom] when they changed it. */
data class StepOrder(val steps: List<String>, val custom: Boolean)

/** How a step reports what it finds, overriding each check's own choice. */
enum class StepSeverity(val wire: String) {
    WARNING("warning"), ERROR("error");

    companion object {
        fun of(text: String?): StepSeverity? = entries.firstOrNull { it.wire == text }
    }
}

/** A saved validation limited to some asset types (kept as it is; the web has no way to change it either). */
data class TypeScope(val masterFormatIds: List<String>, val unclassified: Boolean)

/** A validation the user saved: its steps in order, with optional fields and severity per step. */
data class ValidationDef(
    val id: String,
    val name: String,
    val description: String?,
    val steps: List<String>,
    val templateKey: String?,
    /** Step key → the field ids (server UUIDs) it looks at; absent = every field. */
    val stepFields: Map<String, List<String>>,
    val stepSeverity: Map<String, StepSeverity>,
    val typeScope: TypeScope?,
    val lastRun: ValRun?,
    /**
     * It has runs (or one set up as ready), as the server's `locked`: its runs keep the steps and settings they were made
     * with, so it can't be edited any more (the server answers 409); a new validation is made instead.
     */
    val locked: Boolean = false,
)

/** What is saved for a validation (new or changed). */
data class ValidationDraft(
    val name: String,
    val description: String,
    val steps: List<String>,
    val stepFields: Map<String, List<String>>,
    val stepSeverity: Map<String, StepSeverity>,
    val typeScope: TypeScope?,
    val templateKey: String?,
)

/** A run's state at the server. */
enum class RunStatus {
    READY, QUEUED, RUNNING, COMPLETED, FAILED;

    /** Queued or running: its numbers still change. */
    val active: Boolean get() = this == QUEUED || this == RUNNING
    val finished: Boolean get() = this == COMPLETED || this == FAILED

    companion object {
        fun of(text: String?): RunStatus = when (text) {
            "ready" -> READY
            "queued" -> QUEUED
            "running" -> RUNNING
            "failed" -> FAILED
            else -> COMPLETED
        }
    }
}

/** Which assets a run covers, as the server reports it. */
enum class RunScope {
    ALL, SELECTED, CHANGED, TYPES;

    companion object {
        fun of(text: String?): RunScope = when (text) {
            "selected" -> SELECTED
            "changed" -> CHANGED
            "types" -> TYPES
            else -> ALL
        }
    }
}

data class StepStats(val passed: Int, val warning: Int, val failed: Int, val skipped: Int) {
    val seen: Int get() = passed + warning + failed + skipped
}

/** How a step of a run is shown: waiting, not applicable or its worst outcome. */
enum class StepState { PENDING, SKIPPED, PASSED, WARNING, FAILED }

/** One validation run (a "job"). Times are epoch millis. */
data class ValRun(
    val id: String,
    val validationId: String?,
    val name: String,
    val steps: List<String>,
    val scope: RunScope,
    val status: RunStatus,
    val statusDetail: String?,
    val phase: String?,
    val total: Int,
    val processed: Int,
    val issueCount: Int,
    val openCount: Int,
    val passedCount: Int,
    val warningCount: Int,
    val failedCount: Int,
    /** 0..100, null until an asset was checked. */
    val score: Double?,
    val stepStats: Map<String, StepStats>,
    val resumedFrom: String?,
    val createdAt: Long?,
    val startedAt: Long?,
    val finishedAt: Long?,
    /** Who started it (the server's `startedBy`, a name); null when the server doesn't say. */
    val startedBy: String? = null,
    /** The id of who started it (the server's `createdBy`); null when the app started it or the server doesn't say. */
    val createdBy: String? = null,
    /** How it was set up, as it ran; only when one run is read from the server (null in lists and offline). */
    val setup: RunSetup? = null,
) {
    /** The server's step state: nothing seen yet is waiting (or skipped once done); else the worst outcome. */
    fun stateOf(step: String): StepState {
        val s = stepStats[step]
        return when {
            s == null || s.seen == 0 -> if (status.active) StepState.PENDING else StepState.SKIPPED
            s.skipped == s.seen -> StepState.SKIPPED
            s.failed > 0 -> StepState.FAILED
            s.warning > 0 -> StepState.WARNING
            else -> StepState.PASSED
        }
    }
}

/** What a new run checks: a template, a saved validation or a list of steps. */
sealed interface RunSource {
    data class Template(val key: String) : RunSource
    data class Saved(val id: String) : RunSource
    data class Steps(val keys: List<String>) : RunSource
}

/** Which assets a new run covers. */
enum class RunMode { ALL, CHOSEN, CHANGED }

/** A run to start now ([start]) or to save as ready. */
data class RunRequest(val source: RunSource, val mode: RunMode, val assetIds: List<String>, val start: Boolean)

/** A reviewer's decision on an asset in a run. [EXCLUDED] is no longer offered: it only comes back from older runs (Undo clears it). */
enum class Decision(val wire: String) {
    VALIDATED("validated"), RECOLLECT("recollect"), EXCLUDED("excluded"), CLEAR("clear");

    companion object {
        fun of(text: String?): Decision? = entries.firstOrNull { it.wire == text && it != CLEAR }
    }
}

/**
 * How a run was set up, as it ran (the server's `setup` on one run): each step's checks with the fields and severity
 * they used, and the asset types it was limited to ([types], plus assets with no type when [unclassified]; neither:
 * every asset).
 */
data class RunSetup(val steps: List<SetupStep>, val types: List<String>, val unclassified: Boolean)

/** One step of a run's setup, with its checks. */
data class SetupStep(val key: String, val name: String, val checks: List<SetupCheck>)

/**
 * A check as a run used it: the names of the [fields] it looked at (null: every field; a deleted one as the server names
 * it) and the [severity] its problems were reported as (null: as the check says).
 */
data class SetupCheck(val key: String, val name: String, val fields: List<String>?, val severity: IssueSeverity?)

/** How one asset did in a run. */
data class RunAssetResult(
    /** The result's id: the list is newest first by it (time-ordered, so its text order is its age); null from an older server. */
    val id: String? = null,
    val assetId: String,
    val assetName: String,
    val outcome: StepOutcome,
    /** Step key → its outcome for this asset (missing = skipped). */
    val checks: Map<String, StepOutcome>,
    val issueCount: Int,
    val openCount: Int,
    val score: Int,
    val decision: Decision?,
    val decisionNote: String?,
    val checkedAt: Long?,
    /** The asset's status now (the server's `assetStatus`); null when unknown or the asset was deleted. */
    val assetStatus: Status? = null,
) {
    fun outcomeOf(step: String): StepOutcome = checks[step] ?: StepOutcome.SKIPPED

    /**
     * The run is only a check for this asset: it is a draft or an exception, so it can't be marked as validated (submit
     * it first), as the server's decide refuses and the web's "check only".
     */
    val checkOnly: Boolean get() = assetStatus != null && assetStatus !in SIGNABLE_STATUSES
}

/** Only submitted assets (or ones already validated) can be marked as validated in a run. */
private val SIGNABLE_STATUSES = setOf(Status.SUBMITTED, Status.VALIDATED)

/** What a run's decision did: how many assets it changed, and those left as they were because they aren't submitted. */
data class DecisionOutcome(val updated: Int, val notSubmitted: List<String> = emptyList())

/** Which results to list: an outcome, the assets that didn't pass a step, a name search. */
data class ResultFilter(val outcome: StepOutcome? = null, val step: String? = null, val query: String = "")

data class ResultPage(val items: List<RunAssetResult>, val nextCursor: String?)

/**
 * The results shown after a refresh, as the web's: the fresh first [page] over the rows already [shown]. A shown row the
 * page leaves out is dropped when it would be on that page (its asset was deleted or merged, or it no longer matches the
 * filter); rows past the page stay (they are on later pages), and so does the open asset's row ([keep]).
 */
fun withFirstPage(shown: List<RunAssetResult>, page: ResultPage, keep: String?): List<RunAssetResult> {
    val listed = page.items.mapTo(HashSet()) { it.assetId }
    val last = page.items.lastOrNull()?.id
    val stays = { r: RunAssetResult ->
        when {
            r.assetId in listed -> false
            r.assetId == keep -> true
            // The page holds every result: one it leaves out is gone.
            page.nextCursor == null -> false
            // Without ids (an older server) a row stays.
            else -> !(r.id != null && last != null && r.id > last)
        }
    }
    return page.items + shown.filter(stays)
}

/** What an issue is about. */
enum class IssueTarget {
    FIELD, CLASSIFICATION, JACS_CODE;

    companion object {
        fun of(text: String?): IssueTarget = when (text) {
            "classification" -> CLASSIFICATION
            "jacs_code" -> JACS_CODE
            else -> FIELD
        }
    }
}

enum class IssueStatus(val wire: String) {
    OPEN("open"), ACCEPTED("accepted"), DISMISSED("dismissed"), RESOLVED("resolved");

    companion object {
        fun of(text: String?): IssueStatus = entries.firstOrNull { it.wire == text } ?: OPEN
    }
}

/** A value from the server, kept as its JSON ([json], sent back as it is) and as text to show ([text], blank when empty). */
data class IssueValue(val json: String, val text: String)

data class IssueSuggestion(
    val value: IssueValue,
    val label: String?,
    /** 0..1. */
    val confidence: Double,
    val reason: String?,
    val sourceUrl: String?,
) {
    val shown: String get() = label?.takeIf { it.isNotBlank() } ?: value.text
}

/** One problem a run found on an asset, with the fixes it suggests. */
data class RunIssue(
    val id: String,
    val jobId: String,
    val assetId: String,
    val assetName: String,
    val target: IssueTarget,
    val fieldId: String?,
    val fieldName: String?,
    val fieldType: String?,
    val rule: String,
    val ruleName: String,
    val step: String,
    val severity: IssueSeverity,
    val message: String,
    val currentValue: IssueValue?,
    val suggestions: List<IssueSuggestion>,
    /** Other assets this one may duplicate. */
    val related: List<String>,
    val status: IssueStatus,
    val resolvedValue: IssueValue?,
    /**
     * It may be kept as it is (dismissed), as the server's `canDismiss`: not missing data (the Complete step), which is
     * filled in on the asset instead (keeping it changes nothing; the next run finds it again).
     */
    val canDismiss: Boolean = true,
) {
    /** A field's value or the JACS code: a value can be applied or typed. */
    val likeField: Boolean get() = fieldId != null || target == IssueTarget.JACS_CODE
    val canApply: Boolean get() = likeField || target == IssueTarget.CLASSIFICATION
    val canType: Boolean get() = likeField && fieldType != "date"

    /** A fix sure enough to apply in bulk (the web's "confident fixes"). */
    val confident: Boolean get() = status == IssueStatus.OPEN && canApply && (suggestions.firstOrNull()?.confidence ?: 0.0) >= CONFIDENT
}

/** How sure a suggestion must be to be applied with "Apply confident fixes". */
const val CONFIDENT = 0.8

data class FieldInsight(val fieldId: String, val fieldName: String, val issues: Int, val open: Int, val missing: Int)

/** What a finished run found, summed up. */
data class RunInsights(
    val topFields: List<FieldInsight>,
    val validated: Int,
    val recollect: Int,
    val excluded: Int,
    val pending: Int,
    val applied: Int,
    val kept: Int,
    val open: Int,
    val aiSuggested: Int,
    val aiApplied: Int,
) {
    val hasContent: Boolean get() = topFields.isNotEmpty() || applied > 0 || open > 0
}

data class BulkFixResult(val accepted: Int, val dismissed: Int, val skipped: Int)

/** An asset field as a step can be limited to it (server id and name). */
data class FieldChoice(val id: String, val name: String)

/** An asset as the merge dialog compares it: its place and its values by field id. */
data class MergeAsset(val id: String, val name: String, val place: String, val values: Map<String, IssueValue>)

/** A run's results as a spreadsheet to save. */
class RunExport(val fileName: String, val bytes: ByteArray)

/** Whether [id] is a server id (a UUID): assets made on this phone get one only once synced, and only those can be validated. */
fun isServerId(id: String): Boolean = runCatching { java.util.UUID.fromString(id) }.isSuccess

/** The most a reviewer's recollection note may hold (the server's limit). */
const val DECISION_NOTE_MAX = 500

/**
 * The name of a copy of [name] that none of [taken] has, as the web's copyName: "Pumps (copy)", then "Pumps (copy 2)"…
 * (compared without case or surrounding spaces). A blank [name] is "Validation".
 */
fun copyName(name: String, taken: Collection<String>): String {
    val used = taken.mapTo(HashSet()) { it.trim().lowercase() }
    val base = name.trim().ifEmpty { "Validation" }
    return generateSequence(1) { it + 1 }
        .map { n -> if (n == 1) "$base (copy)" else "$base (copy $n)" }
        .first { it.lowercase() !in used }
}

/**
 * With AI off for the property, [steps] have nothing to run: every one of them uses AI (the web's "only AI"), so a run
 * of them can't start. Steps not in [byKey] don't count as AI.
 */
fun onlyAiSteps(steps: List<String>, byKey: Map<String, ValStep>): Boolean = steps.isNotEmpty() && steps.all { byKey[it]?.usesAi == true }
