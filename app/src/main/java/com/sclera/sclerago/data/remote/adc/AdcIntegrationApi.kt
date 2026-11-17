package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class IntegrationDto(
    val id: String,
    val propertyId: String = "",
    val vdmsId: String = "",
    val organisationId: String = "",
    val createdAt: String? = null,
)

@Serializable
data class VdmsChoiceDto(val vdmsId: String = "", val propertyName: String? = null)

@Serializable
data class AssigneeDto(val email: String = "", val name: String? = null)

@Serializable
data class MoveResultDto(val moved: Int? = null)

@Serializable
data class ManagedCountsDto(val moved: Int = 0, val returned: Int = 0)

/** The Integration calls as typed results. Every call can throw [com.sclera.sclerago.domain.model.OnlineException]; see [OnlineCall]. */
@Singleton
class AdcIntegrationApi @Inject constructor(private val service: AdcIntegrationService, private val call: OnlineCall) {
    suspend fun integration(p: String): List<IntegrationDto> =
        call.json({ service.integration(p) }) { AdcJson.decodeFromString(ListSerializer(IntegrationDto.serializer()), it) }

    suspend fun available(p: String, key: String?): List<VdmsChoiceDto> =
        call.json({ service.available(p, key) }) { AdcJson.decodeFromString(ListSerializer(VdmsChoiceDto.serializer()), it) }

    suspend fun link(p: String, vdmsId: String): IntegrationDto =
        call.json({ service.link(p, vdmsId) }) { AdcJson.decodeFromString(IntegrationDto.serializer(), it) }

    suspend fun networks(p: String): List<String> =
        call.json({ service.networks(p) }) { AdcJson.decodeFromString(ListSerializer(String.serializer()), it) }

    suspend fun assignees(p: String, key: String?): List<AssigneeDto> =
        call.json({ service.assignees(p, key) }) { AdcJson.decodeFromString(ListSerializer(AssigneeDto.serializer()), it) }

    suspend fun counts(p: String, buildingIds: String?, floorIds: String?, locationIds: String?): ManagedCountsDto =
        call.json({ service.counts(p, buildingIds, floorIds, locationIds) }) { AdcJson.decodeFromString(ManagedCountsDto.serializer(), it) }

    /** [body] is built by `moveBody` (`data/mapper/IntegrationMapper.kt`). */
    suspend fun move(p: String, body: JsonObject): MoveResultDto =
        call.json({ service.move(p, body.toString().toRequestBody(JSON)) }) { AdcJson.decodeFromString(MoveResultDto.serializer(), it) }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
