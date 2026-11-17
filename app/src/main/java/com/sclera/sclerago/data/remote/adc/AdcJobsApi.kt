package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    /** The asset's newest look-up, or null when it never had one. */
    suspend fun latestLookup(property: String, asset: String): LookupJobDto? =
        call.json({ service.latestLookup(property, asset) }) { AdcJson.decodeFromString(LookupJobDto.serializer().nullable, it) }

    suspend fun lookup(property: String, job: String): LookupJobDto =
        call.json({ service.lookup(property, job) }) { lookupOf(it) }

    /** Tells the server the suggestions were shown, so the asset stops showing "ready". */
    suspend fun lookupSeen(property: String, job: String) {
        call.json({ service.lookupSeen(property, job) }) { }
    }

    /** Starts validating [asset] again ([fromStart]: every step, else from its first step to look at); returns the run. */
    suspend fun revalidate(property: String, asset: String, fromStart: Boolean): ValidationJobDto {
        val body = JsonObject(if (fromStart) mapOf("fromStart" to JsonPrimitive(true)) else emptyMap())
        return call.json({ service.revalidate(property, asset, body.asBody()) }) { jobOf(it) }
    }

    suspend fun validation(property: String, job: String): ValidationJobDto =
        call.json({ service.validation(property, job) }) { jobOf(it) }

    private fun lookupOf(text: String) = AdcJson.decodeFromString(LookupJobDto.serializer(), text)

    private fun jobOf(text: String) = AdcJson.decodeFromString(ValidationJobDto.serializer(), text)

    private fun JsonObject.asBody(): RequestBody = toString().toRequestBody(JSON)

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
