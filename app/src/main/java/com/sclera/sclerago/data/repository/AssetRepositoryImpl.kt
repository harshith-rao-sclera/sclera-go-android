package com.sclera.sclerago.data.repository

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetCatalog
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Assets in memory, until they come from the Sclera API. The app's (Hilt's) instance starts empty;
 * previews and tests can start it with [initial] assets and their [initialDetails].
 */
@ActivityRetainedScoped
class AssetRepositoryImpl(
    private val initial: List<Asset>,
    private val initialDetails: Map<Int, AssetDetails>,
) : AssetRepository, UserDataCleaner {
    @Inject constructor() : this(emptyList(), emptyMap())

    private val state = MutableStateFlow(AssetCatalog(assets = initial))
    override val catalog: StateFlow<AssetCatalog> = state.asStateFlow()

    private fun set(f: AssetCatalog.() -> AssetCatalog) {
        state.value = state.value.f()
    }

    private fun AssetCatalog.touch(ids: Collection<Int>): AssetCatalog {
        val now = System.currentTimeMillis()
        return copy(touched = touched + ids.associateWith { now })
    }

    override fun newIds(count: Int): Int {
        val first = state.value.nextId
        set { copy(nextId = nextId + count) }
        return first
    }

    override fun add(assets: List<Asset>) = set { copy(assets = assets + this.assets).touch(assets.map { it.id }) }

    override fun setStatus(ids: Collection<Int>, status: Status) =
        set { copy(statusOverride = statusOverride + ids.associateWith { status }).touch(ids) }

    override fun saveDetails(id: Int, values: Map<String, String>) =
        set { copy(savedDetails = savedDetails + (id to values)).touch(listOf(id)) }

    override fun delete(ids: Collection<Int>) = set { copy(deleted = deleted + ids) }

    override fun assignLocation(ids: Collection<Int>, location: LocValue) =
        set { copy(locationOverride = locationOverride + ids.associateWith { location }).touch(ids) }

    override fun attachMedia(id: Int, media: List<Media>) =
        set { copy(mediaAdded = mediaAdded + (id to (mediaAdded[id].orEmpty() + media))).touch(listOf(id)) }

    override fun linkDevice(deviceId: String, assetId: Int) =
        set { copy(deviceAsset = deviceAsset + (deviceId to assetId), addedDevices = addedDevices + deviceId) }

    override fun clear() {
        state.value = AssetCatalog()
    }

    /** Code and reference fields an asset came with (from the server, once assets come from the API). */
    override fun details(id: Int): AssetDetails? = initialDetails[id]
}
