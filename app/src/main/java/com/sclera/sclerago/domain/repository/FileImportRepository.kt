package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.AssetDraft
import com.sclera.sclerago.domain.model.DraftField
import com.sclera.sclerago.domain.model.DraftsAccepted
import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.UploadedDocument

/**
 * Collecting assets from files at the server (`/properties/{property}/documents` and `/asset-drafts`): upload a file,
 * read the assets found in it, then add or throw them away. Online only; every call can throw
 * [com.sclera.sclerago.domain.model.OnlineException].
 */
interface FileImportRepository {
    /**
     * Sends the picked [file] to [property]; [onProgress] gets 0..100 as it goes. The server then reads it in the
     * background; the assets found in it come from [source] (a file, or the Camera's Many assets).
     */
    suspend fun upload(property: String, file: ImportedFile, onProgress: (Int) -> Unit, source: Source = Source.FILE): UploadedDocument

    /** The uploaded file [documentId] as it stands now. */
    suspend fun document(property: String, documentId: String): UploadedDocument

    /** The assets read from [documentId] that are still waiting for review, in the file's order. */
    suspend fun drafts(property: String, documentId: String): List<AssetDraft>

    /** The property's fields a file's values go in (active standard fields, in order). */
    suspend fun fields(): List<DraftField>

    /**
     * Corrects draft [draftId] before it is added: [values] by field id, null to clear one. The server takes a value
     * typed as certain. Returns the draft as it now is.
     */
    suspend fun updateDraft(property: String, draftId: String, values: Map<String, String?>): AssetDraft

    /** Adds the drafts [ids] as assets; with [createFields], what no field holds becomes fields of each asset. */
    suspend fun accept(property: String, ids: List<String>, createFields: Boolean): DraftsAccepted

    /** Throws the drafts [ids] away; returns how many went. */
    suspend fun discard(property: String, ids: List<String>): Int
}
