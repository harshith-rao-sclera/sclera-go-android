package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ADC API calls the property, settings and access screens need, as typed results.
 * Every call can throw [com.sclera.sclerago.domain.model.OnlineException]; see [OnlineCall].
 * Request bodies are built by the mappers (`data/mapper/AdcMapper.kt`).
 */
@Singleton
class AdcApi @Inject constructor(private val service: AdcApiService, private val call: OnlineCall) {

    suspend fun me(): MeDto = call.json({ service.me() }) { AdcJson.decodeFromString(MeDto.serializer(), it) }

    // ---- Properties

    suspend fun properties(org: String): List<PropertyDto> =
        allPages(PropertyDto.serializer()) { cursor -> service.properties(org, AdcConfig.PAGE_SIZE, cursor) }

    suspend fun createProperty(org: String, body: JsonObject): PropertyDto =
        call.json({ service.createProperty(org, body.asBody()) }) { AdcJson.decodeFromString(PropertyDto.serializer(), it) }

    suspend fun property(id: String): PropertyDto =
        call.json({ service.property(id) }) { AdcJson.decodeFromString(PropertyDto.serializer(), it) }

    suspend fun updateProperty(id: String, body: JsonObject): PropertyDto =
        call.json({ service.updateProperty(id, body.asBody()) }) { AdcJson.decodeFromString(PropertyDto.serializer(), it) }

    suspend fun deleteProperty(id: String) = call.unit { service.deleteProperty(id) }

    // ---- Importing properties from a spreadsheet

    /** The sample sheet (`.xlsx`) to fill in. */
    suspend fun propertyImportTemplate(org: String): ByteArray = call.bytes { service.propertyImportTemplate(org) }

    /** The property details a column can be imported into. */
    suspend fun propertyImportColumns(org: String): List<ImportTargetDto> =
        call.json({ service.propertyImportColumns(org) }) { AdcJson.decodeFromString(ImportTargetsDto.serializer(), it).data }

    /** Which detail each of the sheet's columns most likely holds (nothing is saved). */
    suspend fun suggestPropertyImport(org: String, fileName: String, file: ByteArray): ImportSuggestionDto =
        call.json({ service.suggestPropertyImport(org, sheetPart(fileName, file)) }) {
            AdcJson.decodeFromString(ImportSuggestionDto.serializer(), it)
        }

    /** Imports the sheet's rows as properties, reading each column as [mapping] says (heading → detail key). */
    suspend fun importProperties(org: String, fileName: String, file: ByteArray, mapping: Map<String, String>): ImportResultDto {
        val parts = buildList {
            add(sheetPart(fileName, file))
            if (mapping.isNotEmpty()) {
                add(MultipartBody.Part.createFormData("mapping", AdcJson.encodeToString(MapSerializer(String.serializer(), String.serializer()), mapping)))
            }
        }
        return call.json({ service.importProperties(org, parts) }) { AdcJson.decodeFromString(ImportResultDto.serializer(), it) }
    }

    private fun sheetPart(fileName: String, file: ByteArray): MultipartBody.Part =
        MultipartBody.Part.createFormData("file", fileName, file.toRequestBody(XLSX_TYPE.toMediaType()))

    suspend fun settings(id: String): SettingsDto =
        call.json({ service.settings(id) }) { AdcJson.decodeFromString(SettingsDto.serializer(), it) }

    suspend fun updateSettings(id: String, body: JsonObject): SettingsDto =
        call.json({ service.updateSettings(id, body.asBody()) }) { AdcJson.decodeFromString(SettingsDto.serializer(), it) }

    suspend fun geofence(id: String): GeofenceDto =
        call.json({ service.geofence(id) }) { AdcJson.decodeFromString(GeofenceDto.serializer(), it) }

    suspend fun putGeofence(id: String, body: JsonObject): GeofenceDto =
        call.json({ service.putGeofence(id, body.asBody()) }) { AdcJson.decodeFromString(GeofenceDto.serializer(), it) }

    suspend fun members(id: String): List<MemberDto> =
        call.json({ service.members(id) }) { AdcJson.decodeFromString(PageDto.serializer(MemberDto.serializer()), it).data }

    /** The people the property's assets are assigned to (the asset list's people filter), by name. */
    suspend fun assetAssignees(id: String): List<AssigneeDto> =
        call.json({ service.assetAssignees(id) }) { AdcJson.decodeFromString(PageDto.serializer(AssigneeDto.serializer()), it).data }

    suspend fun addMember(id: String, body: JsonObject): MemberDto =
        call.json({ service.addMember(id, body.asBody()) }) { AdcJson.decodeFromString(MemberDto.serializer(), it) }

    suspend fun removeMember(id: String, userId: String) = call.unit { service.removeMember(id, userId) }

    // ---- Countries, states and towns

    suspend fun countries(): List<PlaceNameDto> =
        call.json({ service.countries() }) { AdcJson.decodeFromString(ListSerializer(PlaceNameDto.serializer()), it) }

    suspend fun states(country: String): List<PlaceNameDto> =
        call.json({ service.states(country) }) { AdcJson.decodeFromString(ListSerializer(PlaceNameDto.serializer()), it) }

    suspend fun cities(country: String, state: String?, q: String?, limit: Int): List<CityDto> =
        call.json({ service.cities(country, state, q, limit) }) { AdcJson.decodeFromString(ListSerializer(CityDto.serializer()), it) }

    // ---- Assigning assets to a person

    suspend fun assignAssets(property: String, body: JsonObject): AssignResultDto =
        call.json({ service.assignAssets(property, body.asBody()) }) { AdcJson.decodeFromString(AssignResultDto.serializer(), it) }

    // ---- Access

    suspend fun permissions(org: String): List<PermissionDto> =
        call.json({ service.permissions(org) }) { AdcJson.decodeFromString(ListSerializer(PermissionDto.serializer()), it) }

    suspend fun groups(org: String): List<GroupDto> =
        call.json({ service.groups(org) }) { AdcJson.decodeFromString(PageDto.serializer(GroupDto.serializer()), it).data }

    suspend fun createGroup(org: String, body: JsonObject): GroupDto =
        call.json({ service.createGroup(org, body.asBody()) }) { AdcJson.decodeFromString(GroupDto.serializer(), it) }

    suspend fun updateGroup(org: String, id: String, body: JsonObject): GroupDto =
        call.json({ service.updateGroup(org, id, body.asBody()) }) { AdcJson.decodeFromString(GroupDto.serializer(), it) }

    suspend fun deleteGroup(org: String, id: String) = call.unit { service.deleteGroup(org, id) }

    suspend fun users(org: String): List<UserDto> =
        allPages(UserDto.serializer()) { cursor -> service.users(org, AdcConfig.PAGE_SIZE, cursor) }

    suspend fun inviteUser(org: String, body: JsonObject): UserDto =
        call.json({ service.inviteUser(org, body.asBody()) }) { AdcJson.decodeFromString(UserDto.serializer(), it) }

    suspend fun updateUser(org: String, id: String, body: JsonObject): UserDto =
        call.json({ service.updateUser(org, id, body.asBody()) }) { AdcJson.decodeFromString(UserDto.serializer(), it) }

    /** A [page] (from 1) of [limit] people the organisation's directory lists; everyone when [query] is blank. */
    suspend fun directory(org: String, query: String?, limit: Int, page: Int): DirectoryDto =
        call.json({ service.directory(org, query?.takeIf { it.isNotBlank() }, limit, page) }) {
            AdcJson.decodeFromString(DirectoryDto.serializer(), it)
        }

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
        const val XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val MAX_PAGES = 50
    }
}
