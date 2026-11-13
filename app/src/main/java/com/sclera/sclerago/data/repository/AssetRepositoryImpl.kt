package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.offline.AssetsOfflineSource
import com.sclera.sclerago.data.offline.AssetsWriter
import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.domain.model.AssetCatalog
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.NewAsset
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.repository.AssetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The assets of the downloaded property, read from the offline database and written back to it (the sync pushes the
 * changes). The one downloaded database is the property being worked on, so assets are not filtered by property.
 */
@Singleton
class AssetRepositoryImpl @Inject constructor(
    private val source: AssetsOfflineSource,
    private val writer: AssetsWriter,
    @ApplicationScope scope: CoroutineScope,
) : AssetRepository {
    override val catalog: StateFlow<AssetCatalog> =
        combine(source.listFlow(), source.deviceLinksFlow(), source.scanTextFlow()) { assets, devices, scanText ->
            AssetCatalog(assets, devices, scanText)
        }.stateIn(scope, SharingStarted.Eagerly, AssetCatalog())

    override suspend fun add(assets: List<NewAsset>) = writer.add(assets)

    override suspend fun setStatus(ids: Collection<String>, status: Status) = writer.setStatus(ids, status)

    override suspend fun saveDetails(id: String, values: Map<String, String>) = writer.saveDetails(id, values)

    override suspend fun delete(ids: Collection<String>) = writer.delete(ids)

    override suspend fun assignLocation(ids: Collection<String>, location: LocValue) = writer.assignLocation(ids, location)

    override suspend fun attachMedia(id: String, media: List<Media>) = writer.attachMedia(id, media)

    override suspend fun details(id: String): AssetDetails? = source.asset(id)?.details

    override suspend fun detailsFor(ids: Collection<String>): Map<String, AssetDetails> = source.detailsFor(ids)
}
