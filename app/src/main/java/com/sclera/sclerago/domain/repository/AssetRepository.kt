package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.AssetCatalog
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.NewAsset
import com.sclera.sclerago.domain.model.Status
import kotlinx.coroutines.flow.StateFlow

/**
 * The assets of the downloaded property and the edits made to them. Writes throw
 * [com.sclera.sclerago.domain.model.DataException] when they cannot be made.
 */
interface AssetRepository {
    val catalog: StateFlow<AssetCatalog>

    /** Adds [assets], each with its media, values and the scanned device it was made from, in one change. */
    suspend fun add(assets: List<NewAsset>)

    suspend fun setStatus(ids: Collection<String>, status: Status)

    /** Keeps the values from an asset's details form. */
    suspend fun saveDetails(id: String, values: Map<String, String>)

    suspend fun delete(ids: Collection<String>)

    suspend fun assignLocation(ids: Collection<String>, location: LocValue)

    /** Adds photos / videos / documents to an asset. */
    suspend fun attachMedia(id: String, media: List<Media>)

    /** The code, fields, confidence and sources of the asset [id]; null when it is not there. */
    suspend fun details(id: String): AssetDetails?

    /** [details] of several assets at once, by asset id. */
    suspend fun detailsFor(ids: Collection<String>): Map<String, AssetDetails>
}
