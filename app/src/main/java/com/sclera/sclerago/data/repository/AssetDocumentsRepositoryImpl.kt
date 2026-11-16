package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.offline.AssetsWriter
import com.sclera.sclerago.data.offline.DocumentsOfflineSource
import com.sclera.sclerago.domain.model.AssetDocument
import com.sclera.sclerago.domain.repository.AssetDocumentsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** An asset's documents read from the property's offline database; removing a page is synced to the server. */
class AssetDocumentsRepositoryImpl @Inject constructor(
    private val source: DocumentsOfflineSource,
    private val writer: AssetsWriter,
) : AssetDocumentsRepository {
    override fun observe(assetId: String): Flow<List<AssetDocument>> = source.documentsFlow(assetId)

    override suspend fun removeWebPage(documentId: String) = writer.removeWebDocument(documentId)
}
