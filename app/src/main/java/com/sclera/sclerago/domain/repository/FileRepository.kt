package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.Media

/** Photos, videos, documents and spreadsheets on the device, by content URI. */
interface FileRepository {
    /** Photos / videos picked from the gallery, described for attaching to an asset. */
    suspend fun describeMedia(uris: List<String>): List<Media>

    /** Documents picked in the system file manager, described for import. */
    suspend fun describeFiles(uris: List<String>): List<ImportedFile>

    /** Deletes the files of [media] that this app took itself (others aren't ours to delete). */
    fun deleteOwned(media: List<Media>)

    /** Writes [bytes] to the document [uri] the user picked to save to; false when it can't be written. */
    suspend fun writeFile(uri: String, bytes: ByteArray): Boolean

    /** Copies the file at [from] to the document [to] the user picked to save to; false when it can't be read or written. */
    suspend fun copyFile(from: String, to: String): Boolean
}
