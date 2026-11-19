package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.validation.BulkFixResult
import com.sclera.sclerago.domain.validation.Decision
import com.sclera.sclerago.domain.validation.DecisionOutcome
import com.sclera.sclerago.domain.validation.FieldChoice
import com.sclera.sclerago.domain.validation.IssueStatus
import com.sclera.sclerago.domain.validation.IssueValue
import com.sclera.sclerago.domain.validation.MergeAsset
import com.sclera.sclerago.domain.validation.ResultFilter
import com.sclera.sclerago.domain.validation.ResultPage
import com.sclera.sclerago.domain.validation.RunExport
import com.sclera.sclerago.domain.validation.RunInsights
import com.sclera.sclerago.domain.validation.RunIssue
import com.sclera.sclerago.domain.validation.RunRequest
import com.sclera.sclerago.domain.validation.StepOrder
import com.sclera.sclerago.domain.validation.ValRun
import com.sclera.sclerago.domain.validation.ValStep
import com.sclera.sclerago.domain.validation.ValTemplate
import com.sclera.sclerago.domain.validation.ValidationDef
import com.sclera.sclerago.domain.validation.ValidationDraft

// The Validation page's data at the server. Every call can throw OnlineException; calls that read lists fall back to
// what sync brought to the phone when there is no connection (see each one).

/** The steps, templates and the user's step order, and the fields a step can be limited to. */
interface ValidationCatalogRepository {
    /** The steps in the user's order; the server's defaults when offline. */
    suspend fun steps(propertyId: String): List<ValStep>

    /** The templates, their steps in the user's order; the server's defaults when offline. */
    suspend fun templates(propertyId: String): List<ValTemplate>

    suspend fun order(propertyId: String): StepOrder

    suspend fun saveOrder(propertyId: String, steps: List<String>): StepOrder

    suspend fun resetOrder(propertyId: String)

    /** The property's active asset fields (for "Fields" / "Match on" of a step). */
    suspend fun fields(propertyId: String): List<FieldChoice>
}

/** The user's saved validations. */
interface SavedValidationRepository {
    /** Newest first; from the phone's copy when offline. */
    suspend fun validations(propertyId: String): List<ValidationDef>

    /** Throws OnlineException (Conflict when the name is taken). */
    suspend fun create(propertyId: String, draft: ValidationDraft): ValidationDef

    suspend fun update(propertyId: String, id: String, draft: ValidationDraft): ValidationDef

    suspend fun delete(propertyId: String, id: String)
}

/** Validation runs, their results and issues, and what reviewers do with them. */
interface ValidationRunRepository {

    /** The newest runs (at most [limit]); from the phone's copy when offline. */
    suspend fun runs(propertyId: String, limit: Int): List<ValRun>

    /** One run; from the phone's copy when offline. */
    suspend fun run(propertyId: String, id: String): ValRun

    suspend fun start(propertyId: String, request: RunRequest): ValRun

    /** Starts a run that was saved as ready. */
    suspend fun startReady(propertyId: String, id: String): ValRun

    /** Runs a finished run [id] again: a new run with the same steps, settings and assets (the server's rerun). */
    suspend fun rerun(propertyId: String, id: String): ValRun

    /** Discards a run that was saved as ready. */
    suspend fun discard(propertyId: String, id: String)

    /** One page of [jobId]'s results; offline, all of the phone's copy filtered here. */
    suspend fun results(propertyId: String, jobId: String, filter: ResultFilter, cursor: String?): ResultPage

    /** [jobId]'s issues ([assetId]'s only when given) with [status] (all when null). */
    suspend fun issues(propertyId: String, jobId: String, assetId: String?, status: IssueStatus?): List<RunIssue>

    suspend fun insights(propertyId: String, jobId: String): RunInsights

    suspend fun export(propertyId: String, jobId: String): RunExport

    /** Applies [value] (the best suggestion when null). */
    suspend fun accept(propertyId: String, issueId: String, value: IssueValue?): RunIssue

    /** Applies a value the reviewer typed. */
    suspend fun acceptText(propertyId: String, issueId: String, text: String): RunIssue

    suspend fun dismiss(propertyId: String, issueId: String): RunIssue

    suspend fun revert(propertyId: String, issueId: String): RunIssue

    /** Applies the best suggestion of each issue that is at least [minConfidence] sure. */
    suspend fun acceptAll(propertyId: String, issueIds: List<String>, minConfidence: Double): BulkFixResult

    /**
     * Returns how many assets changed, and (marking as validated) those left as they were because they aren't submitted.
     */
    suspend fun decide(propertyId: String, jobId: String, assetIds: List<String>, decision: Decision, note: String?): DecisionOutcome

    /** An asset as the server has it, for merging duplicates. */
    suspend fun mergeAsset(propertyId: String, assetId: String): MergeAsset

    /** Merges [mergeIds] into [keepId], with the chosen value per field id. */
    suspend fun merge(propertyId: String, keepId: String, mergeIds: List<String>, values: Map<String, IssueValue>)
}
