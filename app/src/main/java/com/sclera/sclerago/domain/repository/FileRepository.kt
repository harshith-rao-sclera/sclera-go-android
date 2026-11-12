package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.Media
import java.io.OutputStream

/** Photos, videos, documents and spreadsheets on the device, by content URI. */
interface FileRepository {
    /** Photos / videos picked from the gallery, described for attaching to an asset. */
    suspend fun describeMedia(uris: List<String>): List<Media>

    /** Documents picked in the system file manager, described for import. */
    suspend fun describeFiles(uris: List<String>): List<ImportedFile>

    /** Deletes the files of [media] that this app took itself (others aren't ours to delete). */
    fun deleteOwned(media: List<Media>)

    /** The rows of a CSV or Excel file; null when it can't be read. */
    suspend fun readSheet(uri: String): List<List<String>>?

    /** Writes the sample spreadsheet for importing properties. */
    fun writePropertyImportTemplate(out: OutputStream)
}
