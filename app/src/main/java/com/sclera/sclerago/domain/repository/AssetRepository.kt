package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.AssetCatalog
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.NewAsset
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.SubmitOutcome
import kotlinx.coroutines.flow.StateFlow

/**
 * The assets of the downloaded property and the edits made to them. Writes throw
 * [com.sclera.sclerago.domain.model.DataException] when they cannot be made.
 */
interface AssetRepository {
    val catalog: StateFlow<AssetCatalog>

    /** Adds [assets], each with its media, values and the scanned device it was made from, in one change. */
    suspend fun add(assets: List<NewAsset>)

    /**
     * Moves assets to [status]. Submitting follows the server's rule: an asset with a mandatory field empty, or fewer
     * photos of a kind than [photoRules] ask for, goes in as an exception; one already submitted or validated is left
     * alone. Back in draft approves a change request (the asset goes to whoever asked). An asset assigned to nobody
     * becomes the mover's. Returns what submitting did (all zero when not submitting).
     */
    suspend fun setStatus(
        ids: Collection<String>,
        status: Status,
        photoRules: List<PhotoRule> = emptyList(),
    ): SubmitOutcome

    /**
     * Keeps the values from an asset's details form. [sources] (form field key -> web page) are where applied AI
     * suggestions came from: kept with those values, and the pages kept with the asset, as the server's edit does.
     */
    suspend fun saveDetails(id: String, values: Map<String, String>, sources: Map<String, String> = emptyMap(), place: LocValue? = null)

    suspend fun delete(ids: Collection<String>)

    suspend fun assignLocation(ids: Collection<String>, location: LocValue)

    /** Adds photos / videos / documents to an asset. */
    suspend fun attachMedia(id: String, media: List<Media>)

    /** Removes the photo, video, voice memo or file [mediaId] from asset [assetId] (and from the field it answers). */
    suspend fun removeMedia(assetId: String, mediaId: String)

    /** The code, fields, confidence and sources of the asset [id]; null when it is not there. */
    suspend fun details(id: String): AssetDetails?

    /** [details] of several assets at once, by asset id. */
    suspend fun detailsFor(ids: Collection<String>): Map<String, AssetDetails>
}
