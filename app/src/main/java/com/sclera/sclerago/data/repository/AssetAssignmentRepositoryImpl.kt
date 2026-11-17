package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.mapper.assignBody
import com.sclera.sclerago.data.mapper.toAssignResult
import com.sclera.sclerago.data.remote.adc.AdcApi
import com.sclera.sclerago.domain.model.AssignResult
import com.sclera.sclerago.domain.repository.AssetAssignmentRepository
import javax.inject.Inject

/** Assigns assets through `POST /properties/{p}/assets/bulk/assign`, as the web's new API does. */
class AssetAssignmentRepositoryImpl @Inject constructor(private val api: AdcApi) : AssetAssignmentRepository {
    override suspend fun assign(propertyId: String, assetIds: Collection<String>, email: String?): AssignResult =
        api.assignAssets(propertyId, assignBody(assetIds, email)).toAssignResult()
}
