package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The look-up and validation-run calls as typed results. Every call can throw
 * [com.sclera.sclerago.domain.model.OnlineException]; see [OnlineCall].
 */
@Singleton
class AdcJobsApi @Inject constructor(private val service: AdcJobsService, private val call: OnlineCall) {

    suspend fun startLookup(property: String, asset: String, values: JsonObject): LookupJobDto =
        call.json({ service.startLookup(property, asset, JsonObject(mapOf("values" to values)).asBody()) }) { lookupOf(it) }

    suspend fun lookup(property: String, job: String): LookupJobDto =
        call.json({ service.lookup(property, job) }) { lookupOf(it) }

    /** Tells the server the suggestions were shown, so the asset stops showing "ready". */
    suspend fun lookupSeen(property: String, job: String) {
        call.json({ service.lookupSeen(property, job) }) { }
    }

    suspend fun startValidation(property: String, body: JsonObject): ValidationJobDto =
        call.json({ service.startValidation(property, body.asBody()) }) { jobOf(it) }

    suspend fun validation(property: String, job: String): ValidationJobDto =
        call.json({ service.validation(property, job) }) { jobOf(it) }

    /** Every result of the run, following `nextCursor` (at most [MAX_PAGES] pages). */
    suspend fun validationResults(property: String, job: String): List<ValidationResultDto> {
        val all = mutableListOf<ValidationResultDto>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val page = call.json({ service.validationResults(property, job, AdcConfig.PAGE_SIZE, cursor) }) {
                AdcJson.decodeFromString(PageDto.serializer(ValidationResultDto.serializer()), it)
            }
            all += page.data
            cursor = page.nextCursor
            if (!page.hasMore || cursor == null) return all
        }
        return all
    }

    /** The run's problems, open or not (the most serious first, at most [MAX_ISSUES]). */
    suspend fun validationIssues(property: String, job: String): List<ValidationIssueDto> =
        call.json({ service.validationIssues(property, job, status = "", limit = MAX_ISSUES) }) {
            AdcJson.decodeFromString(PageDto.serializer(ValidationIssueDto.serializer()), it).data
        }

    private fun lookupOf(text: String) = AdcJson.decodeFromString(LookupJobDto.serializer(), text)

    private fun jobOf(text: String) = AdcJson.decodeFromString(ValidationJobDto.serializer(), text)

    private fun JsonObject.asBody(): RequestBody = toString().toRequestBody(JSON)

    private companion object {
        val JSON = "application/json".toMediaType()
        const val MAX_PAGES = 50
        const val MAX_ISSUES = 2000
    }
}
