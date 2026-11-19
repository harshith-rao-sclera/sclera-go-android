package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.offline.AssetsOfflineSource
import com.sclera.sclerago.data.offline.AssetsWriter
import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.domain.model.AssetCatalog
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.NewAsset
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.SubmitOutcome
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.repository.VdmsLinkRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The assets of the downloaded property, read from the offline database and written back to it (the sync pushes the
 * changes). The one downloaded database is the property being worked on, so assets are not filtered by property.
 */
@Singleton
class AssetRepositoryImpl @Inject constructor(
    private val source: AssetsOfflineSource,
    private val writer: AssetsWriter,
    private val vdmsLinks: VdmsLinkRepository,
    private val downloads: PropertyDownloadRepository,
    @ApplicationScope scope: CoroutineScope,
) : AssetRepository {
    override val catalog: StateFlow<AssetCatalog> =
        source.listReadFlow().map { read -> AssetCatalog(read.assets, loaded = read.open) }.stateIn(scope, SharingStarted.Eagerly, AssetCatalog(loaded = false))

    override suspend fun add(assets: List<NewAsset>) = writer.add(assets)

    override suspend fun setStatus(ids: Collection<String>, status: Status, photoRules: List<PhotoRule>): SubmitOutcome =
        writer.setStatus(ids, status, photoRules)

    override suspend fun saveDetails(id: String, values: Map<String, String>, sources: Map<String, String>, place: LocValue?) {
        writer.saveDetails(id, values, sources, place)
    }

    // In a property linked to a VDMS the delete waits for VDMS to confirm it, as the server's DELETE does.
    override suspend fun delete(ids: Collection<String>) {
        val linked = downloads.downloads.value.currentId?.let { vdmsLinks.isLinked(it) } ?: false
        writer.delete(ids, vdmsLinked = linked)
    }

    override suspend fun assignLocation(ids: Collection<String>, location: LocValue) = writer.assignLocation(ids, location)

    override suspend fun attachMedia(id: String, media: List<Media>) = writer.attachMedia(id, media)

    override suspend fun removeMedia(assetId: String, mediaId: String) = writer.removeMedia(assetId, mediaId)

    override suspend fun details(id: String): AssetDetails? = source.asset(id)?.details

    override suspend fun detailsFor(ids: Collection<String>): Map<String, AssetDetails> = source.detailsFor(ids)
}
