package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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

    /** The manuals of [asset]'s model, and the latest search for them. */
    suspend fun assetManuals(property: String, asset: String): AssetManualsDto =
        call.json({ service.assetManuals(property, asset) }) { AdcJson.decodeFromString(AssetManualsDto.serializer(), it) }

    /** Tells the server the suggestions were shown, so the asset stops showing "ready". */
    suspend fun lookupSeen(property: String, job: String) {
        call.json({ service.lookupSeen(property, job) }) { }
    }

    /**
     * The phone's look-up from [photos] (at most 3) and [values] typed (field id -> value); with [masterFormatId] (the
     * asset's type) that type's fields are filled too and no type is suggested.
     */
    suspend fun lookUpOnPhone(property: String, photos: List<ClassifyPhoto>, values: JsonObject, masterFormatId: String?): SpecSuggestionsDto {
        val parts = buildList {
            add(MultipartBody.Part.createFormData("values", values.toString()))
            masterFormatId?.let { add(MultipartBody.Part.createFormData("masterFormatId", it)) }
            photos.forEach { p -> add(MultipartBody.Part.createFormData("photos", p.filename, p.bytes.toRequestBody(p.contentType.toMediaType()))) }
        }
        return call.json({ service.lookUpOnPhone(property, parts) }) { AdcJson.decodeFromString(SpecSuggestionsDto.serializer(), it) }
    }

    /** Voice fill: [body] is the transcript and the Info form; the answer is the values the AI found for its fields. */
    suspend fun voiceFill(property: String, body: JsonObject): VoiceFillDto =
        call.json({ service.voiceFill(property, body.asBody()) }) { AdcJson.decodeFromString(VoiceFillDto.serializer(), it) }

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
