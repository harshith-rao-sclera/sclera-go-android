package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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
        const val PAGE = 500
        const val MAX_PAGES = 50
    }
}
