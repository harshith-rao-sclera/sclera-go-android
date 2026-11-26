package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.AssetDocument
import com.sclera.sclerago.domain.model.OpenableFile

/**
 * An asset's documents (its Documents tab), the server's only: documents are not synced, so every one is listed, opened,
 * added and taken off through the API each time. Every call throws [com.sclera.sclerago.domain.model.OnlineException]
 * (Offline without a connection).
 */
interface AssetDocumentsRepository {
    /**
     * The asset's documents: the ones added to it (its media of kind document), the file it was read from, the web pages
     * its values came from and its model's manuals.
     */
    suspend fun list(assetId: String): List<AssetDocument>

    /** [document]'s file, fetched from the server to be opened. */
    suspend fun download(assetId: String, document: AssetDocument): OpenableFile

    /** Takes the document [documentId] added to the asset off it. */
    suspend fun removeAttachment(assetId: String, documentId: String)

    /** Takes the web page [documentId] off the asset. */
    suspend fun removeWebPage(assetId: String, documentId: String)
}
