package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.AssetDocument
import kotlinx.coroutines.flow.Flow

/**
 * An asset's documents (its Documents tab): the file it was read from, the web pages its values came from and its
 * model's manuals. Writes throw [com.sclera.sclerago.domain.model.DataException].
 */
interface AssetDocumentsRepository {
    /** The asset's documents in the server's order (source file, pages, manuals), again whenever they change. */
    fun observe(assetId: String): Flow<List<AssetDocument>>

    /** Takes the web page [documentId] off its asset. */
    suspend fun removeWebPage(documentId: String)

    /** Deletes the uploaded file [documentId] (the file the asset was read from, or one uploaded on its Documents tab). */
    suspend fun removeSourceFile(documentId: String)
}
