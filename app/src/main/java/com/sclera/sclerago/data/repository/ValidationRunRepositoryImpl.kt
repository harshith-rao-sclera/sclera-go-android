package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.mapper.acceptBody
import com.sclera.sclerago.data.mapper.bulkAcceptBody
import com.sclera.sclerago.data.mapper.decisionBody
import com.sclera.sclerago.data.mapper.mergeBody
import com.sclera.sclerago.data.mapper.orderBody
import com.sclera.sclerago.data.mapper.toBody
import com.sclera.sclerago.data.mapper.toDomain
import com.sclera.sclerago.data.mapper.toJson
import com.sclera.sclerago.data.offline.ValidationRunsOfflineSource
import com.sclera.sclerago.data.remote.adc.AdcValidationApi
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.SavedValidationRepository
import com.sclera.sclerago.domain.repository.ValidationCatalogRepository
import com.sclera.sclerago.domain.repository.ValidationRunRepository
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
import com.sclera.sclerago.domain.validation.StepOutcome
import com.sclera.sclerago.domain.validation.ValRun
import com.sclera.sclerago.domain.validation.ValStep
import com.sclera.sclerago.domain.validation.ValTemplate
import com.sclera.sclerago.domain.validation.ValidationDef
import com.sclera.sclerago.domain.validation.ValidationDraft
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Validation page at the server (`/v1/properties/{p}/validation…`). Lists fall back to the phone's copy (what sync
 * last brought) when there is no connection; everything that changes data needs the server.
 */
