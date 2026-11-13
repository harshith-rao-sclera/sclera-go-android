package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
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

    suspend fun settings(id: String): SettingsDto =
        call.json({ service.settings(id) }) { AdcJson.decodeFromString(SettingsDto.serializer(), it) }

    suspend fun updateSettings(id: String, body: JsonObject): SettingsDto =
        call.json({ service.updateSettings(id, body.asBody()) }) { AdcJson.decodeFromString(SettingsDto.serializer(), it) }

    suspend fun members(id: String): List<MemberDto> =
        call.json({ service.members(id) }) { AdcJson.decodeFromString(PageDto.serializer(MemberDto.serializer()), it).data }

    suspend fun addMember(id: String, body: JsonObject): MemberDto =
        call.json({ service.addMember(id, body.asBody()) }) { AdcJson.decodeFromString(MemberDto.serializer(), it) }

    suspend fun removeMember(id: String, userId: String) = call.unit { service.removeMember(id, userId) }

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

    suspend fun directory(org: String, query: String?): DirectoryDto =
        call.json({ service.directory(org, query?.takeIf { it.isNotBlank() }, DIRECTORY_LIMIT) }) {
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
        const val MAX_PAGES = 50
        const val DIRECTORY_LIMIT = 10
    }
}
