package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.mapper.toDetail
import com.sclera.sclerago.data.mapper.toDomain
import com.sclera.sclerago.data.remote.adc.AdcIntegrationApi
import com.sclera.sclerago.domain.model.PlacesDirection
import com.sclera.sclerago.domain.model.PlacesHistoryPage
import com.sclera.sclerago.domain.model.PlacesTransferDetail
import com.sclera.sclerago.domain.repository.PlacesHistoryRepository
import javax.inject.Inject

/** The places import history at the server; online only, as on the web. */
class PlacesHistoryRepositoryImpl @Inject constructor(private val api: AdcIntegrationApi) : PlacesHistoryRepository {
    override suspend fun page(propertyId: String, direction: PlacesDirection?, limit: Int, offset: Int): PlacesHistoryPage =
        api.placesHistory(
            propertyId,
            direction = when (direction) {
                PlacesDirection.TO_VDMS -> "to_vdms"
                PlacesDirection.FROM_VDMS -> "from_vdms"
                null -> null
            },
            // The server takes 1 to 200.
            limit = limit.coerceIn(1, MAX_LIMIT),
            offset = offset.coerceAtLeast(0),
        ).toDomain()

    override suspend fun transfer(propertyId: String, transferId: String): PlacesTransferDetail =
        api.placesTransfer(propertyId, transferId).toDetail()

    private companion object {
        const val MAX_LIMIT = 200
    }
}