@Singleton
class ValidationRunRepositoryImpl @Inject constructor(
    private val api: AdcValidationApi,
    private val offline: ValidationRunsOfflineSource,
) : ValidationCatalogRepository, SavedValidationRepository, ValidationRunRepository {

    /** [online], or [fallback] when there is no connection. */
    private suspend fun <T> orOffline(online: suspend () -> T, fallback: suspend () -> T): T = try {
        online()
    } catch (e: OnlineException) {
        if (e.error == OnlineError.Offline) fallback() else throw e
    }

    // ---- Catalogue

    override suspend fun steps(propertyId: String): List<ValStep> = api.rules(propertyId).map { it.toDomain() }

    override suspend fun templates(propertyId: String): List<ValTemplate> = api.templates(propertyId).map { it.toDomain() }

    override suspend fun order(propertyId: String): StepOrder = api.order(propertyId).let { StepOrder(it.rules, it.custom) }

    override suspend fun saveOrder(propertyId: String, steps: List<String>): StepOrder =
        api.saveOrder(propertyId, orderBody(steps)).let { StepOrder(it.rules, it.custom) }

    override suspend fun resetOrder(propertyId: String) = api.resetOrder(propertyId)

    override suspend fun fields(propertyId: String): List<FieldChoice> =
        api.fields(propertyId).filter { it.isActive }.map { FieldChoice(it.id, it.name) }

    // ---- Saved validations

    override suspend fun validations(propertyId: String): List<ValidationDef> =
        orOffline({ api.validations(propertyId).map { it.toDomain() } }, { offline.validations() })

    override suspend fun create(propertyId: String, draft: ValidationDraft): ValidationDef =
        api.createValidation(propertyId, draft.toBody(create = true)).toDomain()

    override suspend fun update(propertyId: String, id: String, draft: ValidationDraft): ValidationDef =
        api.updateValidation(propertyId, id, draft.toBody(create = false)).toDomain()

    override suspend fun delete(propertyId: String, id: String) = api.deleteValidation(propertyId, id)

    // ---- Runs

    override suspend fun runs(propertyId: String, limit: Int): List<ValRun> =
        orOffline({ api.jobs(propertyId, limit.coerceIn(1, 100)).map { it.toDomain() } }, { offline.runs(limit) })

    override suspend fun run(propertyId: String, id: String): ValRun =
        orOffline({ api.job(propertyId, id).toDomain() }, { offline.run(id) ?: throw OnlineException(OnlineError.Offline) })

    override suspend fun start(propertyId: String, request: RunRequest): ValRun = api.startJob(propertyId, request.toBody()).toDomain()

    override suspend fun startReady(propertyId: String, id: String): ValRun = api.runReady(propertyId, id).toDomain()

    override suspend fun rerun(propertyId: String, id: String): ValRun = api.rerun(propertyId, id).toDomain()

    override suspend fun discard(propertyId: String, id: String) = api.discard(propertyId, id)

    override suspend fun recheck(propertyId: String, jobId: String, assetId: String): ValRun =
        api.recheck(propertyId, jobId, assetId).toDomain()

    override suspend fun results(propertyId: String, jobId: String, filter: ResultFilter, cursor: String?): ResultPage = orOffline(
        {
            val page = api.results(
                propertyId, jobId,
                outcome = filter.outcome?.let(::wireOutcome),
                step = filter.step,
                q = filter.query.trim().takeIf { it.isNotEmpty() },
                cursor = cursor,
            )
            ResultPage(page.data.map { it.toDomain() }, page.nextCursor.takeIf { page.hasMore })
        },
        {
            val q = filter.query.trim()
            val items = offline.results(jobId).filter { r ->
                (filter.outcome == null || r.outcome == filter.outcome) &&
                    (filter.step == null || r.outcomeOf(filter.step).problem) &&
                    (q.isEmpty() || r.assetName.contains(q, ignoreCase = true))
            }
            ResultPage(items, null)
        },
    )

    override suspend fun issues(propertyId: String, jobId: String, assetId: String?, status: IssueStatus?): List<RunIssue> = orOffline(
        { api.issues(propertyId, jobId, status?.wire.orEmpty(), assetId).map { it.toDomain() } },
        { offline.issues(jobId, assetId, status) },
    )

    override suspend fun insights(propertyId: String, jobId: String): RunInsights = api.insights(propertyId, jobId).toDomain()

    override suspend fun export(propertyId: String, jobId: String): RunExport {
        val name = api.job(propertyId, jobId).name
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60).ifEmpty { "validation" }
        return RunExport("$slug-results.csv", api.export(propertyId, jobId).toByteArray(Charsets.UTF_8))
    }

    override suspend fun accept(propertyId: String, issueId: String, value: IssueValue?): RunIssue =
        api.accept(propertyId, issueId, acceptBody(value?.toJson())).toDomain()

    override suspend fun acceptText(propertyId: String, issueId: String, text: String): RunIssue =
        api.accept(propertyId, issueId, acceptBody(JsonPrimitive(text.trim()))).toDomain()

    override suspend fun dismiss(propertyId: String, issueId: String): RunIssue = api.dismiss(propertyId, issueId).toDomain()

    override suspend fun revert(propertyId: String, issueId: String): RunIssue = api.revert(propertyId, issueId).toDomain()

    override suspend fun acceptAll(propertyId: String, issueIds: List<String>, minConfidence: Double): BulkFixResult =
        api.bulk(propertyId, bulkAcceptBody(issueIds, minConfidence)).let { BulkFixResult(it.accepted, it.dismissed, it.skipped) }

    override suspend fun decide(propertyId: String, jobId: String, assetIds: List<String>, decision: Decision, note: String?): DecisionOutcome =
        api.decide(propertyId, jobId, decisionBody(assetIds, decision, note)).let { DecisionOutcome(it.updated, it.notSubmitted) }

    override suspend fun mergeAsset(propertyId: String, assetId: String): MergeAsset = api.asset(propertyId, assetId).toDomain()

    override suspend fun merge(propertyId: String, keepId: String, mergeIds: List<String>, values: Map<String, IssueValue>) =
        api.merge(propertyId, mergeBody(keepId, mergeIds, values))

    private fun wireOutcome(o: StepOutcome) = when (o) {
        StepOutcome.PASSED -> "passed"
        StepOutcome.WARNING -> "warning"
        StepOutcome.FAILED -> "failed"
        StepOutcome.SKIPPED -> "skipped"
    }
}
