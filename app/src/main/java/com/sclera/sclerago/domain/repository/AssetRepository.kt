package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetCatalog
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.Status
import kotlinx.coroutines.flow.StateFlow

/**
 * Assets and the edits made to them. Every change that edits an asset (not deleting it) also
 * records when, in [AssetCatalog.touched].
 */
interface AssetRepository {
    val catalog: StateFlow<AssetCatalog>

    /** Reserves [count] new asset ids and returns the first; the rest follow it. */
    fun newIds(count: Int): Int

    /** Adds [assets] in front of the others (newest first). */
    fun add(assets: List<Asset>)

    fun setStatus(ids: Collection<Int>, status: Status)

    /** Keeps the values from an asset's details form. */
    fun saveDetails(id: Int, values: Map<String, String>)

    fun delete(ids: Collection<Int>)

    fun assignLocation(ids: Collection<Int>, location: LocValue)

    /** Adds photos / videos / documents to an asset. */
    fun attachMedia(id: Int, media: List<Media>)

    /** Remembers that the scanned device [deviceId] was made into the asset [assetId]. */
    fun linkDevice(deviceId: String, assetId: Int)

    /** Code and reference fields an existing asset came with; null for assets added here. */
    fun details(id: Int): AssetDetails?
}
