package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.offline.AssetsOfflineSource
import com.sclera.sclerago.data.offline.AssetsWriter
import com.sclera.sclerago.data.sync.SyncCredentialsProvider
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.repository.AssetActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * An asset's comments and history, read from and written to the property's offline database (`asset_activity`);
 * the sync pushes new comments to the server, as it does the history lines the app writes. The phone's data has no
 * names of other people, so an entry only says when the signed-in user wrote it ([AssetActivity.byMe]).
 */
class AssetActivityRepositoryImpl @Inject constructor(
    private val source: AssetsOfflineSource,
    private val writer: AssetsWriter,
    private val user: SyncCredentialsProvider,
) : AssetActivityRepository {
    override fun observe(assetId: String): Flow<List<AssetActivity>> =
        combine(source.activityFlow(assetId), user.credentials) { entries, me ->
            val id = me?.userId?.takeIf { it.isNotBlank() }
            entries.map { it.copy(byMe = id != null && it.createdBy == id) }
        }

    override suspend fun addComment(assetId: String, text: String) = writer.addComment(assetId, text)
}
