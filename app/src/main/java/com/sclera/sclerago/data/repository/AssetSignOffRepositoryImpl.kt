package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.offline.AssetsWriter
import com.sclera.sclerago.domain.repository.AssetSignOffRepository
import javax.inject.Inject

/** An admin's sign-off written to the property's offline database; the sync takes it to the server. */
class AssetSignOffRepositoryImpl @Inject constructor(private val writer: AssetsWriter) : AssetSignOffRepository {
    override suspend fun markValidated(ids: Collection<String>) = writer.markValidated(ids)
}
