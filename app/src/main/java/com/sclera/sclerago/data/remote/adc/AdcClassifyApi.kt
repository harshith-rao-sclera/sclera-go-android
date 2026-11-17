package com.sclera.sclerago.data.remote.adc

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The classification calls as typed results. Every call can throw [com.sclera.sclerago.domain.model.OnlineException];
 * see [OnlineCall].
 */
@Singleton
class AdcClassifyApi @Inject constructor(private val service: AdcClassifyService, private val call: OnlineCall) {

    /** What the asset is, from [details] and [photos] (at most 3), or just the type [masterFormatId] when given. */
    suspend fun suggest(property: String, details: SuggestDetailsDto, photos: List<ClassifyPhoto>, masterFormatId: String?): SuggestDto {
        val parts = buildList {
            add(MultipartBody.Part.createFormData("details", AdcJson.encodeToString(SuggestDetailsDto.serializer(), details)))
            photos.forEach { p -> add(MultipartBody.Part.createFormData("photos", p.filename, p.bytes.toRequestBody(p.contentType.toMediaType()))) }
            masterFormatId?.let { add(MultipartBody.Part.createFormData("masterFormatId", it)) }
        }
        return call.json({ service.suggest(property, parts) }) { AdcJson.decodeFromString(SuggestDto.serializer(), it) }
    }

    suspend fun typeFields(property: String, masterFormatId: String): MasterFormatFieldsDto =
        call.json({ service.typeFields(property, masterFormatId) }) { AdcJson.decodeFromString(MasterFormatFieldsDto.serializer(), it) }

    /** Every active type's fields of [property]. */
    suspend fun allTypeFields(property: String): List<MasterFormatFieldsDto> =
        call.json({ service.allTypeFields(property, ids = null) }) { AdcJson.decodeFromString(MasterFormatFieldsListDto.serializer(), it).data }
}
