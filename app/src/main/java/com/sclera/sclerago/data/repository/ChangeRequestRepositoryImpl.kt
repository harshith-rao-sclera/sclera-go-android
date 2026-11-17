package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.offline.AssetsWriter
import com.sclera.sclerago.domain.repository.ChangeRequestRepository
import javax.inject.Inject

/** Change requests written to the property's offline database; the sync takes them to the server. */
class ChangeRequestRepositoryImpl @Inject constructor(private val writer: AssetsWriter) : ChangeRequestRepository {
    override suspend fun request(assetId: String, note: String?) = writer.requestChange(assetId, note)

    override suspend fun close(assetId: String, declined: Boolean) = writer.closeChangeRequest(assetId, declined)
}
