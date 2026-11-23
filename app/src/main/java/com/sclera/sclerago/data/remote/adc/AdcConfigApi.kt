package com.sclera.sclerago.data.remote.adc

import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.SheetRejectedException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration → Asset fields and Building info calls of the ADC API, as typed results. Every call can throw
 * [com.sclera.sclerago.domain.model.OnlineException]; see [OnlineCall]. Bodies are built by the mappers
 * (`data/mapper/PropertyConfigMapper.kt`).
 */
@Singleton
class AdcConfigApi @Inject constructor(private val service: AdcConfigService, private val call: OnlineCall) {

    // ---- Asset fields

    /** The property's standard fields (every page). */
    suspend fun fields(property: String): List<CfgFieldDto> =
        allPages(CfgFieldDto.serializer()) { cursor -> service.fields(property, standard = true, limit = PAGE, cursor = cursor) }

    /** Adds several fields together (all or none). */
    suspend fun createFields(property: String, body: JsonObject): List<CfgFieldDto> =
        call.json({ service.createFields(property, body.asBody()) }) { AdcJson.decodeFromString(PageDto.serializer(CfgFieldDto.serializer()), it).data }

    suspend fun updateField(property: String, id: String, body: JsonObject): CfgFieldDto =
        call.json({ service.updateField(property, id, body.asBody()) }) { AdcJson.decodeFromString(CfgFieldDto.serializer(), it) }

    suspend fun deleteField(property: String, id: String) = call.unit { service.deleteField(property, id) }

    /** Import from Excel's sample sheet (`.xlsx`). */
    suspend fun fieldImportTemplate(property: String): ByteArray = call.bytes { service.fieldImportTemplate(property) }

    /** Reads the .xlsx [file] one field per row (nothing is made); a wrong sheet throws [SheetRejectedException]. */
    suspend fun readFieldImport(property: String, fileName: String, file: ByteArray): FieldImportReadDto =
        sheetCall({ service.readFieldImport(property, sheetPart(fileName, file)) }) { AdcJson.decodeFromString(FieldImportReadDto.serializer(), it) }

    // ---- Buildings, floors, locations

    suspend fun buildings(property: String): List<BuildingDto> =
        allPages(BuildingDto.serializer()) { cursor -> service.buildings(property, PAGE, cursor) }

    suspend fun floors(property: String): List<FloorDto> =
        allPages(FloorDto.serializer()) { cursor -> service.floors(property, PAGE, cursor) }

    suspend fun locations(property: String): List<LocationDto> =
        allPages(LocationDto.serializer()) { cursor -> service.locations(property, PAGE, cursor) }

    suspend fun createBuilding(property: String, body: JsonObject): BuildingDto =
        call.json({ service.createBuilding(property, body.asBody()) }) { AdcJson.decodeFromString(BuildingDto.serializer(), it) }

    suspend fun createFloor(property: String, building: String, body: JsonObject): FloorDto =
        call.json({ service.createFloor(property, building, body.asBody()) }) { AdcJson.decodeFromString(FloorDto.serializer(), it) }

    suspend fun createLocation(property: String, floor: String, body: JsonObject): LocationDto =
        call.json({ service.createLocation(property, floor, body.asBody()) }) { AdcJson.decodeFromString(LocationDto.serializer(), it) }

    /** PATCH of a building, floor or location; [collection] is `buildings`, `floors` or `locations`. */
    suspend fun updatePlace(property: String, collection: String, id: String, body: JsonObject) =
        call.unit { service.updatePlace(property, collection, id, body.asBody()) }

    suspend fun deletePlace(property: String, collection: String, id: String) = call.unit { service.deletePlace(property, collection, id) }

    /** Import buildings' sample sheet (`.xlsx`). */
    suspend fun placeImportTemplate(property: String): ByteArray = call.bytes { service.placeImportTemplate(property) }

    /**
     * Makes the buildings, floors and locations of the .xlsx [file] (columns matched by heading at the server), all or
     * nothing; a wrong sheet throws [SheetRejectedException].
     */
    suspend fun importPlaces(property: String, fileName: String, file: ByteArray): PlaceImportResultDto =
        sheetCall({ service.importPlaces(property, sheetPart(fileName, file)) }) { AdcJson.decodeFromString(PlaceImportResultDto.serializer(), it) }

    // ---- Spreadsheet uploads

    private fun sheetPart(fileName: String, file: ByteArray): MultipartBody.Part =
        MultipartBody.Part.createFormData("file", fileName, file.toRequestBody(XLSX.toMediaType()))

    /**
     * [call] for a spreadsheet upload: the server refuses a wrong sheet with `422` and lists the rows to fix, so that answer
     * becomes a [SheetRejectedException] with those row numbers (its messages are never read). Other failures are
     * [OnlineException]s as usual.
     */
    private suspend fun <T> sheetCall(request: suspend () -> Response<ResponseBody>, parse: (String) -> T): T {
        var rejectedRows: List<Int>? = null
        try {
            return call.json({
                val response = request()
                val body = response.errorBody()
                if (response.code() != UNPROCESSABLE || body == null) {
                    response
                } else {
                    // The body can be read once: keep it for OnlineCall, which maps the status.
                    val type = body.contentType()
                    val text = body.use { it.string() }
                    rejectedRows = problemRows(text)
                    Response.error<ResponseBody>(text.toResponseBody(type), response.raw())
                }
            }, parse)
        } catch (e: OnlineException) {
            rejectedRows?.let { throw SheetRejectedException(it) }
            throw e
        }
    }

    /** The rows a refused sheet's problem JSON names (`errors[].row`), each once, in order. */
    private fun problemRows(text: String): List<Int> = runCatching {
        AdcJson.decodeFromString(SheetProblemsDto.serializer(), text).errors.mapNotNull { it.row }.filter { it > 0 }.distinct().sorted()
    }.getOrDefault(emptyList())

    /** Every item of a cursor-paged list, following `nextCursor` (at most [MAX_PAGES] pages). */
    private suspend fun <T> allPages(
        item: KSerializer<T>,
        request: suspend (cursor: String?) -> retrofit2.Response<okhttp3.ResponseBody>,
    ): List<T> {
        val all = mutableListOf<T>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val page = call.json({ request(cursor) }) { AdcJson.decodeFromString(PageDto.serializer(item), it) }
            all += page.data
            cursor = page.nextCursor
            if (!page.hasMore || cursor == null) return all
        }
        return all
    }

    private fun JsonObject.asBody(): RequestBody = toString().toRequestBody(JSON)

    private companion object {
        val JSON = "application/json".toMediaType()
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val UNPROCESSABLE = 422
        const val PAGE = 500
        const val MAX_PAGES = 50
    }
}
