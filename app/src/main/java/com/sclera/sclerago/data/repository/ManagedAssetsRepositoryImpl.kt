package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.mapper.moveBody
import com.sclera.sclerago.data.remote.adc.AdcIntegrationApi
import com.sclera.sclerago.domain.model.ManagedCounts
import com.sclera.sclerago.domain.model.PlaceScope
import com.sclera.sclerago.domain.model.VdmsAssignee
import com.sclera.sclerago.domain.repository.ManagedAssetsRepository
import javax.inject.Inject

/** Move to Managed Assets at the server; online only, as on the web. */
class ManagedAssetsRepositoryImpl @Inject constructor(private val api: AdcIntegrationApi) : ManagedAssetsRepository {
    override suspend fun networks(propertyId: String): List<String> =
        api.networks(propertyId).map { it.trim().take(MAX_TEXT) }.filter { it.isNotEmpty() }.distinct()

    override suspend fun assignees(propertyId: String, key: String?): List<VdmsAssignee> =
        api.assignees(propertyId, key?.trim()?.take(MAX_KEY)?.takeIf { it.isNotEmpty() })
            .filter { it.email.isNotBlank() }
            .map { VdmsAssignee(it.email.trim().take(MAX_TEXT), it.name?.takeIf { n -> n.isNotBlank() && n != "null" }?.take(MAX_TEXT)) }

    override suspend fun counts(propertyId: String, scope: PlaceScope): ManagedCounts {
        // As the web sends them: comma-separated ids, or nothing for every place.
        fun ids(list: List<String>) = list.filter { it.isNotBlank() }.joinToString(",").ifEmpty { null }
        val dto = api.counts(propertyId, ids(scope.buildingIds), ids(scope.floorIds), ids(scope.locationIds))
        return ManagedCounts(dto.moved.coerceAtLeast(0), dto.returned.coerceAtLeast(0))
    }

    override suspend fun move(propertyId: String, assetIds: Collection<String>, networkName: String, assignee: String?): Int? =
        api.move(propertyId, moveBody(assetIds, networkName, assignee)).moved

    private companion object {
        const val MAX_KEY = 200
        const val MAX_TEXT = 255
    }
}
