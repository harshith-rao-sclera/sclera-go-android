package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.mapper.parseDbTime
import com.sclera.sclerago.data.mapper.placesSelectionBody
import com.sclera.sclerago.data.mapper.toDomain
import com.sclera.sclerago.data.remote.adc.AdcIntegrationApi
import com.sclera.sclerago.domain.model.PlaceKey
import com.sclera.sclerago.domain.model.PlacesImportResult
import com.sclera.sclerago.domain.model.PlacesPush
import com.sclera.sclerago.domain.model.VdmsPlaces
import com.sclera.sclerago.domain.repository.VdmsPlacesRepository
import javax.inject.Inject

/** The places import with the linked VDMS at the server; online only, as on the web. */
class VdmsPlacesRepositoryImpl @Inject constructor(private val api: AdcIntegrationApi) : VdmsPlacesRepository {
    override suspend fun places(propertyId: String): VdmsPlaces = api.places(propertyId).toDomain()

    override suspend fun refresh(propertyId: String): Long? = parseDbTime(api.refreshPlaces(propertyId).snapshotRequestedAt)

    override suspend fun push(propertyId: String, chosen: Set<PlaceKey>): PlacesPush =
        api.pushPlaces(propertyId, placesSelectionBody(chosen)).toDomain()

    override suspend fun pull(propertyId: String, chosen: Set<PlaceKey>): PlacesImportResult =
        api.importPlaces(propertyId, placesSelectionBody(chosen)).toDomain()
}
