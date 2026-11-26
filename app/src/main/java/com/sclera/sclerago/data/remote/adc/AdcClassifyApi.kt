package com.sclera.sclerago.data.remote.adc

import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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

    /**
     * The whole JACS catalogue with its version (the `ETag`), or null when it hasn't changed since [etag] (`304`).
     */
    suspend fun jacsCatalog(etag: String?): Pair<String?, JacsCatalogDto>? {
        var tag: String? = null
        val text = call.json({
            val answer = service.jacsCatalog(etag)
            tag = answer.headers()["ETag"]
            // Not changed: an empty body, read as "nothing new".
            if (answer.code() == NOT_MODIFIED) Response.success(NOT_MODIFIED_BODY.toResponseBody()) else answer
        }) { it }
        if (text.isEmpty()) return null
        val catalog = AdcJson.decodeFromString(JacsCatalogDto.serializer(), text)
        return (tag ?: catalog.version) to catalog
    }

    suspend fun jacsMasterFormat(property: String, code: String): JacsMasterFormatDto =
        call.json({ service.jacsMasterFormat(property, code) }) { AdcJson.decodeFromString(JacsMasterFormatDto.serializer(), it) }

    suspend fun assetIcons(): PageDto<AssetIconDto> =
        call.json({ service.assetIcons() }) { AdcJson.decodeFromString(PageDto.serializer(AssetIconDto.serializer()), it) }

    /** The SVG of the icon [name], as text. */
    suspend fun assetIconSvg(name: String): String = call.bytes { service.assetIcon(name) }.toString(Charsets.UTF_8)

    suspend fun classification(property: String): ClassificationDto =
        call.json({ service.classification(property) }) { AdcJson.decodeFromString(ClassificationDto.serializer(), it) }

    private companion object {
        const val NOT_MODIFIED = 304
        const val NOT_MODIFIED_BODY = ""
    }
}
