package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** The Validation page calls as typed results. Every call can throw [com.sclera.sclerago.domain.model.OnlineException]; see [OnlineCall]. */
@Singleton
class AdcValidationApi @Inject constructor(private val service: AdcValidationService, private val call: OnlineCall) {

    suspend fun rules(p: String): List<RuleDto> = call.json({ service.rules(p) }) { list(RuleDto.serializer(), it) }

    suspend fun templates(p: String): List<TemplateDto> = call.json({ service.templates(p) }) { list(TemplateDto.serializer(), it) }

    suspend fun order(p: String): RuleOrderDto = call.json({ service.order(p) }) { one(RuleOrderDto.serializer(), it) }

    suspend fun saveOrder(p: String, body: JsonObject): RuleOrderDto =
        call.json({ service.saveOrder(p, body.asBody()) }) { one(RuleOrderDto.serializer(), it) }

    suspend fun resetOrder(p: String) = call.unit { service.resetOrder(p) }

    /** Every active field, following `nextCursor` (at most [MAX_PAGES] pages). */
    suspend fun fields(p: String): List<FieldDto> {
        val all = mutableListOf<FieldDto>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val page = call.json({ service.fields(p, true, FIELDS_PAGE, cursor) }) { page(FieldDto.serializer(), it) }
            all += page.data
            cursor = page.nextCursor
            if (!page.hasMore || cursor == null) return all
        }
        return all
    }

    suspend fun validations(p: String): List<SavedValidationDto> =
        call.json({ service.validations(p) }) { page(SavedValidationDto.serializer(), it).data }

    suspend fun createValidation(p: String, body: JsonObject): SavedValidationDto =
        call.json({ service.createValidation(p, body.asBody()) }) { one(SavedValidationDto.serializer(), it) }

    suspend fun updateValidation(p: String, id: String, body: JsonObject): SavedValidationDto =
        call.json({ service.updateValidation(p, id, body.asBody()) }) { one(SavedValidationDto.serializer(), it) }

    suspend fun deleteValidation(p: String, id: String) = call.unit { service.deleteValidation(p, id) }

    suspend fun jobs(p: String, limit: Int): List<JobDto> = call.json({ service.jobs(p, limit) }) { page(JobDto.serializer(), it).data }

    suspend fun startJob(p: String, body: JsonObject): JobDto = call.json({ service.startJob(p, body.asBody()) }) { one(JobDto.serializer(), it) }

    suspend fun job(p: String, id: String): JobDto = call.json({ service.job(p, id) }) { one(JobDto.serializer(), it) }

    suspend fun runReady(p: String, id: String): JobDto = call.json({ service.runReady(p, id) }) { one(JobDto.serializer(), it) }

    suspend fun discard(p: String, id: String) = call.unit { service.discard(p, id) }

    suspend fun results(p: String, job: String, outcome: String?, step: String?, q: String?, cursor: String?): PageDto<ResultDto> =
        call.json({ service.results(p, job, outcome, step, q, RESULTS_PAGE, cursor) }) { page(ResultDto.serializer(), it) }

    /** [status] "" for every status. */
    suspend fun issues(p: String, job: String, status: String, assetId: String?): List<IssueDto> =
        call.json({ service.issues(p, job, status, assetId, MAX_ISSUES) }) { page(IssueDto.serializer(), it).data }

    suspend fun insights(p: String, job: String): InsightsDto = call.json({ service.insights(p, job) }) { one(InsightsDto.serializer(), it) }

    /** The run's results as CSV text. */
    suspend fun export(p: String, job: String): String = call.json({ service.export(p, job) }) { it }

    suspend fun decide(p: String, job: String, body: JsonObject): DecisionResultDto =
        call.json({ service.decide(p, job, body.asBody()) }) { one(DecisionResultDto.serializer(), it) }

    suspend fun accept(p: String, id: String, body: JsonObject): IssueDto =
        call.json({ service.accept(p, id, body.asBody()) }) { one(IssueDto.serializer(), it) }

    suspend fun dismiss(p: String, id: String): IssueDto = call.json({ service.dismiss(p, id) }) { one(IssueDto.serializer(), it) }

    suspend fun revert(p: String, id: String): IssueDto = call.json({ service.revert(p, id) }) { one(IssueDto.serializer(), it) }

    suspend fun bulk(p: String, body: JsonObject): BulkResultDto = call.json({ service.bulk(p, body.asBody()) }) { one(BulkResultDto.serializer(), it) }

    suspend fun asset(p: String, id: String): MergeAssetDto = call.json({ service.asset(p, id) }) { one(MergeAssetDto.serializer(), it) }

    suspend fun merge(p: String, body: JsonObject) {
        call.json({ service.merge(p, body.asBody()) }) { }
    }

    private fun <T> one(s: KSerializer<T>, text: String): T = AdcJson.decodeFromString(s, text)

    private fun <T> list(s: KSerializer<T>, text: String): List<T> = AdcJson.decodeFromString(ListSerializer(s), text)

    private fun <T> page(s: KSerializer<T>, text: String): PageDto<T> = AdcJson.decodeFromString(PageDto.serializer(s), text)

    private fun JsonObject.asBody(): RequestBody = toString().toRequestBody(JSON)

    private companion object {
        val JSON = "application/json".toMediaType()
        const val MAX_PAGES = 20
        const val FIELDS_PAGE = 500
        const val RESULTS_PAGE = 50
        const val MAX_ISSUES = 2000
    }
}
