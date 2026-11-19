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

    /**
     * What the asset is: an asset on the server by [assetId] (its saved answers and photos; [photos] sent now come
     * first), else from [details] and [photos] (at most 3); or just the type [masterFormatId] when given.
     */
    suspend fun suggest(
        property: String,
        details: SuggestDetailsDto,
        photos: List<ClassifyPhoto>,
        masterFormatId: String?,
        assetId: String?,
    ): SuggestDto {
        val parts = buildList {
            // The server reads an asset's own answers when it has its id, and the details otherwise.
            if (assetId != null) add(MultipartBody.Part.createFormData("assetId", assetId))
            else add(MultipartBody.Part.createFormData("details", AdcJson.encodeToString(SuggestDetailsDto.serializer(), details)))
            photos.forEach { p -> add(MultipartBody.Part.createFormData("photos", p.filename, p.bytes.toRequestBody(p.contentType.toMediaType()))) }
            masterFormatId?.let { add(MultipartBody.Part.createFormData("masterFormatId", it)) }
        }
        return call.json({ service.suggest(property, parts) }) { AdcJson.decodeFromString(SuggestDto.serializer(), it) }
    }

    suspend fun typeFields(property: String, masterFormatId: String): MasterFormatFieldsDto =
        call.json({ service.typeFields(property, masterFormatId) }) { AdcJson.decodeFromString(MasterFormatFieldsDto.serializer(), it) }

    suspend fun jacsCodes(query: String?, cursor: String?, limit: Int): PageDto<JacsCodeDto> =
        call.json({ service.jacsCodes(query, cursor, limit) }) { AdcJson.decodeFromString(PageDto.serializer(JacsCodeDto.serializer()), it) }

    suspend fun jacsMasterFormat(property: String, code: String): JacsMasterFormatDto =
        call.json({ service.jacsMasterFormat(property, code) }) { AdcJson.decodeFromString(JacsMasterFormatDto.serializer(), it) }

    suspend fun assetIcons(): PageDto<AssetIconDto> =
        call.json({ service.assetIcons() }) { AdcJson.decodeFromString(PageDto.serializer(AssetIconDto.serializer()), it) }

    /** The SVG of the icon [name], as text. */
    suspend fun assetIconSvg(name: String): String = call.bytes { service.assetIcon(name) }.toString(Charsets.UTF_8)

    suspend fun classification(property: String): ClassificationDto =
        call.json({ service.classification(property) }) { AdcJson.decodeFromString(ClassificationDto.serializer(), it) }

    /** Every active type's fields of [property]. */
    suspend fun allTypeFields(property: String): List<MasterFormatFieldsDto> =
        call.json({ service.allTypeFields(property, ids = null) }) { AdcJson.decodeFromString(MasterFormatFieldsListDto.serializer(), it).data }
}
